package com.vulnaudit.controller;

import com.vulnaudit.dto.ApiResponse;
import com.vulnaudit.entity.ApprovalScope;
import com.vulnaudit.entity.User;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.ApprovalScopeRepository;
import com.vulnaudit.repository.UserRepository;
import com.vulnaudit.service.ApprovalRoutingService;
import com.vulnaudit.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 审批范围管理。
 *
 * <p>SRS 3.7.2/3.7.3 要求「审批权限由上级领导授予」，审批范围记录在
 * {@code approval_scopes} 表中。此前该表只有实体与仓储、没有任何接口，
 * 导致团队负责人永远无法被授予审批范围。本控制器补齐授予/查询/撤销能力。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/approval-scopes")
@RequiredArgsConstructor
@Tag(name = "审批范围", description = "审批人授权范围管理（team / asset / asset_category）")
@SecurityRequirement(name = "bearerAuth")
public class ApprovalScopeController {

    private final ApprovalScopeRepository approvalScopeRepository;
    private final ApprovalRoutingService approvalRoutingService;
    private final UserService userService;
    private final UserRepository userRepository;

    @GetMapping
    @Operation(summary = "查询审批范围", description = "管理员/项目负责人查看全部；其他人只看授予自己的")
    public ResponseEntity<ApiResponse<List<ApprovalScope>>> listScopes(
            @Parameter(description = "按审批人过滤") @RequestParam(required = false) UUID approverId) {
        UUID currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(401, "未授权"));
        }
        List<ApprovalScope> scopes;
        if (approvalRoutingService.isGlobalApprover(currentUserId)) {
            scopes = approverId != null
                    ? approvalScopeRepository.findByApproverId(approverId)
                    : approvalScopeRepository.findAll();
        } else {
            scopes = approvalScopeRepository.findByApproverId(currentUserId);
        }
        return ResponseEntity.ok(ApiResponse.success(scopes));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_LEADER')")
    @Operation(summary = "授予审批范围", description = "上级领导为审批人授予 team/asset/asset_category 范围")
    public ResponseEntity<ApiResponse<ApprovalScope>> grantScope(@RequestBody ApprovalScope request) {
        log.info("授予审批范围: approverId={}, type={}, scopeId={}",
                request.getApproverId(), request.getScopeType(), request.getScopeId());

        if (request.getApproverId() == null || request.getScopeType() == null || request.getScopeId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "approverId、scopeType、scopeId 均不能为空");
        }
        String type = request.getScopeType().trim().toLowerCase();
        if (!ApprovalRoutingService.SCOPE_TEAM.equals(type)
                && !ApprovalRoutingService.SCOPE_ASSET.equals(type)
                && !ApprovalRoutingService.SCOPE_ASSET_CATEGORY.equals(type)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "scopeType 仅支持 team / asset / asset_category");
        }
        if (!userRepository.existsById(request.getApproverId())) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        UUID operatorId = getCurrentUserId();
        ApprovalScope scope = ApprovalScope.builder()
                .approverId(request.getApproverId())
                .scopeType(type)
                .scopeId(request.getScopeId())
                .grantedBy(operatorId)
                .expiresAt(request.getExpiresAt())
                .build();
        ApprovalScope saved = approvalScopeRepository.save(scope);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(saved));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_LEADER')")
    @Operation(summary = "撤销审批范围", description = "撤销某条审批范围授权")
    public ResponseEntity<ApiResponse<Void>> revokeScope(@PathVariable UUID id) {
        log.info("撤销审批范围: id={}", id);
        if (!approvalScopeRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(404, "审批范围不存在"));
        }
        approvalScopeRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/my")
    @Operation(summary = "我的审批范围", description = "当前登录用户被授予的审批范围")
    public ResponseEntity<ApiResponse<List<ApprovalScope>>> myScopes() {
        UUID currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(401, "未授权"));
        }
        return ResponseEntity.ok(ApiResponse.success(approvalScopeRepository.findByApproverId(currentUserId)));
    }

    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {
            return null;
        }
        Optional<User> user = userService.findByUsername(authentication.getName());
        return user.map(User::getId).orElse(null);
    }
}
