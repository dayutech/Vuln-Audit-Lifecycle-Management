package com.vulnaudit.service.notify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.vulnaudit.config.NotificationProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Webhook 通道发送器单元测试
 *
 * <p>使用本地回环 HTTP 服务验证：JSON 报文、HMAC-SHA256 签名头、失败重试（最多 3 次）与降级。
 */
@DisplayName("Webhook 通道发送器测试")
class WebhookSenderTest {

    private static final String SECRET = "top-secret-key";

    private HttpServer server;
    private NotificationProperties properties;
    private WebhookSender webhookSender;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AtomicInteger attempts = new AtomicInteger();
    private final AtomicReference<String> lastSignature = new AtomicReference<>();
    private final AtomicReference<String> lastTimestamp = new AtomicReference<>();
    private final AtomicReference<String> lastEvent = new AtomicReference<>();
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private final List<Integer> statusSequence = new CopyOnWriteArrayList<>();
    private final int defaultStatus = 200;

    @BeforeEach
    void setUp() throws IOException {
        attempts.set(0);
        statusSequence.clear();
        lastSignature.set(null);
        lastTimestamp.set(null);
        lastEvent.set(null);
        lastBody.set(null);

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/hook", exchange -> {
            attempts.incrementAndGet();
            byte[] body = exchange.getRequestBody().readAllBytes();
            lastBody.set(new String(body, StandardCharsets.UTF_8));
            lastSignature.set(exchange.getRequestHeaders().getFirst(WebhookSender.HEADER_SIGNATURE));
            lastTimestamp.set(exchange.getRequestHeaders().getFirst(WebhookSender.HEADER_TIMESTAMP));
            lastEvent.set(exchange.getRequestHeaders().getFirst(WebhookSender.HEADER_EVENT));
            int status = statusSequence.isEmpty() ? defaultStatus : statusSequence.remove(0);
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
        });
        server.start();

        properties = new NotificationProperties();
        NotificationProperties.Webhook webhook = properties.getWebhook();
        webhook.setUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/hook");
        webhook.setSecret(SECRET);
        webhook.setTimeoutMs(2000);
        webhook.setMaxRetries(3);
        webhook.setRetryBackoffMs(1);
        webhookSender = new WebhookSender(properties, objectMapper);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    private NotificationMessage message() {
        return NotificationMessage.builder()
                .notificationId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .username("工程师")
                .type("vulnerability_discovered")
                .title("发现高危漏洞")
                .content("CVE-2026-0001 需要确认")
                .priority("high")
                .relatedType("vulnerability")
                .relatedId(UUID.randomUUID())
                .build();
    }

    private static String expectedSignature(String timestamp, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal((timestamp + "." + body).getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) {
            hex.append(String.format("%02x", b));
        }
        return "sha256=" + hex;
    }

    @Test
    @DisplayName("默认未启用 - 直接跳过，不发起 HTTP 请求")
    void disabled_skipsWithoutHttpCall() {
        properties.getWebhook().setEnabled(false);

        ChannelSendResult result = webhookSender.send(message());

        assertTrue(result.skipped());
        assertFalse(result.success());
        assertEquals(0, attempts.get());
    }

    @Test
    @DisplayName("启用但 url 为空 - 视为未启用并降级")
    void enabledWithoutUrl_isNotEnabled() {
        properties.getWebhook().setEnabled(true);
        properties.getWebhook().setUrl("");

        assertFalse(webhookSender.isEnabled());
        assertTrue(webhookSender.send(message()).skipped());
    }

    @Test
    @DisplayName("推送成功 - 携带 JSON 报文与合法 HMAC-SHA256 签名头")
    void send_success_carriesSignedPayload() throws Exception {
        properties.getWebhook().setEnabled(true);

        ChannelSendResult result = webhookSender.send(message());

        assertTrue(result.success());
        assertEquals(1, attempts.get());

        JsonNode json = objectMapper.readTree(lastBody.get());
        assertNotNull(json.get("notificationId"));
        assertEquals("vulnerability_discovered", json.get("event").asText());
        assertEquals("发现高危漏洞", json.get("title").asText());
        assertEquals("vulnerability_discovered", lastEvent.get());

        assertNotNull(lastSignature.get());
        assertEquals(expectedSignature(lastTimestamp.get(), lastBody.get()), lastSignature.get());
        assertTrue(WebhookSender.verify(SECRET, lastTimestamp.get(), lastBody.get(), lastSignature.get()));
    }

    @Test
    @DisplayName("前两次失败第三次成功 - 最多重试 3 次后返回成功")
    void send_retriesThenSucceeds() {
        properties.getWebhook().setEnabled(true);
        statusSequence.add(500);
        statusSequence.add(502);

        ChannelSendResult result = webhookSender.send(message());

        assertTrue(result.success());
        assertEquals(3, attempts.get());
        assertEquals(3, result.attempts());
    }

    @Test
    @DisplayName("持续 5xx - 3 次后返回失败并记录错误")
    void send_allAttemptsFail_returnsFailure() {
        properties.getWebhook().setEnabled(true);
        statusSequence.add(500);
        statusSequence.add(500);
        statusSequence.add(500);

        ChannelSendResult result = webhookSender.send(message());

        assertFalse(result.success());
        assertFalse(result.skipped());
        assertEquals(3, attempts.get());
        assertEquals(3, result.attempts());
        assertNotNull(result.error());
        assertTrue(result.error().contains("HTTP 500"));
    }

    @Test
    @DisplayName("未配置密钥 - 不携带签名头但仍可投递")
    void send_withoutSecret_omitsSignatureHeader() {
        properties.getWebhook().setEnabled(true);
        properties.getWebhook().setSecret(null);

        ChannelSendResult result = webhookSender.send(message());

        assertTrue(result.success());
        assertNull(lastSignature.get());
    }

    @Test
    @DisplayName("目标不可达 - 捕获异常并返回失败，不抛出")
    void send_unreachableTarget_returnsFailure() {
        properties.getWebhook().setEnabled(true);
        properties.getWebhook().setMaxRetries(1);
        // 端口 1 基本不会监听，用于验证连接异常被吞掉
        properties.getWebhook().setUrl("http://127.0.0.1:1/hook");

        ChannelSendResult result = webhookSender.send(message());

        assertFalse(result.success());
        assertNotNull(result.error());
    }
}
