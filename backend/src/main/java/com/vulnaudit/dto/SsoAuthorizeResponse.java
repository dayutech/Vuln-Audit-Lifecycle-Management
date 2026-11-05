package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * SSO 授权跳转信息（t104）
 *
 * @param url   第三方授权地址（前端直接 location 跳转）
 * @param state CSRF state（5 分钟有效，一次性）
 */
@Schema(description = "SSO 授权跳转信息")
public record SsoAuthorizeResponse(String url, String state) {
}
