package com.vulnaudit.controller;

import com.vulnaudit.dto.ApiResponse;
import com.vulnaudit.dto.ApproveRegistrationResponse;
import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.dto.RegistrationRequestVO;
import com.vulnaudit.dto.RejectRegistrationRequest;
import com.vulnaudit.security.CurrentUserProvider;
import com.vulnaudit.service.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 注册申请审批（ADMIN / PROJECT_LEADER，t102）
 *
 * <p>权限：三个端点统一 {@code hasAnyRole('ADMIN','PROJECT_LEADER')}（与「用户管理」同权）。
 * 模拟态下 {@code /api/v1/admin/**} 的写方法会被 {@code ImpersonationGuardFilter} 一律 403（防借壳批号）。
 *
 * <p>审计：服务层写 {@code REGISTRATION_APPROVE} / {@code REGISTRATION_REJECT}（含 reviewer、申请ID、手机号脱敏），
 * 对应端点已加入 {@code AuditTrailInterceptor.SKIP_ENDPOINTS}，避免一次审批出现两条记录。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/registration-requests")
@RequiredArgsConstructor
@Tag(name = "注册申请审批", description = "查看/通过/拒绝自助注册申请")
@SecurityRequirement(name = "bearerAuth")
public class RegistrationRequestAdminController {

    private final RegistrationService registrationService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_LEADER')")
    @Operation(summary = "分页查询注册申请",
            description = "status 可选：PENDING（默认展示待审批）/ APPROVED / REJECTED；不传则全部")
    public ApiResponse<PageResponse<RegistrationRequestVO>> list(
            @Parameter(description = "状态过滤（PENDING/APPROVED/REJECTED）")
            @RequestParam(required = false) String status,
            @Parameter(description = "页码（从 0 开始）") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(registrationService.list(status, page, size));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_LEADER')")
    @Operation(summary = "审批通过",
            description = "把占位用户转为 active（手机/邮箱落库并标记 verified），申请置 APPROVED，写审计")
    public ApiResponse<ApproveRegistrationResponse> approve(@PathVariable UUID id) {
        UUID reviewerId = currentUserProvider.currentUserId().orElse(null);
        return ApiResponse.success(registrationService.approve(id, reviewerId), "审批通过");
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_LEADER')")
    @Operation(summary = "审批拒绝",
            description = "申请置 REJECTED 且该用户登录返回 403；note 必填并写入 review_note 与审计")
    public ApiResponse<RegistrationRequestVO> reject(@PathVariable UUID id,
                                                     @Valid @RequestBody RejectRegistrationRequest request) {
        UUID reviewerId = currentUserProvider.currentUserId().orElse(null);
        return ApiResponse.success(registrationService.reject(id, reviewerId, request.note()), "已拒绝");
    }
}
