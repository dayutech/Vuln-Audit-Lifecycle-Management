package com.vulnaudit.controller;

import com.vulnaudit.dto.ApiResponse;
import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.Environment;
import com.vulnaudit.entity.EnvironmentSnapshot;
import com.vulnaudit.service.EnvironmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 环境管理控制器
 * 
 * 注意：此模块为空实现，待后续需求明确后再实现
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/environments")
@RequiredArgsConstructor
@Tag(name = "环境管理", description = "环境管理接口（暂未实现）")
@SecurityRequirement(name = "bearerAuth")
public class EnvironmentController {

    private final EnvironmentService environmentService;

    @PostMapping
    @Operation(summary = "创建环境", description = "创建新环境")
    public ResponseEntity<ApiResponse<Environment>> createEnvironment(@RequestBody Environment environment) {
        log.info("创建环境: name={}", environment.getName());
        
        Environment createdEnvironment = environmentService.createEnvironment(environment);
        return ResponseEntity.ok(ApiResponse.success(createdEnvironment));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取环境详情", description = "根据ID获取环境详细信息")
    public ResponseEntity<ApiResponse<Environment>> getEnvironment(@PathVariable UUID id) {
        log.info("获取环境详情: id={}", id);
        
        return environmentService.findById(id)
                .map(env -> ResponseEntity.ok(ApiResponse.success(env)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(404, "环境不存在")));
    }

    @GetMapping
    @Operation(summary = "分页查询环境列表", description = "分页查询环境列表")
    public ResponseEntity<ApiResponse<PageResponse<Environment>>> listEnvironments(
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "搜索关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "资产ID") @RequestParam(required = false) UUID assetId) {
        
        log.info("分页查询环境: page={}, size={}, keyword={}, assetId={}", page, size, keyword, assetId);
        
        PageResponse<Environment> response = environmentService.findAll(page, size, keyword, assetId);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新环境", description = "更新环境信息")
    public ResponseEntity<ApiResponse<Environment>> updateEnvironment(
            @PathVariable UUID id,
            @RequestBody Environment environment) {
        
        log.info("更新环境: id={}", id);
        
        Environment updatedEnvironment = environmentService.updateEnvironment(id, environment);
        return ResponseEntity.ok(ApiResponse.success(updatedEnvironment));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除环境", description = "删除环境")
    public ResponseEntity<ApiResponse<Void>> deleteEnvironment(@PathVariable UUID id) {
        log.info("删除环境: id={}", id);
        
        environmentService.deleteEnvironment(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{id}/deploy")
    @Operation(summary = "部署环境", description = "部署指定环境")
    public ResponseEntity<ApiResponse<Void>> deployEnvironment(@PathVariable UUID id) {
        log.info("部署环境: id={}", id);
        environmentService.deployEnvironment(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{id}/start")
    @Operation(summary = "启动环境", description = "启动指定环境")
    public ResponseEntity<ApiResponse<Void>> startEnvironment(@PathVariable UUID id) {
        log.info("启动环境: id={}", id);
        
        environmentService.startEnvironment(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{id}/stop")
    @Operation(summary = "停止环境", description = "停止指定环境")
    public ResponseEntity<ApiResponse<Void>> stopEnvironment(@PathVariable UUID id) {
        log.info("停止环境: id={}", id);
        
        environmentService.stopEnvironment(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{id}/snapshots")
    @Operation(summary = "创建快照", description = "为环境创建快照")
    public ResponseEntity<ApiResponse<Void>> createSnapshot(
            @PathVariable UUID id,
            @Parameter(description = "快照名称") @RequestParam String name) {
        
        log.info("创建快照: environmentId={}, name={}", id, name);
        
        environmentService.createSnapshot(id, name);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{id}/snapshots")
    @Operation(summary = "获取快照列表", description = "获取环境的快照列表")
    public ResponseEntity<ApiResponse<java.util.List<EnvironmentSnapshot>>> getSnapshots(@PathVariable UUID id) {
        log.info("获取快照列表: environmentId={}", id);
        return ResponseEntity.ok(ApiResponse.success(environmentService.getSnapshots(id)));
    }

    @PostMapping("/{id}/snapshots/{snapshotId}/rollback")
    @Operation(summary = "回滚快照", description = "将环境回滚到指定快照")
    public ResponseEntity<ApiResponse<Void>> rollbackSnapshot(
            @PathVariable UUID id,
            @PathVariable UUID snapshotId) {
        log.info("回滚快照: environmentId={}, snapshotId={}", id, snapshotId);
        environmentService.rollbackSnapshot(id, snapshotId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
