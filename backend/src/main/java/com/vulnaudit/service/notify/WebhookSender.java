package com.vulnaudit.service.notify;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vulnaudit.config.NotificationProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Webhook 通道发送器
 *
 * <p>功能说明：
 * <ul>
 *     <li>HTTP POST JSON 报文到 {@code app.notify.webhook.url}；</li>
 *     <li>请求头携带 HMAC-SHA256 签名（{@code X-Notify-Signature: sha256=<hex>}，
 *         签名内容为 {@code timestamp + "." + body}）以及时间戳与事件类型；</li>
 *     <li>单次请求带超时，失败后最多重投 {@code app.notify.max-retries} 次（含首次，默认 3 次）；</li>
 *     <li>未启用或 url 为空时 {@link #isEnabled()} 返回 false，上层降级为仅站内通知。</li>
 * </ul>
 */
@Slf4j
@Component
public class WebhookSender implements NotificationChannelSender {

    static final String HEADER_SIGNATURE = "X-Notify-Signature";
    static final String HEADER_TIMESTAMP = "X-Notify-Timestamp";
    static final String HEADER_EVENT = "X-Notify-Event";
    static final String SIGNATURE_PREFIX = "sha256=";

    private final NotificationProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public WebhookSender(NotificationProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1L, properties.getWebhook().getTimeoutMs())))
                .build());
    }

    /**
     * 供单元测试注入自定义 HTTP 客户端
     */
    WebhookSender(NotificationProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    @PostConstruct
    public void reportConfiguration() {
        NotificationProperties.Webhook webhook = properties.getWebhook();
        if (webhook == null || !webhook.isEnabled()) {
            log.info("Webhook 通道未启用（app.notify.webhook.enabled=false），通知降级为仅站内");
            return;
        }
        if (isBlank(webhook.getUrl())) {
            log.warn("Webhook 通道已开启但 app.notify.webhook.url 未配置，降级为仅站内通知");
            return;
        }
        if (isBlank(webhook.getSecret())) {
            log.warn("Webhook 通道已开启但 app.notify.webhook.secret 未配置，请求将不携带签名头");
        }
        log.info("Webhook 通道已就绪: url={}, maxRetries={}, timeoutMs={}",
                webhook.getUrl(), webhook.getMaxRetries(), webhook.getTimeoutMs());
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.WEBHOOK;
    }

    @Override
    public boolean isEnabled() {
        NotificationProperties.Webhook webhook = properties.getWebhook();
        return webhook != null && webhook.isEnabled() && !isBlank(webhook.getUrl());
    }

    @Override
    public ChannelSendResult send(NotificationMessage message) {
        if (message == null) {
            return ChannelSendResult.failure(NotificationChannel.WEBHOOK, "通知报文为空");
        }
        if (!isEnabled()) {
            return ChannelSendResult.skipped(NotificationChannel.WEBHOOK, "Webhook 通道未启用或 url 未配置");
        }

        NotificationProperties.Webhook webhook = properties.getWebhook();
        int maxAttempts = Math.max(1, webhook.getMaxRetries());
        long backoff = Math.max(0L, webhook.getRetryBackoffMs());
        String payload;
        try {
            payload = buildPayload(message);
        } catch (Exception e) {
            return ChannelSendResult.failure(NotificationChannel.WEBHOOK, "报文序列化失败: " + e.getMessage());
        }

        String timestamp = String.valueOf(System.currentTimeMillis());
        String signature = sign(webhook.getSecret(), timestamp, payload);
        String lastError = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(webhook.getUrl()))
                        .timeout(Duration.ofMillis(Math.max(1L, webhook.getTimeoutMs())))
                        .header("Content-Type", "application/json;charset=UTF-8")
                        .header(HEADER_TIMESTAMP, timestamp)
                        .header(HEADER_EVENT, safeType(message.type()))
                        .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8));
                if (signature != null) {
                    builder.header(HEADER_SIGNATURE, signature);
                }
                HttpRequest request = builder.build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    log.info("Webhook 推送成功: notificationId={}, status={}, attempts={}",
                            message.notificationId(), status, attempt);
                    return ChannelSendResult.success(NotificationChannel.WEBHOOK, attempt);
                }
                lastError = "HTTP " + status;
                log.warn("Webhook 推送返回非 2xx: notificationId={}, status={}, attempt={}/{}",
                        message.notificationId(), status, attempt, maxAttempts);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                lastError = "Interrupted";
                break;
            } catch (Exception e) {
                lastError = e.getClass().getSimpleName() + ": " + e.getMessage();
                log.warn("Webhook 推送异常: notificationId={}, attempt={}/{}, error={}",
                        message.notificationId(), attempt, maxAttempts, lastError);
            }

            if (attempt < maxAttempts && backoff > 0) {
                try {
                    Thread.sleep(backoff * attempt);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        log.warn("Webhook 推送最终失败，降级为仅站内: notificationId={}, attempts={}, error={}",
                message.notificationId(), maxAttempts, lastError);
        return ChannelSendResult.failure(NotificationChannel.WEBHOOK, maxAttempts, lastError);
    }

    /**
     * 构建 JSON 报文
     */
    private String buildPayload(NotificationMessage message) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("event", safeType(message.type()));
        payload.put("notificationId", message.notificationId() == null ? null : message.notificationId().toString());
        payload.put("userId", message.userId() == null ? null : message.userId().toString());
        payload.put("username", message.username());
        payload.put("title", message.title());
        payload.put("content", message.content());
        payload.put("priority", message.priority());
        payload.put("relatedType", message.relatedType());
        payload.put("relatedId", message.relatedId() == null ? null : message.relatedId().toString());
        payload.put("timestamp", System.currentTimeMillis());
        return objectMapper.writeValueAsString(payload);
    }

    /**
     * 计算 HMAC-SHA256 签名
     *
     * @param secret    签名密钥，为空时返回 null
     * @param timestamp 时间戳
     * @param payload   请求体
     * @return {@code sha256=<hex>}，未配置密钥时返回 null
     */
    public static String sign(String secret, String timestamp, String payload) {
        if (isBlank(secret)) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal((timestamp + "." + payload).getBytes(StandardCharsets.UTF_8));
            return SIGNATURE_PREFIX + toHex(digest);
        } catch (Exception e) {
            log.warn("Webhook 签名计算失败: {}", e.getMessage());
            return null;
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            String hex = Integer.toHexString(b & 0xFF);
            if (hex.length() == 1) {
                builder.append('0');
            }
            builder.append(hex);
        }
        return builder.toString();
    }

    private static String safeType(String type) {
        return isBlank(type) ? "system" : type;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * 签名工具：便于运维在接收端校验（与发送端算法一致）
     */
    public static boolean verify(String secret, String timestamp, String payload, String signature) {
        String expected = sign(secret, timestamp, payload);
        if (expected == null || signature == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }
}
