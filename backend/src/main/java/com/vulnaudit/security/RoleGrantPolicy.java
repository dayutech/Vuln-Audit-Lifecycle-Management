package com.vulnaudit.security;

import com.vulnaudit.entity.Role;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 角色授予边界（t115，单一事实来源）
 *
 * <h3>为什么需要</h3>
 * 前端「按最小权限收敛」只隐藏了按钮，**不是强制**：PROJECT_LEADER 直接调
 * {@code POST /admin/registration-requests/{id}/approve {"roles":["admin"]}} 就能自助提权。
 * 角色「可授予集合」必须在后端判定，并且**所有**角色写入入口都走这里。
 *
 * <h3>边界</h3>
 * <ul>
 *   <li><b>受限角色</b>：{@code roles.is_system = true} 的角色（内置管理类），以及代码为
 *       {@code admin} / {@code project_leader} 的角色 —— 只有 <b>ADMIN</b> 能授予；</li>
 *   <li><b>业务角色</b>（vulnerability_engineer / team_leader / auditor 等）—— ADMIN 与 PROJECT_LEADER 均可授予；</li>
 *   <li>受限角色被非 ADMIN 授予 → <b>403</b>「无权授予管理类角色: admin」，调用方必须**先行失败**、
 *       不得留下任何副作用（不建号、不改动角色、不写成功审计）。</li>
 * </ul>
 *
 * <h3>「谁能授予」的判定</h3>
 * 只看当前请求的认证信息（{@code ROLE_ADMIN}）。**无认证上下文**（如启动初始化、管理员触发的批处理内部调用）
 * 视为系统内部调用放行，但会打 WARN 便于审计；Web 请求一律有认证上下文，因此不会成为绕过口子。
 */
@Slf4j
@Component
public class RoleGrantPolicy {

    /** 永远视为受限的角色代码（即便库里 is_system=false 也不会被下放） */
    public static final Set<String> ALWAYS_RESTRICTED_CODES = Set.of("admin", "project_leader");

    /**
     * 是否为「受限角色」（只有 ADMIN 可授予）
     */
    public boolean isRestricted(Role role) {
        if (role == null) {
            return false;
        }
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            return true;
        }
        return ALWAYS_RESTRICTED_CODES.contains(normalize(role.getCode()));
    }

    /**
     * 当前请求的操作者是否 ADMIN
     */
    public boolean currentActorIsAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(RoleAuthority.ADMIN::equals);
    }

    /**
     * 当前是否有认证上下文（用于区分「系统内部调用」与「普通请求」）
     */
    public boolean hasAuthenticationContext() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getName());
    }

    /**
     * 校验当前操作者是否可以把这些角色授予别人
     *
     * @param roles 待授予的角色（已按角色码解析）
     * @throws BusinessException 403 存在受限角色且操作者不是 ADMIN
     */
    public void assertCanGrant(Collection<Role> roles) {
        List<String> restricted = restrictedCodes(roles);
        if (restricted.isEmpty()) {
            return;
        }
        if (currentActorIsAdmin()) {
            return;
        }
        if (!hasAuthenticationContext()) {
            // 系统内部调用（无请求上下文）：放行但留痕，避免把初始化/批处理误伤
            log.warn("无认证上下文的角色授予命中受限角色，按系统内部调用放行: roles={}", restricted);
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN,
                "无权授予管理类角色: " + String.join(", ", restricted));
    }

    /**
     * 其中的受限角色码（用于报错与审计）
     */
    public List<String> restrictedCodes(Collection<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of();
        }
        Set<String> codes = new LinkedHashSet<>();
        for (Role role : roles) {
            if (isRestricted(role)) {
                codes.add(normalize(role.getCode()));
            }
        }
        return List.copyOf(codes);
    }

    private String normalize(String code) {
        return code == null ? "" : code.trim().toLowerCase(Locale.ROOT);
    }
}
