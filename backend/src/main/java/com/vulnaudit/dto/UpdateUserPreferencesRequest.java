package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 更新用户偏好请求（t96）
 *
 * <p><b>只能改自己</b>：控制器固定使用当前登录用户（{@code /users/me/preferences}），
 * 请求体里即便带 {@code userId} 也会被忽略（不存在该字段）。
 *
 * @param loginTheme 登录页主题（白名单：pencil / geometric / owl / pixel-eyes）
 */
public record UpdateUserPreferencesRequest(
        @NotBlank(message = "validation.user.login_theme.required")
        @Size(max = 32, message = "validation.user.login_theme.too_long")
        @Schema(description = "登录页主题", example = "owl", requiredMode = Schema.RequiredMode.REQUIRED)
        String loginTheme) {
}