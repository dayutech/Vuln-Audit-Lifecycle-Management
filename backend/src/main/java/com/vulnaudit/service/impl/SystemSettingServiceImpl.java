package com.vulnaudit.service.impl;

import com.vulnaudit.dto.LoginThemeOption;
import com.vulnaudit.dto.LoginThemeSettingResponse;
import com.vulnaudit.dto.PublicLoginThemeResponse;
import com.vulnaudit.entity.SystemSetting;
import com.vulnaudit.entity.User;
import com.vulnaudit.entity.enums.LoginTheme;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.i18n.ApiMessageResolver;
import com.vulnaudit.repository.SystemSettingRepository;
import com.vulnaudit.repository.UserRepository;
import com.vulnaudit.service.AuditLogService;
import com.vulnaudit.service.SystemSettingService;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 系统设置实现（t109）
 *
 * <p>登录页缺省主题 {@code login.defaultTheme} 的读写、启动自愈、审计与公开视图。
 * 白名单复用 t100 的 {@link LoginTheme}（枚举即单一来源），非法值文案与用户偏好接口共用
 * {@code error.user.login_theme.unsupported}（含「支持: …」完整列表）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemSettingServiceImpl implements SystemSettingService {

    private static final String THEME_MESSAGE_KEY = "error.user.login_theme.unsupported";
    private static final String THEME_MESSAGE_FALLBACK = "不支持的登录页主题: {0}（支持: {1}）";

    private final SystemSettingRepository systemSettingRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final ApiMessageResolver messageResolver;

    // ------------------------------------------------------------------ 启动自愈

    /**
     * 应用就绪后幂等补齐缺省行；失败只记 WARN，绝不让启动/登录流程挂掉
     */
    @EventListener(ApplicationReadyEvent.class)
    public void bootstrapOnStartup() {
        try {
            ensureDefaults();
        } catch (RuntimeException e) {
            log.warn("系统设置启动自愈失败（不影响启动，读取时仍会兜底 pencil）: {}", e.getMessage());
        }
    }

    @Override
    @Transactional
    public boolean ensureDefaults() {
        if (systemSettingRepository.existsBySettingKey(LOGIN_DEFAULT_THEME_KEY)) {
            return false;
        }
        try {
            systemSettingRepository.saveAndFlush(SystemSetting.builder()
                    .settingKey(LOGIN_DEFAULT_THEME_KEY)
                    .settingValue(LoginTheme.DEFAULT.getCode())
                    .updatedBy(null)
                    .updatedAt(LocalDateTime.now())
                    .build());
            log.info("系统设置初始化: {}={}", LOGIN_DEFAULT_THEME_KEY, LoginTheme.DEFAULT.getCode());
            return true;
        } catch (DataIntegrityViolationException e) {
            // 并发启动（多实例）时唯一键冲突 → 视为已被其他实例插入
            log.debug("系统设置已被并发初始化: {}", LOGIN_DEFAULT_THEME_KEY);
            return false;
        }
    }

    // ------------------------------------------------------------------ 读取

    @Override
    @Transactional
    public String getDefaultLoginTheme() {
        return readDefaultLoginTheme();
    }

    @Override
    @Transactional
    public PublicLoginThemeResponse getPublicLoginTheme() {
        List<LoginThemeOption> themes = LoginTheme.codes().stream()
                .map(LoginThemeOption::new)
                .toList();
        return new PublicLoginThemeResponse(readDefaultLoginTheme(), themes);
    }

    @Override
    @Transactional(readOnly = true)
    public LoginThemeSettingResponse getLoginThemeSetting() {
        SystemSetting setting = systemSettingRepository.findBySettingKey(LOGIN_DEFAULT_THEME_KEY).orElse(null);
        if (setting == null) {
            return new LoginThemeSettingResponse(LoginTheme.DEFAULT.getCode(), null, null);
        }
        return new LoginThemeSettingResponse(
                normalizeOrDefault(setting.getSettingValue()),
                resolveUpdatedByUsername(setting.getUpdatedBy()),
                setting.getUpdatedAt());
    }

    // ------------------------------------------------------------------ 写入

    @Override
    @Transactional
    public LoginThemeSettingResponse updateDefaultLoginTheme(@Nullable UUID actorId, @Nullable String rawTheme) {
        LoginTheme target = LoginTheme.fromCode(rawTheme);
        if (target == null) {
            String raw = rawTheme == null ? "" : rawTheme.trim();
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    messageResolver.resolve(THEME_MESSAGE_KEY, THEME_MESSAGE_FALLBACK,
                            raw, LoginTheme.supportedCodes()));
        }

        SystemSetting setting = systemSettingRepository.findBySettingKey(LOGIN_DEFAULT_THEME_KEY)
                .orElseGet(() -> systemSettingRepository.save(SystemSetting.builder()
                        .settingKey(LOGIN_DEFAULT_THEME_KEY)
                        .settingValue(LoginTheme.DEFAULT.getCode())
                        .updatedAt(LocalDateTime.now())
                        .build()));

        String previous = normalizeOrDefault(setting.getSettingValue());
        String next = target.getCode();
        if (Objects.equals(previous, next)) {
            // 值未变化：保持 200，但不写审计（与 t96 用户偏好口径一致，避免噪声）
            log.debug("登录页缺省主题未变化，跳过写入: {}", next);
            return getLoginThemeSetting();
        }

        setting.setSettingValue(next);
        setting.setUpdatedBy(actorId);
        setting.setUpdatedAt(LocalDateTime.now());
        systemSettingRepository.save(setting);

        UUID resourceId = setting.getId();
        auditLogService.log(actorId, ACTION_UPDATE_SYSTEM_SETTING, RESOURCE_TYPE_SYSTEM_SETTING, resourceId,
                "登录页缺省主题变更: from=" + previous + " -> to=" + next);
        log.info("管理员修改登录页缺省主题: actor={}, {} -> {}", actorId, previous, next);

        return new LoginThemeSettingResponse(next, resolveUpdatedByUsername(actorId), setting.getUpdatedAt());
    }

    // ------------------------------------------------------------------ 内部

    /**
     * 读缺省主题：缺失 → 自愈插入；非法值 → WARN + 回落 pencil（不抛异常、不改库）
     */
    private String readDefaultLoginTheme() {
        SystemSetting setting = systemSettingRepository.findBySettingKey(LOGIN_DEFAULT_THEME_KEY).orElse(null);
        if (setting == null) {
            ensureDefaults();
            return LoginTheme.DEFAULT.getCode();
        }
        return normalizeOrDefault(setting.getSettingValue());
    }

    /**
     * 值归一：空白/非法一律回落默认主题，并记录 WARN（便于运维发现被手工改坏的配置）
     */
    private String normalizeOrDefault(@Nullable String raw) {
        LoginTheme theme = LoginTheme.fromCode(raw);
        if (theme == null) {
            log.warn("系统设置 {} 的值非法（{}），回落默认主题 {}",
                    LOGIN_DEFAULT_THEME_KEY, raw == null ? "null" : raw, LoginTheme.DEFAULT.getCode());
            return LoginTheme.DEFAULT.getCode();
        }
        return theme.getCode();
    }

    private String resolveUpdatedByUsername(@Nullable UUID updatedBy) {
        if (updatedBy == null) {
            return null;
        }
        return userRepository.findById(updatedBy).map(User::getUsername).orElse(updatedBy.toString());
    }
}
