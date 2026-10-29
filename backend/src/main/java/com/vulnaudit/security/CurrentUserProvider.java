package com.vulnaudit.security;

import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * 当前登录用户解析（统一入口）
 *
 * <p>背景：多个实体存在 NOT NULL 的审计字段（{@code created_by / uploaded_by / author_id /
 * assigned_by / owner_id …}），此前各控制器/服务各自实现 {@code getCurrentUserId()}，
 * 极易出现「忘记赋值 → NULL → 500」的漏写（Tool.createdBy、ToolVersion.createdBy 均如此）。
 *
 * <p>本类作为唯一解析入口：
 * <ul>
 *     <li>{@link #currentUserId()} 取不到时返回 {@link Optional#empty()}，供「可选场景」使用；</li>
 *     <li>{@link #requireCurrentUserId()} 取不到直接抛 401，供「审计字段必填」的写路径使用，
 *         把 500 变成可读的 401。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CurrentUserProvider {

    private final UserRepository userRepository;

    /**
     * 当前登录用户名（未登录返回空）
     */
    public Optional<String> currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return Optional.empty();
        }
        String name = authentication.getName();
        if (name == null || name.isBlank() || "anonymousUser".equals(name)) {
            return Optional.empty();
        }
        return Optional.of(name);
    }

    /**
     * 当前登录用户ID（未登录/用户不存在返回空）
     */
    public Optional<UUID> currentUserId() {
        return currentUsername().flatMap(username -> userRepository.findByUsername(username))
                .map(user -> user.getId());
    }

    /**
     * 强制要求当前登录用户，用于写 NOT NULL 审计字段
     *
     * @throws BusinessException 401 未授权
     */
    public UUID requireCurrentUserId() {
        return currentUserId().orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "未授权"));
    }

    /**
     * 取当前用户ID；取不到时使用给定兜底值，兜底值也为空则抛 401
     */
    public UUID currentUserIdOrElse(UUID fallback) {
        return currentUserId().orElseGet(() -> {
            if (fallback == null) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "未授权");
            }
            log.debug("无法解析当前登录用户，使用兜底用户: {}", fallback);
            return fallback;
        });
    }
}
