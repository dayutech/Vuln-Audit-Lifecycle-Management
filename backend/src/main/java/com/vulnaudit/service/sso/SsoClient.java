package com.vulnaudit.service.sso;

/**
 * 第三方 OAuth 客户端抽象（t104）
 *
 * <p><b>只做 HTTP 拼装</b>：凭据（client_id/secret）与三个端点 URL 全部来自 {@code sso_providers} 表，
 * 代码里不写死；实现类只负责按各平台协议拼接授权 URL、用 code 换 token、再取 userinfo。
 *
 * <p>测试用 {@code FakeSsoClient}（见 SsoFlowTest）替换，避免真实外网调用。
 */
public interface SsoClient {

    /**
     * 生成授权跳转地址
     *
     * @param state       CSRF state（5 分钟有效）
     * @param redirectUri 回调地址
     */
    String authorizeUrl(String state, String redirectUri);

    /**
     * 用授权码换取第三方用户信息
     *
     * @param code 授权码
     * @return 第三方身份
     */
    SsoUserInfo exchange(String code);
}
