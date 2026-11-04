package com.vulnaudit.dto;

import com.vulnaudit.entity.enums.LoginTheme;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 更新用户偏好请求（t96 / t100）
 *
 * <p><b>只能改自己</b>：控制器固定使用当前登录用户（{@code /users/me/preferences}），
 * 请求体里即便带 {@code userId} 也会被忽略（不存在该字段）。
 *
 * <p><b>字段名固定为 {@code loginTheme}</b>（前端 t97 已按此实现，不要改名）。
 *
 * <p><b>白名单单一来源</b>：可选值只在 {@link LoginTheme} 声明一次；本 DTO 只做「非空 + 长度」这类
 * 结构校验，取值合法性由服务层用 {@link LoginTheme#isValid(String)} 判定 —— 这样 400 的文案能带上
 * 完整「支持: …」列表并由 MessageSource 做中英切换。新增主题无需改本文件。
 *
 * @param loginTheme 登录页主题代码（{@link LoginTheme#getCode()}，大小写与首尾空格不敏感）
 */
public record UpdateUserPreferencesRequest(
        @NotBlank(message = "validation.user.login_theme.required")
        @Size(max = LoginTheme.MAX_CODE_LENGTH, message = "validation.user.login_theme.too_long")
        @Schema(description = "登录页主题（8 项：pencil / geometric / owl / pixel-eyes / aurora / mono-grid / orbit / big-type）",
                example = "owl", requiredMode = Schema.RequiredMode.REQUIRED)
        String loginTheme) {
}
