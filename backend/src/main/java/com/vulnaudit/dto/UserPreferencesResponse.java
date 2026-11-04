package com.vulnaudit.dto;

/**
 * 用户偏好（t96）
 *
 * @param loginTheme 登录页主题：pencil / geometric / owl / pixel-eyes
 */
public record UserPreferencesResponse(String loginTheme) {
}