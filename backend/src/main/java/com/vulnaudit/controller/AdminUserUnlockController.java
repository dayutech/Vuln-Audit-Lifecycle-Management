package com.vulnaudit.controller;

import com.vulnaudit.dto.ApiResponse;
import com.vulnaudit.service.LoginLockoutService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 管理员解锁被锁定账户（t123）
 *
 * <p>用于「用户没有绑定任何已验证手机/邮箱」的场景：此时 OTP 走不通，只能由管理员解锁。
 * 解锁会清零失败计数与来源 IP 记录，并写 {@code ACCOUNT_UNLOCKED} 审计（端点已加入
 * {@code AuditTrailInterceptor.SKIP_ENDPOINTS}，一次解锁只留 1 条带语义的审计）。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@Tag(name = "用户管理-账户解锁", description = "管理员解锁因暴力破解被锁定的账户")
public class AdminUserUnlockController {

    private final LoginLockoutService loginLockoutService;
    private final com.vulnaudit.security.CurrentUserProvider currentUserProvider;

    @PostMapping("/{id}/unlock")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "解锁账户（管理员）",
            description = "清除该账户的失败计数与锁定状态，写审计 ACCOUNT_UNLOCKED；非 ADMIN 返回 403")
    public ApiResponse<Void> unlock(@PathVariable UUID id) {
        UUID operatorId = currentUserProvider.currentUserId().orElse(null);
        loginLockoutService.unlockByAdmin(id, operatorId);
        return ApiResponse.success(null, "账户已解锁");
    }
}
