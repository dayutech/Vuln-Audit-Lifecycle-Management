package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 登录页可见的 SSO 提供商（t104）
 *
 * <p>只包含「管理员已开启 **且** 已配置凭据」的提供商；未配置的不展示，避免用户点进去才失败。
 *
 * @param code 提供商代码（github/microsoft/google/wechat）
 * @param name 展示名
 * @param icon 图标标识
 */
@Schema(description = "SSO 提供商选项")
public record SsoProviderOption(String code, String name, String icon) {
}
