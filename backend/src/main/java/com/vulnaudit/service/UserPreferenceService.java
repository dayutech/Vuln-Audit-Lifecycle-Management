package com.vulnaudit.service;

import com.vulnaudit.dto.UserPreferencesResponse;
import com.vulnaudit.entity.enums.LoginTheme;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 用户偏好服务（t96 / t100）
 *
 * <p>当前仅承载「登录页主题」；所有读写都固定作用于调用者自己（{@code /users/me/preferences}）。
 *
 * <p>白名单与默认值**不在这里手写**：统一从 {@link LoginTheme} 派生，
 * 避免「枚举 / 白名单 / 默认值 / 错误消息 / 测试」五处各写一份字符串列表而互相脱节。
 */
public interface UserPreferenceService {

    /**
     * 可选主题白名单（8 项，顺序即错误消息中的展示顺序）
     *
     * <p>等价于 {@link LoginTheme#codes()}，保留该常量是为了给既有调用方/测试一个稳定入口；
     * 新增主题只需改 {@link LoginTheme}。
     */
    List<String> SUPPORTED_LOGIN_THEMES = LoginTheme.codes();

    /** 默认主题（不随白名单扩展而变化） */
    String DEFAULT_LOGIN_THEME = LoginTheme.DEFAULT.getCode();

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
     * @param userId     当前登录用户
     * @param loginTheme 目标主题（{@link LoginTheme} 中的 code，大小写与空格不敏感）
     * @throws com.vulnaudit.exception.BusinessException 非法主题 → 400（中英文案由 MessageSource 决定）
     */
    @Nonnull
    UserPreferencesResponse updatePreferences(@Nullable UUID userId, @Nullable String loginTheme);
}
