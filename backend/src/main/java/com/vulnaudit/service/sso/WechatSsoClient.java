package com.vulnaudit.service.sso;

import com.fasterxml.jackson.databind.JsonNode;
import com.vulnaudit.entity.SsoProvider;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;

import java.util.Map;

/**
 * 微信开放平台扫码登录（t104）
 *
 * <p>差异点：
 * <ul>
 *   <li>授权端点是 <b>QRConnect</b>，参数名为 {@code appid}（不是 client_id），且必须带 {@code #wechat_redirect}；</li>
 *   <li>token 端点是 GET + query；</li>
 *   <li>userinfo 用 {@code openid + access_token}，昵称字段为中文键 {@code nickname}。</li>
 * </ul>
 */
public class WechatSsoClient extends AbstractSsoClient {

    public WechatSsoClient(SsoProvider provider) {
        super(provider);
    }

    @Override
    public String authorizeUrl(String state, String redirectUri) {
        String base = provider.getAuthorizeUrl();
        String url = base + (base.contains("?") ? "&" : "?")
                + "appid=" + encode(provider.getClientId())
                + "&redirect_uri=" + encode(redirectUri)
                + "&response_type=code"
                + "&scope=" + encode(provider.getScopes() == null || provider.getScopes().isBlank()
                        ? "snsapi_login" : provider.getScopes())
                + "&state=" + encode(state);
        return url + "#wechat_redirect";
    }

    @Override
    public SsoUserInfo exchange(String code) {
        String tokenUrl = provider.getTokenUrl()
                + (provider.getTokenUrl().contains("?") ? "&" : "?")
                + "appid=" + encode(provider.getClientId())
                + "&secret=" + encode(provider.getClientSecret())
                + "&code=" + encode(code)
                + "&grant_type=authorization_code";
        JsonNode token = get(tokenUrl, Map.of());
        String accessToken = token.path("access_token").asText(null);
        String openid = token.path("openid").asText(null);
        if (accessToken == null || openid == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "第三方登录校验失败，请重试");
        }
        JsonNode user = get(provider.getUserInfoUrl()
                        + "?access_token=" + encode(accessToken) + "&openid=" + encode(openid) + "&lang=zh_CN",
                Map.of());
        return new SsoUserInfo(provider.getCode(), openid,
                user.path("email").asText(null),
                user.path("nickname").asText(""));
    }
}
