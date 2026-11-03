package com.vulnaudit.controller;

import com.vulnaudit.dto.ApiResponse;
import com.vulnaudit.dto.ImpersonateRequest;
import com.vulnaudit.dto.ImpersonateResponse;
import com.vulnaudit.security.CurrentUserProvider;
import com.vulnaudit.security.ImpersonationContext;
import com.vulnaudit.service.ImpersonationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员模拟视角（t90）
 *
 * <p>端点与权限：
 * <ul>
 *   <li>{@code POST /admin/impersonate}：仅 ADMIN；模拟态 token 一律 403（{@code ImpersonationGuardFilter}
 *       拦截 {@code /api/v1/admin/**} 的写方法 → 禁止链式模拟）；</li>
 *   <li>{@code POST /admin/impersonate/stop}：普通态要求 ADMIN；模拟态也允许（否则无法退出），
 *       但要求 token 的 {@code act}（真实操作者）必须是 ADMIN（服务层校验）。</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "管理员模拟视角", description = "以指定用户身份与权限查看系统（安全受限）")
@SecurityRequirement(name = "bearerAuth")
public class AdminImpersonationController {

    private final ImpersonationService impersonationService;
    private final CurrentUserProvider currentUserProvider;

    @PostMapping("/impersonate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "发起模拟",
            description = "仅 ADMIN；目标必须存在、status=active 且不是 ADMIN（409）。"
                    + "返回受限 token（含 act/imp claim，默认 2 小时），并写 IMPERSONATE_START 审计")
    public ResponseEntity<ApiResponse<ImpersonateResponse>> impersonate(
            @Valid @RequestBody ImpersonateRequest request) {

        var actorId = currentUserProvider.currentUserId().orElse(null);
        ImpersonateResponse response = impersonationService.impersonate(actorId, request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/impersonate/stop")
    @Operation(summary = "停止模拟",
            description = "普通态要求 ADMIN；模拟态允许调用（token 的 act 必须是 ADMIN）。"
                    + "写 IMPERSONATE_STOP 审计，返回 204")
    public ResponseEntity<Void> stopImpersonation() {
        ImpersonationContext.Snapshot snapshot = ImpersonationContext.get();
        var actorId = snapshot != null ? snapshot.actorId() : currentUserProvider.currentUserId().orElse(null);
        String targetUsername = snapshot != null ? snapshot.targetUsername()
                : currentUserProvider.currentUsername().orElse(null);

        impersonationService.stop(actorId, targetUsername);
        return ResponseEntity.noContent().build();
    }
}
