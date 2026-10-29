package com.vulnaudit.controller;

import com.vulnaudit.dto.*;
import com.vulnaudit.entity.Permission;
import com.vulnaudit.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 权限管理控制器
 *
 * <p><b>双轨说明</b>：本控制器仍按角色准入（权限矩阵 2.11：查询对 admin/project_leader 开放，
 * 增删改仅 admin）。权限点本身是后续 ABAC 的基础——角色→权限点→API 的三段映射落地后，
 * 可把这里的 {@code hasRole} 换成 {@code hasAuthority('permission:read')} 而无需改动业务代码。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/permissions")
@RequiredArgsConstructor
@Tag(name = "权限管理", description = "权限CRUD接口")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('ADMIN','PROJECT_LEADER')")
public class PermissionController {

    private final PermissionService permissionService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "创建权限", description = "创建新权限")
    public ResponseEntity<ApiResponse<Permission>> createPermission(@Valid @RequestBody Permission permission) {
        log.info("创建权限: code={}", permission.getCode());
        
        Permission createdPermission = permissionService.createPermission(permission);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(createdPermission));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取权限详情", description = "根据ID获取权限详细信息")
    public ResponseEntity<ApiResponse<Permission>> getPermission(@PathVariable UUID id) {
        log.info("获取权限详情: id={}", id);
        
        return permissionService.findById(id)
                .map(permission -> ResponseEntity.ok(ApiResponse.success(permission)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(404, "权限不存在")));
    }

    @GetMapping
    @Operation(summary = "获取所有权限", description = "获取所有权限列表")
    public ResponseEntity<ApiResponse<List<Permission>>> listPermissions() {
        log.info("获取所有权限");
        
        List<Permission> permissions = permissionService.findAll();
        return ResponseEntity.ok(ApiResponse.success(permissions));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "更新权限", description = "更新权限信息")
    public ResponseEntity<ApiResponse<Permission>> updatePermission(
            @PathVariable UUID id,
            @Valid @RequestBody Permission permission) {
        
        log.info("更新权限: id={}", id);
        
        Permission updatedPermission = permissionService.updatePermission(id, permission);
        return ResponseEntity.ok(ApiResponse.success(updatedPermission));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "删除权限", description = "删除权限")
    public ResponseEntity<ApiResponse<Void>> deletePermission(@PathVariable UUID id) {
        log.info("删除权限: id={}", id);
        
        permissionService.deletePermission(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
