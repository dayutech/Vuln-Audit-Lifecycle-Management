package com.vulnaudit.service.sso;

import com.fasterxml.jackson.databind.JsonNode;
import com.vulnaudit.entity.SsoProvider;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;

import java.util.Map;

/**
 * Google 登录（OpenID Connect，t104）
 *
 * <p>授权端点 {@code response_type=code}；token 端点返回 JSON；userinfo 取 {@code sub/email/name}。
 */
public class GoogleSsoClient extends AbstractSsoClient {

    public GoogleSsoClient(SsoProvider provider) {
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
        String externalId = user.path("sub").asText(null);
        if (externalId == null || externalId.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "第三方登录校验失败，请重试");
        }
        return new SsoUserInfo(provider.getCode(), externalId,
                user.path("email").asText(null),
                user.path("name").asText(user.path("email").asText("")));
    }
}
