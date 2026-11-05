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
 * 用户偏好实现（t96 / t100 / t112）
 *
 * <p>要点：
 * <ul>
 *   <li>只能读写自己（控制器固定传当前登录用户ID，不接收 userId 参数）；</li>
 *   <li>主题白名单**唯一来源**为 {@link LoginTheme}，非法值 → 400，消息走 t60 的 MessageSource（中/英），
 *       文案内含「支持: …」完整列表；</li>
 *   <li><b>三态（t112）</b>：{@code login_theme=NULL} = 跟随系统默认；具体 code = 用户指定。
 *       二者由 {@code followDefault} 与 {@code loginTheme} 二选一表达，同传/都不传 → 400；</li>
 *   <li>修改写审计：{@code action=UPDATE_LOGIN_THEME}、{@code resourceType=user}、{@code resourceId=用户ID}，
 *       details 记录 {@code from=… -> to=…}（NULL 记为 {@code <follow-default>}），值未变化时不写。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPreferenceServiceImpl implements UserPreferenceService {

    private static final String THEME_MESSAGE_KEY = "error.user.login_theme.unsupported";
    private static final String THEME_MESSAGE_FALLBACK = "不支持的登录页主题: {0}（支持: {1}）";

    /** 请求体二选一冲突 */
    private static final String CONFLICT_MESSAGE_KEY = "validation.user.login_theme.conflict";
    private static final String CONFLICT_MESSAGE_FALLBACK = "登录页主题与「跟随系统默认」只能二选一";

    /** 请求体两者都没给 */
    private static final String MISSING_MESSAGE_KEY = "validation.user.login_theme.missing";
    private static final String MISSING_MESSAGE_FALLBACK = "必须指定 loginTheme，或将 followDefault 设为 true";

    /** 审计 details 里 NULL（跟随默认）的占位符 */
    public static final String FOLLOW_DEFAULT_MARKER = "<follow-default>";

    private final UserRepository userRepository;
    private final AuditLogService auditLogService;
    private final ApiMessageResolver messageResolver;

    @Override
    @Transactional(readOnly = true)
    public UserPreferencesResponse getPreferences(@Nullable UUID userId) {
        User user = requireUser(userId);
        String theme = normalizeForRead(user.getLoginTheme());
        return new UserPreferencesResponse(theme, theme == null);
    }

    @Override
    @Transactional
    public UserPreferencesResponse updatePreferences(@Nullable UUID userId,
                                                     @Nullable String loginTheme,
                                                     @Nullable Boolean followDefault) {
        User user = requireUser(userId);

        boolean follow = Boolean.TRUE.equals(followDefault);
        boolean hasTheme = StringUtils.hasText(loginTheme);

        if (follow && hasTheme) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    messageResolver.resolve(CONFLICT_MESSAGE_KEY, CONFLICT_MESSAGE_FALLBACK));
        }
        if (!follow && !hasTheme) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    messageResolver.resolve(MISSING_MESSAGE_KEY, MISSING_MESSAGE_FALLBACK));
        }

        // follow=true → target 为 null（跟随默认）；否则白名单校验后写入具体 code
        String target = follow ? null : normalizeTheme(loginTheme);
        String previous = normalizeForRead(user.getLoginTheme());

        if (!Objects.equals(previous, target)) {
            user.setLoginTheme(target);
            userRepository.save(user);
            auditLogService.log(user.getId(), "UPDATE_LOGIN_THEME", "user", user.getId(),
                    "登录页主题变更: from=" + describe(previous) + " -> to=" + describe(target));
            log.info("用户更新登录页主题: userId={}, {} -> {}", user.getId(), describe(previous), describe(target));
        }
        return new UserPreferencesResponse(target, target == null);
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
     * 读取语义（三态）：
     * <ul>
     *   <li>null / 空白 → {@code null}（跟随系统默认）；</li>
     *   <li>合法 code → 原样归一；</li>
     *   <li>历史脏值（非法 code）→ 回落默认主题，不把脏值回吐给前端（t96 口径）。</li>
     * </ul>
     */
    private String normalizeForRead(@Nullable String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        LoginTheme theme = LoginTheme.fromCode(raw);
        if (theme == null) {
            log.warn("用户登录页主题存在脏值（{}），读取时回落默认主题 {}", raw, DEFAULT_LOGIN_THEME);
            return DEFAULT_LOGIN_THEME;
        }
        return theme.getCode();
    }

    /**
     * 组装「不支持的登录页主题: xxx（支持: …）」的中/英文案
     */
    private String unsupportedThemeMessage(String raw) {
        return messageResolver.resolve(THEME_MESSAGE_KEY, THEME_MESSAGE_FALLBACK,
                raw, LoginTheme.supportedCodes());
    }

    /** 审计/日志里 NULL 的可读写法 */
    private String describe(@Nullable String theme) {
        return theme == null ? FOLLOW_DEFAULT_MARKER : theme;
    }
}
