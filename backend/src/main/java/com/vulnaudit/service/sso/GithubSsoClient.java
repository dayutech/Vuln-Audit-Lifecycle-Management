package com.vulnaudit.service.sso;

import com.fasterxml.jackson.databind.JsonNode;
import com.vulnaudit.entity.SsoProvider;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;

import java.util.Map;

/**
 * GitHub OAuth（t104）
 *
 * <p>token 端点默认返回表单文本，因此请求头带 {@code Accept: application/json}；
 * 用户信息取 {@code /user}（id/login/name），邮箱取 {@code /user/emails} 的 primary+verified 项。
 */
public class GithubSsoClient extends AbstractSsoClient {

    public GithubSsoClient(SsoProvider provider) {
        super(provider);
    }

    @Override
    public SsoUserInfo exchange(String code) {
        JsonNode token = postForm(provider.getTokenUrl(), tokenForm(code, null));
        String accessToken = token.path("access_token").asText(null);
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "第三方登录校验失败，请重试");
        }
        JsonNode user = get(provider.getUserInfoUrl(),
                Map.of("Authorization", "Bearer " + accessToken));
        String externalId = user.path("id").asText(null);
        if (externalId == null || externalId.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "第三方登录校验失败，请重试");
        }
        String displayName = user.path("name").asText(user.path("login").asText(""));
        String email = user.path("email").asText(null);
        if (email == null || email.isBlank()) {
            email = fetchPrimaryEmail(accessToken);
        }
        return new SsoUserInfo(provider.getCode(), externalId, email, displayName);
    }

    private String fetchPrimaryEmail(String accessToken) {
        try {
            JsonNode emails = get(provider.getUserInfoUrl() + "/emails",
                    Map.of("Authorization", "Bearer " + accessToken));
            if (emails.isArray()) {
                for (JsonNode item : emails) {
                    if (item.path("primary").asBoolean(false) && item.path("verified").asBoolean(false)) {
                        return item.path("email").asText(null);
                    }
                }
            }
        } catch (RuntimeException e) {
            // 邮箱是可选信息，取不到不影响登录（只是不能用邮箱找回密码）
            return null;
        }
        return null;
    }
}
