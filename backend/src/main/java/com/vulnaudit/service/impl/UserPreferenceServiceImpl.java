package com.vulnaudit.service.impl;

import com.vulnaudit.dto.UserPreferencesResponse;
import com.vulnaudit.entity.User;
import com.vulnaudit.entity.enums.LoginTheme;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.i18n.ApiMessageResolver;
import com.vulnaudit.repository.UserRepository;
import com.vulnaudit.service.AuditLogService;
import com.vulnaudit.service.UserPreferenceService;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.UUID;

/**
 * 用户偏好实现（t96 / t100）
 *
 * <p>要点：
 * <ul>
 *   <li>只能读写自己（控制器固定传当前登录用户ID，不接收 userId 参数）；</li>
 *   <li>主题白名单**唯一来源**为 {@link LoginTheme}（{@link LoginTheme#isValid(String)}），
 *       非法值 → 400，消息走 t60 的 MessageSource（中/英），文案内含「支持: …」完整列表；</li>
 *   <li>修改写审计：{@code action=UPDATE_LOGIN_THEME}、{@code resourceType=user}、{@code resourceId=用户ID}，
 *       details 记录 {@code from=… -> to=…}，值未变化时不写。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPreferenceServiceImpl implements UserPreferenceService {

    private static final String THEME_MESSAGE_KEY = "error.user.login_theme.unsupported";
    private static final String THEME_MESSAGE_FALLBACK = "不支持的登录页主题: {0}（支持: {1}）";

    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final ApiMessageResolver messageResolver;

    @Override
    @Transactional(readOnly = true)
    public UserPreferencesResponse getPreferences(@Nullable UUID userId) {
        User user = requireUser(userId);
        return new UserPreferencesResponse(normalizeOrDefault(user.getLoginTheme()));
    }

    @Override
    @Transactional
    public UserPreferencesResponse updatePreferences(@Nullable UUID userId, @Nullable String loginTheme) {
        User user = requireUser(userId);
        String target = normalizeTheme(loginTheme);
        String previous = normalizeOrDefault(user.getLoginTheme());

        if (!Objects.equals(previous, target)) {
            user.setLoginTheme(target);
            userRepository.save(user);
            auditLogService.log(user.getId(), "UPDATE_LOGIN_THEME", "user", user.getId(),
                    "登录页主题变更: from=" + previous + " -> to=" + target);
            log.info("用户更新登录页主题: userId={}, {} -> {}", user.getId(), previous, target);
        }
        return new UserPreferencesResponse(target);
    }

    // ------------------------------------------------------------------ 内部

    private User requireUser(@Nullable UUID userId) {
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未认证，无法读取用户偏好");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "用户不存在: " + userId));
    }

    /**
     * 白名单校验：非法值抛 400，消息由 MessageSource 按请求语言解析（中/英）
     *
     * <p>文案形如「不支持的登录页主题: neon（支持: pencil, geometric, owl, pixel-eyes, aurora, mono-grid, orbit, big-type）」，
     * 支持列表由 {@link LoginTheme#supportedCodes()} 生成，加主题后自动同步。
     */
    private String normalizeTheme(@Nullable String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        LoginTheme theme = LoginTheme.fromCode(trimmed);
        if (theme == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, unsupportedThemeMessage(trimmed));
        }
        return theme.getCode();
    }

    /**
     * 读取时容忍历史脏数据：非法/空白回落默认主题，不把脏值回吐给前端
     */
    private String normalizeOrDefault(@Nullable String theme) {
        return StringUtils.hasText(theme)
                ? LoginTheme.fromCodeOrDefault(theme).getCode()
                : DEFAULT_LOGIN_THEME;
    }

    /**
     * 组装「不支持的登录页主题: xxx（支持: …）」的中/英文案
     */
    private String unsupportedThemeMessage(String raw) {
        return messageResolver.resolve(THEME_MESSAGE_KEY, THEME_MESSAGE_FALLBACK,
                raw, LoginTheme.supportedCodes());
    }
}
