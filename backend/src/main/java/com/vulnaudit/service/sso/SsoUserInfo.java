package com.vulnaudit.service.sso;

/**
 * 第三方身份信息（t104）
 *
 * @param provider    提供商代码（github / microsoft / google / wechat）
 * @param externalId  第三方稳定用户ID（GitHub id / 微信 openid / OIDC sub）
 * @param email       邮箱（可能为空；为空时该身份不能用于邮箱找回密码等能力）
 * @param displayName 展示名（昵称 / name）
 */
public record SsoUserInfo(String provider, String externalId, String email, String displayName) {
}
