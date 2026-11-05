package com.vulnaudit.dto;

/**
 * 可选登录页主题（t109）
 *
 * <p>公开端点 {@code GET /api/v1/auth/login-theme} 用它把 8 个主题代码回给登录页，
 * 前端据此渲染主题选择器（主题名/副标题由前端 i18n 提供，后端只暴露稳定 code）。
 *
 * @param code 主题代码，取值见 {@link com.vulnaudit.entity.enums.LoginTheme}
 */
public record LoginThemeOption(String code) {
}
