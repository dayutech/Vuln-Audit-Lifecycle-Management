package com.vulnaudit.dto;

import com.vulnaudit.entity.enums.LoginTheme;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 修改登录页缺省主题请求（t109）
 *
 * <p>字段名固定为 {@code defaultTheme}（区别于用户偏好的 {@code loginTheme}：
 * 后者是「我的选择」，前者是「全站缺省」）。取值白名单同 {@link LoginTheme}。
 *
 * @param defaultTheme 缺省主题代码（8 项之一）
 */
public record UpdateLoginThemeSettingRequest(
        @NotBlank(message = "validation.user.login_theme.required")
        @Size(max = LoginTheme.MAX_CODE_LENGTH, message = "validation.user.login_theme.too_long")
        @Schema(description = "登录页缺省主题（8 项：pencil / geometric / owl / pixel-eyes / aurora / mono-grid / orbit / big-type）",
                example = "owl", requiredMode = Schema.RequiredMode.REQUIRED)
        String defaultTheme) {
}
