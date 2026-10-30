package com.vulnaudit.controller;

import com.vulnaudit.dto.ApiResponse;
import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.Environment;
import com.vulnaudit.entity.EnvironmentSnapshot;
import com.vulnaudit.service.EnvironmentService;
import com.vulnaudit.service.UserService;
import com.vulnaudit.service.env.EnvironmentProvisionerInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 环境管理控制器
 *
 * <p>部署/启停会真实调用容器编排；宿主机未启用容器编排时接口返回 503 且
 * {@code environment.status=failed}，失败原因见 {@code failReason}。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/environments")
@RequiredArgsConstructor
@Tag(name = "环境管理", description = "环境 CRUD、部署/启停、快照接口")
@SecurityRequirement(name = "bearerAuth")
public class EnvironmentController {

    private final EnvironmentService environmentService;
    private final UserService userService;
    private final com.vulnaudit.security.CurrentUserProvider currentUserProvider;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_LEADER','TEAM_LEADER')")
    @Operation(summary = "创建环境", description = "创建新环境；createdBy 缺省时取当前登录用户")
    public ResponseEntity<ApiResponse<Environment>> createEnvironment(@RequestBody Environment environment) {
        log.info("创建环境: name={}", environment.getName());

        // environments.created_by 为 NOT NULL：未传时补当前用户，避免落库 500
        if (environment.getCreatedBy() == null) {
            UUID createdBy = currentUserProvider.currentUserId().orElse(null);
            if (createdBy == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(401, "未授权"));
            }
            environment.setCreatedBy(createdBy);
        }

        Environment createdEnvironment = environmentService.createEnvironment(environment);
        return ResponseEntity.ok(ApiResponse.success(createdEnvironment));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取环境详情", description = "返回环境详情，并附带当前部署器类型与可用性")
    public ResponseEntity<ApiResponse<Environment>> getEnvironment(@PathVariable UUID id) {
        log.info("获取环境详情: id={}", id);

        return environmentService.findById(id)
                .map(environment -> {
                    applyProvisionerInfo(environment);
                    return ResponseEntity.ok(ApiResponse.success(environment));
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(404, "环境不存在")));
    }

    /**
     * 填充部署器信息（transient 字段，仅用于接口展示）
     */
    private void applyProvisionerInfo(Environment environment) {
        EnvironmentProvisionerInfo info = environmentService.provisionerInfo();
        environment.setProvisioner(info.type());
        environment.setProvisionerAvailable(info.available());
        environment.setProvisionerMessage(info.message());
    }

    @GetMapping
    @Operation(summary = "分页查询环境列表", description = "分页查询环境列表")
    public ResponseEntity<ApiResponse<PageResponse<Environment>>> listEnvironments(
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "搜索关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "资产ID") @RequestParam(required = false) UUID assetId,
            @Parameter(description = "资产版本ID") @RequestParam(required = false) UUID assetVersionId) {
        
        log.info("分页查询环境: page={}, size={}, keyword={}, assetId={}, assetVersionId={}", page, size, keyword, assetId, assetVersionId);
        
        PageResponse<Environment> response = environmentService.findAll(page, size, keyword, assetId, assetVersionId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_LEADER','TEAM_LEADER')")
    @Operation(summary = "更新环境", description = "更新环境信息")
    public ResponseEntity<ApiResponse<Environment>> updateEnvironment(
            @PathVariable UUID id,
            @RequestBody Environment environment) {
        
        log.info("更新环境: id={}", id);
        
        Environment updatedEnvironment = environmentService.updateEnvironment(id, environment);
        return ResponseEntity.ok(ApiResponse.success(updatedEnvironment));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_LEADER','TEAM_LEADER')")
    @Operation(summary = "删除环境", description = "删除环境")
    public ResponseEntity<ApiResponse<Void>> deleteEnvironment(@PathVariable UUID id) {
        log.info("删除环境: id={}", id);
        
        environmentService.deleteEnvironment(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{id}/deploy")
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_LEADER','TEAM_LEADER')")
    @Operation(summary = "部署环境", description = "部署指定环境")
    public ResponseEntity<ApiResponse<Void>> deployEnvironment(@PathVariable UUID id) {
        log.info("部署环境: id={}", id);
        environmentService.deployEnvironment(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{id}/start")
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_LEADER','TEAM_LEADER')")
    @Operation(summary = "启动环境", description = "启动指定环境")
    public ResponseEntity<ApiResponse<Void>> startEnvironment(@PathVariable UUID id) {
        log.info("启动环境: id={}", id);
        
        environmentService.startEnvironment(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{id}/stop")
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_LEADER','TEAM_LEADER')")
    @Operation(summary = "停止环境", description = "停止指定环境")
    public ResponseEntity<ApiResponse<Void>> stopEnvironment(@PathVariable UUID id) {
        log.info("停止环境: id={}", id);
        
        environmentService.stopEnvironment(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{id}/snapshots")
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_LEADER','TEAM_LEADER')")
    @Operation(summary = "创建快照", description = "为环境创建快照，created_by 记录真实当前用户")
    public ResponseEntity<ApiResponse<Void>> createSnapshot(
            @PathVariable UUID id,
            @Parameter(description = "快照名称") @RequestParam String name) {

        log.info("创建快照: environmentId={}, name={}", id, name);

        UUID operatorId = getCurrentUserId();
        if (operatorId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(401, "未授权"));
        }

        environmentService.createSnapshot(id, name, operatorId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{id}/snapshots")
    @Operation(summary = "获取快照列表", description = "获取环境的快照列表")
    public ResponseEntity<ApiResponse<java.util.List<EnvironmentSnapshot>>> getSnapshots(@PathVariable UUID id) {
        log.info("获取快照列表: environmentId={}", id);
        return ResponseEntity.ok(ApiResponse.success(environmentService.getSnapshots(id)));
    }

    @PostMapping("/{id}/snapshots/{snapshotId}/rollback")
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_LEADER','TEAM_LEADER')")
    @Operation(summary = "回滚快照", description = "将环境回滚到指定快照")
    public ResponseEntity<ApiResponse<Void>> rollbackSnapshot(
            @PathVariable UUID id,
            @PathVariable UUID snapshotId) {
        log.info("回滚快照: environmentId={}, snapshotId={}", id, snapshotId);
        environmentService.rollbackSnapshot(id, snapshotId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {
            return null;
        }
        return userService.findByUsername(authentication.getName())
                .map(user -> user.getId())
                .orElse(null);
    }
}
