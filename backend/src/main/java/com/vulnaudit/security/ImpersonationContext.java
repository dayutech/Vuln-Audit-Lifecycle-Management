package com.vulnaudit.security;

import java.util.UUID;

/**
 * 模拟视角上下文（t90）——线程级，由 {@link JwtAuthenticationFilter} 在解析到
 * {@code imp=true} 的 token 时写入，请求结束清理。
 *
 * <p>用途：
 * <ul>
 *   <li>{@link ImpersonationGuardFilter} 据此拦截「模拟态禁止的端点」；</li>
 *   <li>{@code AuditTrailInterceptor} 据此在业务审计里追加 {@code impersonatedBy=<actorId>}。</li>
 * </ul>
 *
 * <p>注意：这是<b>辅助标记</b>，鉴权权威仍是 Spring Security 的 authorities + 守卫过滤器；
 * 上下文为空即视为普通登录态（fail-closed 的方向是「不额外放行」，不是「额外放开」）。
 */
public final class ImpersonationContext {

    private static final ThreadLocal<Snapshot> CURRENT = new ThreadLocal<>();

    private ImpersonationContext() {
    }

    /**
     * 模拟态快照
     *
     * @param actorId       真实操作者（ADMIN）用户ID
     * @param actorUsername 真实操作者用户名
     * @param targetUsername 被模拟用户用户名（= token subject）
     */
    public record Snapshot(UUID actorId, String actorUsername, String targetUsername) {
    }

    public static void set(Snapshot snapshot) {
        CURRENT.set(snapshot);
    }

    public static Snapshot get() {
        return CURRENT.get();
    }

    /** 当前请求是否处于模拟态 */
    public static boolean isActive() {
        return CURRENT.get() != null;
    }

    /** 真实操作者ID（非模拟态返回 null） */
    public static UUID actorId() {
        Snapshot snapshot = CURRENT.get();
        return snapshot == null ? null : snapshot.actorId();
    }

    public static void clear() {
        CURRENT.remove();
    }
}
