package com.vulnaudit.service;

import com.vulnaudit.dto.UserPreferencesResponse;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.UUID;

/**
 * 用户偏好服务（t96）
 *
 * <p>当前仅承载「登录页主题」；所有读写都固定作用于调用者自己（{@code /users/me/preferences}）。
 */
public interface UserPreferenceService {

    /** 可选主题白名单 */
    java.util.List<String> SUPPORTED_LOGIN_THEMES =
            java.util.List.of("pencil", "geometric", "owl", "pixel-eyes");

    /** 默认主题 */
    String DEFAULT_LOGIN_THEME = "pencil";

    /**
     * 读取自己的偏好（默认 pencil）
     *
     * @param userId 当前登录用户
     */
    @Nonnull
    UserPreferencesResponse getPreferences(@Nullable UUID userId);

    /**
     * 更新自己的偏好（白名单校验；写审计 UPDATE_LOGIN_THEME）
     *
     * @param userId    当前登录用户
     * @param loginTheme 目标主题
     * @throws com.vulnaudit.exception.BusinessException 非法主题 → 400（中英文案由 MessageSource 决定）
     */
    @Nonnull
    UserPreferencesResponse updatePreferences(@Nullable UUID userId, @Nullable String loginTheme);
}
