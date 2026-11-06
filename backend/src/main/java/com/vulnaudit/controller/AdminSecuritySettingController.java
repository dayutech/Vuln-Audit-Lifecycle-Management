package com.vulnaudit.controller;

import com.vulnaudit.dto.ApiResponse;
import com.vulnaudit.security.CurrentUserProvider;
import com.vulnaudit.service.TwoFactorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 安全相关系统设置（t125）：双因子强制开关
 *
 * <p>关闭时只影响「强制」判定，不修改任何用户已有的 2FA 状态。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/settings")
@RequiredArgsConstructor
@Tag(name = "系统设置-安全", description = "双因子强制开关")
public class AdminSecuritySettingController {

    private final TwoFactorService twoFactorService;
    private final CurrentUserProvider currentUserProvider;

    @PutMapping("/force-two-factor")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "设置是否强制全体用户开启双因子",
            description = "开启后未启用 2FA 的用户除 2FA 设置/登出/认证接口外的业务请求返回 403 TWO_FACTOR_SETUP_REQUIRED；写审计 UPDATE_FORCE_TWO_FACTOR")
    public ApiResponse<Boolean> forceTwoFactor(@Valid @RequestBody ForceTwoFactorRequest request) {
        UUID actorId = currentUserProvider.currentUserId().orElse(null);
        boolean enabled = twoFactorService.setForceTwoFactor(request.enabled(), actorId);
        return ApiResponse.success(enabled, enabled ? "已要求全体用户开启双因子" : "已取消双因子强制");
    }

    /** 强制开关请求体 */
    public record ForceTwoFactorRequest(@NotNull(message = "validation.twoFactor.enabled.required") Boolean enabled) {
    }
}
