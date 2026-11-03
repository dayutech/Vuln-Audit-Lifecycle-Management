package com.vulnaudit.service;

import com.vulnaudit.dto.ImpersonateRequest;
import com.vulnaudit.dto.ImpersonateResponse;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.UUID;

/**
 * 管理员模拟视角服务（t90）
 *
 * <p><b>安全约束</b>（逐条实现在实现类与 {@code ImpersonationGuardFilter}）：
 * <ul>
 *   <li>只能由 ADMIN 发起；</li>
 *   <li>不得模拟 ADMIN（409）；目标必须存在（404）且 status=active（400）；</li>
 *   <li>模拟 token 有效期默认 2 小时（{@code app.security.impersonation.ttl-seconds}）；</li>
 *   <li>模拟态禁止写 {@code /users}、{@code /roles}、{@code /admin}（除 stop），由守卫过滤器统一拦截；</li>
 *   <li>START / STOP 均写审计，业务审计附带 {@code impersonatedBy}。</li>
 * </ul>
 */
public interface ImpersonationService {

    /**
     * 发起模拟
     *
     * @param actorId 真实操作者（ADMIN）用户ID
     * @param request 请求（目标用户 + 原因）
     * @return 受限 token 与双方信息
     */
    @Nonnull
    ImpersonateResponse impersonate(@Nullable UUID actorId, @Nonnull ImpersonateRequest request);

    /**
     * 停止模拟（写审计；前端换回原 token，本次请求 token 本身无需失效）
     *
     * @param actorId        真实操作者ID（来自模拟 token 的 act）
     * @param targetUsername 被模拟用户名（来自 token subject）
     * @return 被模拟用户ID（用于审计 resourceId）
     */
    @Nullable
    UUID stop(@Nullable UUID actorId, @Nullable String targetUsername);
}
