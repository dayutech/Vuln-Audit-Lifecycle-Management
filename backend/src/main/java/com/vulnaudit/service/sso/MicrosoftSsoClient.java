package com.vulnaudit.service.sso;

import com.fasterxml.jackson.databind.JsonNode;
import com.vulnaudit.entity.SsoProvider;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;

import java.util.Map;

/**
 * Microsoft 登录（Microsoft identity platform / Graph，t104）
 *
 * <p>userinfo 用 Graph {@code /me}：id 为 {@code id}，邮箱优先 {@code mail}，回落 {@code userPrincipalName}。
 */
public class MicrosoftSsoClient extends AbstractSsoClient {

    public MicrosoftSsoClient(SsoProvider provider) {
        super(provider);
    }

    @Override
    public SsoUserInfo exchange(String code) {
        JsonNode token = postForm(provider.getTokenUrl(), tokenForm(code, null));
        String accessToken = token.path("access_token").asText(null);
        if (accessToken == null || accessToken.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "第三方登录校验失败，请重试");
        }
        JsonNode user = get(provider.getUserInfoUrl(), Map.of("Authorization", "Bearer " + accessToken));
        String externalId = user.path("id").asText(null);
        if (externalId == null || externalId.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "第三方登录校验失败，请重试");
        }
        String email = user.path("mail").asText(null);
        if (email == null || email.isBlank()) {
            email = user.path("userPrincipalName").asText(null);
        }
        return new SsoUserInfo(provider.getCode(), externalId, email,
                user.path("displayName").asText(""));
    }
}
