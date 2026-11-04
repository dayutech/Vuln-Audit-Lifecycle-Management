package com.vulnaudit.dto;

/**
 * 用户偏好（t96 / t100）
 *
 * <p>字段名固定为 {@code loginTheme}（前端 t97 已按此实现）。
 *
 * @param loginTheme 登录页主题代码，取值见 {@link com.vulnaudit.entity.enums.LoginTheme}
 *                   （8 项：pencil / geometric / owl / pixel-eyes / aurora / mono-grid / orbit / big-type）
 */
public record UserPreferencesResponse(String loginTheme) {
}