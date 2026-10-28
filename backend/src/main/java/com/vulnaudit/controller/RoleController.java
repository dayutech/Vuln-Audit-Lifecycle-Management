package com.vulnaudit.controller;

import com.vulnaudit.dto.*;
import com.vulnaudit.entity.Role;
import com.vulnaudit.service.RoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 角色管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
@Tag(name = "角色管理", description = "角色CRUD、权限分配等接口")
@PreAuthorize("hasAnyRole('ADMIN','PROJECT_LEADER')")
@SecurityRequirement(name = "bearerAuth")
public class RoleController {

    private final RoleService roleService;

    @PreAuthorize("hasRole('ADMIN')")

    @PostMapping
    @Operation(summary = "创建角色", description = "创建新角色")
    public ResponseEntity<ApiResponse<Role>> createRole(@Valid @RequestBody Role role) {
        log.info("创建角色: code={}", role.getCode());
        
        Role createdRole = roleService.createRole(role);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(createdRole));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取角色详情", description = "根据ID获取角色详细信息")
    public ResponseEntity<ApiResponse<Role>> getRole(@PathVariable UUID id) {
        log.info("获取角色详情: id={}", id);
        
        return roleService.findById(id)
                .map(role -> ResponseEntity.ok(ApiResponse.success(role)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(404, "角色不存在")));
    }

    @GetMapping
    @Operation(summary = "获取所有角色", description = "获取所有角色列表")
    public ResponseEntity<ApiResponse<List<Role>>> listRoles() {
        log.info("获取所有角色");
        
        List<Role> roles = roleService.findAll();
        return ResponseEntity.ok(ApiResponse.success(roles));
    }

    @PreAuthorize("hasRole('ADMIN')")

    @PutMapping("/{id}")
    @Operation(summary = "更新角色", description = "更新角色信息")
    public ResponseEntity<ApiResponse<Role>> updateRole(
            @PathVariable UUID id,
            @Valid @RequestBody Role role) {
        
        log.info("更新角色: id={}", id);
        
        Role updatedRole = roleService.updateRole(id, role);
        return ResponseEntity.ok(ApiResponse.success(updatedRole));
    }

    @PreAuthorize("hasRole('ADMIN')")

    @DeleteMapping("/{id}")
    @Operation(summary = "删除角色", description = "删除角色")
    public ResponseEntity<ApiResponse<Void>> deleteRole(@PathVariable UUID id) {
        log.info("删除角色: id={}", id);
        
        roleService.deleteRole(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PreAuthorize("hasRole('ADMIN')")

    @PostMapping("/{id}/permissions")
    @Operation(summary = "分配权限给角色", description = "为角色分配权限")
    public ResponseEntity<ApiResponse<Void>> assignPermissions(
            @PathVariable UUID id,
            @Parameter(description = "权限ID列表") @RequestBody List<UUID> permissionIds) {
        
        log.info("分配权限给角色: roleId={}, permissionCount={}", id, permissionIds.size());
        
        roleService.assignPermissions(id, permissionIds);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PreAuthorize("hasRole('ADMIN')")

    @DeleteMapping("/{id}/permissions")
    @Operation(summary = "移除角色权限", description = "移除角色的权限")
    public ResponseEntity<ApiResponse<Void>> removePermissions(
            @PathVariable UUID id,
            @Parameter(description = "权限ID列表") @RequestBody List<UUID> permissionIds) {
        
        log.info("移除角色权限: roleId={}, permissionCount={}", id, permissionIds.size());
        
        roleService.removePermissions(id, permissionIds);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PreAuthorize("hasRole('ADMIN')")

    @PostMapping("/{id}/users/{userId}")
    @Operation(summary = "分配角色给用户", description = "为用户分配角色")
    public ResponseEntity<ApiResponse<Void>> assignRoleToUser(
            @PathVariable UUID id,
            @PathVariable UUID userId) {
        
        log.info("分配角色给用户: roleId={}, userId={}", id, userId);
        
        roleService.assignRoleToUser(userId, id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PreAuthorize("hasRole('ADMIN')")

    @DeleteMapping("/{id}/users/{userId}")
    @Operation(summary = "移除用户角色", description = "移除用户的角色")
    public ResponseEntity<ApiResponse<Void>> removeRoleFromUser(
            @PathVariable UUID id,
            @PathVariable UUID userId) {
        
        log.info("移除用户角色: roleId={}, userId={}", id, userId);
        
        roleService.removeRoleFromUser(userId, id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
