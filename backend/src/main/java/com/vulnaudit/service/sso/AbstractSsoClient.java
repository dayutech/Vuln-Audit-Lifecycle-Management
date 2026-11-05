package com.vulnaudit.service.sso;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vulnaudit.entity.SsoProvider;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 各 OAuth 提供商的公共 HTTP 模板（t104）
 *
 * <p>只做拼装：授权 URL 由 {@code authorize_url + client_id + redirect_uri + scope + state} 组成；
 * 换 token 与取用户信息是两个表单/GET 请求，凭据与端点全部来自 {@link SsoProvider}。
 *
 * <p>异常统一转成 400（授权码无效/换取失败），不把第三方原始报文透传给前端。
 */
@Slf4j
public abstract class AbstractSsoClient implements SsoClient {

    protected static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();
    protected static final ObjectMapper MAPPER = new ObjectMapper();

    protected final SsoProvider provider;

    protected AbstractSsoClient(SsoProvider provider) {
        this.provider = provider;
    }

    @Override
    public String authorizeUrl(String state, String redirectUri) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(provider.getAuthorizeUrl())
                .queryParam("client_id", provider.getClientId())
                .queryParam("redirect_uri", redirectUri)
                .queryParam("response_type", "code")
                .queryParam("state", state);
        if (provider.getScopes() != null && !provider.getScopes().isBlank()) {
            builder.queryParam("scope", provider.getScopes());
        }
        customAuthorizeParams(builder);
        return builder.build().toUriString();
    }

    /** 子类可追加平台特有参数（如微信的 appid/response_type=code） */
    protected void customAuthorizeParams(UriComponentsBuilder builder) {
        // 默认无额外参数
    }

    /** 子类实现：用 code 换 token 并取用户信息 */
    @Override
    public abstract SsoUserInfo exchange(String code);

    // ------------------------------------------------------------------ HTTP 辅助

    protected JsonNode postForm(String url, Map<String, String> form) {
        String body = form.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        return send(request);
    }

    protected JsonNode get(String url, Map<String, String> headers) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET();
        headers.forEach(builder::header);
        return send(builder.build());
    }

    private JsonNode send(HttpRequest request) {
        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("SSO 第三方接口返回非 2xx: provider={}, status={}", provider.getCode(), response.statusCode());
                throw new BusinessException(ErrorCode.BAD_REQUEST, "第三方登录校验失败，请重试");
            }
            return MAPPER.readTree(response.body());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("SSO 第三方接口调用失败: provider={}, error={}", provider.getCode(), e.getMessage());
            throw new BusinessException(ErrorCode.BAD_REQUEST, "第三方登录校验失败，请重试");
        }
    }

    protected Map<String, String> tokenForm(String code, String redirectUri) {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("client_id", provider.getClientId());
        form.put("client_secret", provider.getClientSecret());
        form.put("code", code);
        form.put("grant_type", "authorization_code");
        if (redirectUri != null && !redirectUri.isBlank()) {
            form.put("redirect_uri", redirectUri);
        }
        return form;
    }

    protected String encode(String raw) {
        return URLEncoder.encode(raw == null ? "" : raw, StandardCharsets.UTF_8);
    }
}
