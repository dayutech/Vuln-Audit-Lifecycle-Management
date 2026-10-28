package com.vulnaudit.security;

import java.util.Locale;

/**
 * 角色码与 Spring Security authority 的唯一转换入口。
 *
 * <p>约定：数据库 {@code roles.code} 为小写蛇形（admin / project_leader / team_leader /
 * vulnerability_engineer），对外 authority 统一为 {@code ROLE_} + 大写，例如 {@code ROLE_ADMIN}。
 *
 * <p>这样 {@code hasRole('ADMIN')}、{@code hasAnyRole('ADMIN','PROJECT_LEADER')} 与
 * {@code authentication.getAuthorities()} 的手工判断才能得到一致结果。
 */
public final class RoleAuthority {

    /** authority 统一前缀 */
    public static final String PREFIX = "ROLE_";

    /** 无任何角色时的兜底 authority */
    public static final String USER = "ROLE_USER";

    public static final String ADMIN = "ROLE_ADMIN";
    public static final String PROJECT_LEADER = "ROLE_PROJECT_LEADER";
    public static final String TEAM_LEADER = "ROLE_TEAM_LEADER";
    public static final String VULNERABILITY_ENGINEER = "ROLE_VULNERABILITY_ENGINEER";

    private RoleAuthority() {
    }

    /**
     * 角色码转换为 authority。
     *
     * @param roleCode 数据库角色码，允许已带 ROLE_ 前缀或大小写混杂
     * @return 形如 ROLE_ADMIN 的 authority；入参为空时返回 null
     */
    public static String of(String roleCode) {
        if (roleCode == null || roleCode.isBlank()) {
            return null;
        }
        String normalized = roleCode.trim().toUpperCase(Locale.ROOT);
        return normalized.startsWith(PREFIX) ? normalized : PREFIX + normalized;
    }

    /**
     * 判断 authority 是否属于管理类角色（管理员 / 项目负责人 / 小组长）。
     *
     * @param authority 单个 authority
     * @return 是否具备管理权限
     */
    public static boolean isManager(String authority) {
        return ADMIN.equals(authority)
                || PROJECT_LEADER.equals(authority)
                || TEAM_LEADER.equals(authority);
    }
}
