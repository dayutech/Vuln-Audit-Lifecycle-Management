package com.vulnaudit.dto;

import java.util.List;

/**
 * 登录页主题公开配置（t109）
 *
 * <p>{@code GET /api/v1/auth/login-theme}（无需认证）的响应体：
 * 登录页在认证之前调用它，拿到「管理员设置的缺省主题」与「全部可选主题」。
 *
 * <p>缺省值来源是 {@code system_settings.login.defaultTheme}（管理员可改）；
 * 若该行缺失或值非法，后端回落 {@code pencil}，**不会**让登录页 500。
 *
 * @param defaultTheme 缺省主题代码（管理员设置值；非法/缺失时回落 pencil）
 * @param themes       全部可选主题（顺序稳定，供选择器渲染）
 */
public record PublicLoginThemeResponse(String defaultTheme, List<LoginThemeOption> themes) {
}
