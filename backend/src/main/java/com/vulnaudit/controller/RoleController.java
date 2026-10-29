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
 *
 * <p><b>双轨说明</b>：本控制器仍按角色准入（权限矩阵 2.11：admin/project_leader 可查询，
 * 增删改仅 admin）。角色与权限点的关联（{@code role_permissions}）是后续 ABAC 的基础数据，
 * 前端「角色权限分配」页据此渲染勾选状态。
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
    @Operation(summary = "创建角色", description = "创建新角色；可一并提交 permissionIds 完成权限分配")
    public ResponseEntity<ApiResponse<Role>> createRole(@Valid @RequestBody RoleUpsertRequest request) {
        log.info("创建角色: code={}, permissionCount={}",
                request.getCode(), request.getPermissionIds() == null ? 0 : request.getPermissionIds().size());

        if (request.getName() == null || request.getName().isBlank()
                || request.getCode() == null || request.getCode().isBlank()) {
            throw new com.vulnaudit.exception.BusinessException(com.vulnaudit.exception.ErrorCode.BAD_REQUEST);
        }

        Role createdRole = roleService.createRole(Role.builder()
                .name(request.getName())
                .code(request.getCode())
                .description(request.getDescription())
                .isSystem(false)
                .build());

        if (request.getPermissionIds() != null) {
            roleService.syncPermissions(createdRole.getId(), request.getPermissionIds());
        }

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

    /**
     * 获取角色列表
     *
     * @param includePermissions 是否附带每个角色的权限点（前端「角色权限分配」页回显勾选状态用），
     *                           兼容 {@code include_permissions} 命名
     */
    @GetMapping
    @Operation(summary = "获取所有角色", description = "获取所有角色列表，可选附带权限点")
    public ResponseEntity<ApiResponse<List<Role>>> listRoles(
            @RequestParam(required = false) Boolean includePermissions,
            @RequestParam(name = "include_permissions", required = false) Boolean includePermissionsSnake) {
        boolean withPermissions = Boolean.TRUE.equals(includePermissions)
                || Boolean.TRUE.equals(includePermissionsSnake);
        log.info("获取所有角色: includePermissions={}", withPermissions);

        List<Role> roles = roleService.findAll();
        if (withPermissions) {
            // 返回副本而不是直接改实体：避免瞬态字段残留在持久化上下文中串到后续请求
            roles = roles.stream().map(this::withPermissions).toList();
        }
        return ResponseEntity.ok(ApiResponse.success(roles));
    }

    /**
     * 复制角色并附带权限点（瞬态字段只用于响应，不写库）
     */
    private Role withPermissions(Role role) {
        return Role.builder()
                .id(role.getId())
                .name(role.getName())
                .code(role.getCode())
                .description(role.getDescription())
                .isSystem(role.getIsSystem())
                .createdAt(role.getCreatedAt())
                .updatedAt(role.getUpdatedAt())
                .permissions(roleService.getRolePermissions(role.getId()))
                .build();
    }

    @PreAuthorize("hasRole('ADMIN')")

    @PutMapping("/{id}")
    @Operation(summary = "更新角色", description = "更新角色信息；提交 permissionIds 时按目标集合覆盖式同步权限点")
    public ResponseEntity<ApiResponse<Role>> updateRole(
            @PathVariable UUID id,
            @Valid @RequestBody RoleUpsertRequest request) {
        
        log.info("更新角色: id={}, permissionCount={}",
                id, request.getPermissionIds() == null ? 0 : request.getPermissionIds().size());
        
        Role updatedRole = roleService.updateRole(id, Role.builder()
                .name(request.getName())
                .code(request.getCode())
                .description(request.getDescription())
                .build());

        if (request.getPermissionIds() != null) {
            roleService.syncPermissions(id, request.getPermissionIds());
            updatedRole = withPermissions(roleService.findById(id).orElse(updatedRole));
        }

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
    @Operation(summary = "分配权限给角色", description = "为角色分配权限；已分配的权限点自动跳过（幂等）")
    public ResponseEntity<ApiResponse<Void>> assignPermissions(
            @PathVariable UUID id,
            @Parameter(description = "权限ID列表") @RequestBody List<UUID> permissionIds) {
        
        log.info("分配权限给角色: roleId={}, permissionCount={}", id, permissionIds.size());
        
        roleService.assignPermissions(id, permissionIds);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 获取角色已分配的权限点
     *
     * <p>供前端「角色权限分配」页回显勾选状态；与 POST /{id}/permissions 成对使用。
     */
    @GetMapping("/{id}/permissions")
    @Operation(summary = "获取角色权限", description = "获取角色已分配的权限点列表")
    public ResponseEntity<ApiResponse<List<com.vulnaudit.entity.Permission>>> getRolePermissions(
            @PathVariable UUID id) {

        log.debug("获取角色权限: roleId={}", id);

        return ResponseEntity.ok(ApiResponse.success(roleService.getRolePermissions(id)));
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
