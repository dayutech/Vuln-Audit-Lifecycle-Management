package com.vulnaudit.service.impl;

import com.vulnaudit.dto.UserPreferencesResponse;
import com.vulnaudit.entity.User;
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

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * 用户偏好实现（t96）
 *
 * <p>要点：
 * <ul>
 *   <li>只能读写自己（控制器固定传当前登录用户ID，不接收 userId 参数）；</li>
 *   <li>主题白名单 {@link #SUPPORTED_LOGIN_THEMES}，非法值 → 400，消息走 t60 的 MessageSource（中/英）；</li>
 *   <li>修改写审计：{@code action=UPDATE_LOGIN_THEME}、{@code resourceType=user}、{@code resourceId=用户ID}，
 *       details 记录 {@code from=… → to=…}。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPreferenceServiceImpl implements UserPreferenceService {

    private static final String THEME_MESSAGE_KEY = "error.user.login_theme.unsupported";
    private static final String THEME_MESSAGE_FALLBACK = "不支持的登录页主题: {0}";

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
     */
    private String normalizeTheme(@Nullable String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    messageResolver.resolve(THEME_MESSAGE_KEY, THEME_MESSAGE_FALLBACK, ""));
        }
        String theme = raw.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED_LOGIN_THEMES.contains(theme)) {
            // resolve(key, fallback, args)：按 ?lang= → Accept-Language → 中文 选择语言，
            // 因此中文环境得到「不支持的登录页主题: xxx」，英文环境得到对应的英文文案。
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    messageResolver.resolve(THEME_MESSAGE_KEY, THEME_MESSAGE_FALLBACK, raw));
        }
        return theme;
    }

    private String normalizeOrDefault(@Nullable String theme) {
        if (!StringUtils.hasText(theme)) {
            return DEFAULT_LOGIN_THEME;
        }
        String normalized = theme.trim().toLowerCase(Locale.ROOT);
        return SUPPORTED_LOGIN_THEMES.contains(normalized) ? normalized : DEFAULT_LOGIN_THEME;
    }
}
