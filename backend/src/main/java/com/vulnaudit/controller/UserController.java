package com.vulnaudit.controller;

import com.vulnaudit.dto.ApiResponse;
import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.dto.UserCreateRequest;
import com.vulnaudit.dto.UserUpdateRequest;
import com.vulnaudit.entity.Permission;
import com.vulnaudit.entity.Role;
import com.vulnaudit.entity.User;
import com.vulnaudit.entity.UserPermission;
import com.vulnaudit.entity.UserRole;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.UserPermissionRepository;
import com.vulnaudit.repository.UserRoleRepository;
import com.vulnaudit.service.PermissionService;
import com.vulnaudit.service.RoleService;
import com.vulnaudit.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 用户控制器
 * 
 * 功能说明：
 * - 处理用户管理相关请求
 * - 提供用户CRUD接口
 * - 提供用户状态管理接口
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Tag(name = "用户管理", description = "用户增删改查、状态管理等接口")
@SecurityRequirement(name = "bearerAuth")
public class UserController {

    private final UserService userService;
    private final RoleService roleService;
    private final PermissionService permissionService;
    private final UserRoleRepository userRoleRepository;
    private final UserPermissionRepository userPermissionRepository;

    /**
     * 创建用户
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "创建用户", description = "管理员创建新用户账号")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "创建成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数错误"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "无权限"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "用户名或邮箱已存在")
    })
    public ApiResponse<User> createUser(
            @Parameter(description = "用户创建请求", required = true)
            @Valid @RequestBody UserCreateRequest request) {
        log.info("创建用户请求: username={}", request.getUsername());
        
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .phone(request.getPhone())
                .passwordHash(request.getPassword())
                .fullName(request.getFullName())
                .avatar(request.getAvatar())
                .position(request.getPosition())
                .build();
        
        User createdUser = userService.createUser(user);
        createdUser.setPasswordHash(null);
        
        log.info("用户创建成功: id={}", createdUser.getId());
        return ApiResponse.success(createdUser);
    }

    /**
     * 根据ID查询用户
     */
    @GetMapping("/{id}")
    @Operation(summary = "查询用户", description = "根据ID查询用户详细信息")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "用户不存在")
    })
    public ApiResponse<User> getUserById(
            @Parameter(description = "用户ID", required = true) @PathVariable UUID id) {
        log.debug("查询用户: id={}", id);
        
        User user = userService.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        
        user.setPasswordHash(null);
        return ApiResponse.success(user);
    }

    /**
     * 分页查询用户列表
     */
    @GetMapping
    @Operation(summary = "查询用户列表", description = "分页查询用户列表，支持关键词搜索和状态筛选")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权")
    })
    public ApiResponse<PageResponse<User>> getUserList(
            @Parameter(description = "页码（从0开始）") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "搜索关键词（用户名/邮箱/姓名）") @RequestParam(required = false) String keyword,
            @Parameter(description = "用户状态（active/suspended/deactivated）") @RequestParam(required = false) String status) {
        log.debug("查询用户列表: page={}, size={}, keyword={}, status={}", page, size, keyword, status);
        
        PageResponse<User> userPage = userService.findAll(page, size, keyword, status);
        userPage.getContent().forEach(user -> user.setPasswordHash(null));
        
        return ApiResponse.success(userPage);
    }

    /**
     * 更新用户信息
     */
    @PutMapping("/{id}")
    @Operation(summary = "更新用户", description = "更新用户信息（只能更新自己的信息，或管理员可更新任何人）")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "更新成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "参数错误"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "无权限"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "用户不存在")
    })
    public ApiResponse<User> updateUser(
            @Parameter(description = "用户ID", required = true) @PathVariable UUID id,
            @Parameter(description = "用户更新请求", required = true) @Valid @RequestBody UserUpdateRequest request) {
        log.info("更新用户请求: id={}", id);
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentUsername = authentication.getName();
        User currentUser = userService.findByUsername(currentUsername)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        
        if (!currentUser.getId().equals(id) && !isAdmin) {
            log.warn("无权限更新用户: currentUserId={}, targetUserId={}", currentUser.getId(), id);
            return ApiResponse.error("无权限更新该用户");
        }
        
        User user = User.builder()
                .email(request.getEmail())
                .phone(request.getPhone())
                .fullName(request.getFullName())
                .avatar(request.getAvatar())
                .position(request.getPosition())
                .build();
        
        User updatedUser = userService.updateUser(id, user);
        updatedUser.setPasswordHash(null);
        
        log.info("用户更新成功: id={}", updatedUser.getId());
        return ApiResponse.success(updatedUser);
    }

    /**
     * 更新用户状态
     */
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "更新用户状态", description = "管理员更新用户状态（active/suspended/deactivated）")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "更新成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "无效的状态值"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "无权限"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "用户不存在")
    })
    public ApiResponse<User> updateUserStatus(
            @Parameter(description = "用户ID", required = true) @PathVariable UUID id,
            @Parameter(description = "新状态（active/suspended/deactivated）", required = true) @RequestParam String status) {
        log.info("更新用户状态请求: id={}, status={}", id, status);
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentUsername = authentication.getName();
        User currentUser = userService.findByUsername(currentUsername)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        
        User updatedUser = userService.updateStatus(id, status, currentUser.getId());
        updatedUser.setPasswordHash(null);
        
        log.info("用户状态更新成功: id={}, status={}", updatedUser.getId(), status);
        return ApiResponse.success(updatedUser);
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "删除用户", description = "管理员删除用户（软删除，设置状态为deactivated）")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "删除成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "不能删除自己"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "无权限"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "用户不存在")
    })
    public ApiResponse<Void> deleteUser(
            @Parameter(description = "用户ID", required = true) @PathVariable UUID id) {
        log.info("删除用户请求: id={}", id);
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentUsername = authentication.getName();
        User currentUser = userService.findByUsername(currentUsername)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        
        if (currentUser.getId().equals(id)) {
            log.warn("不能删除自己: userId={}", id);
            return ApiResponse.error("不能删除自己");
        }
        
        userService.deleteUser(id, currentUser.getId());
        
        log.info("用户删除成功: id={}", id);
        return ApiResponse.success(null);
    }

    /**
     * 重置用户密码
     */
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "重置用户密码", description = "管理员重置用户密码")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "重置成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "无权限"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "用户不存在")
    })
    public ApiResponse<Void> resetPassword(
            @Parameter(description = "用户ID", required = true) @PathVariable UUID id,
            @Parameter(description = "新密码", required = true) @RequestParam String newPassword) {
        log.info("重置用户密码请求: id={}", id);
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentUsername = authentication.getName();
        User currentUser = userService.findByUsername(currentUsername)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        
        userService.resetPassword(id, newPassword, currentUser.getId());
        
        log.info("用户密码重置成功: id={}", id);
        return ApiResponse.success(null);
    }

    /**
     * 检查用户名是否存在
     */
    @GetMapping("/check-username")
    @Operation(summary = "检查用户名是否存在", description = "检查用户名是否已被使用")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "检查成功")
    })
    public ApiResponse<Boolean> checkUsername(
            @Parameter(description = "用户名", required = true) @RequestParam String username) {
        log.debug("检查用户名是否存在: username={}", username);
        
        boolean exists = userService.existsByUsername(username);
        return ApiResponse.success(exists);
    }

    /**
     * 检查邮箱是否存在
     */
    @GetMapping("/check-email")
    @Operation(summary = "检查邮箱是否存在", description = "检查邮箱是否已被使用")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "检查成功")
    })
    public ApiResponse<Boolean> checkEmail(
            @Parameter(description = "邮箱", required = true) @RequestParam String email) {
        log.debug("检查邮箱是否存在: email={}", email);
        
        boolean exists = userService.existsByEmail(email);
        return ApiResponse.success(exists);
    }

    // ==================== 用户角色管理 ====================

    /**
     * 获取用户的角色列表
     */
    @GetMapping("/{id}/roles")
    @Operation(summary = "获取用户角色", description = "获取指定用户被分配的所有角色")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "用户不存在")
    })
    public ApiResponse<List<Role>> getUserRoles(
            @Parameter(description = "用户ID", required = true) @PathVariable UUID id) {
        log.debug("获取用户角色列表: userId={}", id);
        
        List<UserRole> userRoles = userRoleRepository.findByUserId(id);
        List<UUID> roleIds = userRoles.stream()
                .map(UserRole::getRoleId)
                .toList();
        
        List<Role> roles = roleIds.stream()
                .map(roleId -> roleService.findById(roleId))
                .filter(opt -> opt.isPresent())
                .map(opt -> opt.get())
                .collect(Collectors.toList());
        
        return ApiResponse.success(roles);
    }

    /**
     * 为用户分配角色
     */
    @PostMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "分配角色给用户", description = "管理员为用户分配角色，支持一个用户多个角色")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "分配成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "角色不存在"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "无权限"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "用户不存在")
    })
    public ApiResponse<UserRole> assignRoleToUser(
            @Parameter(description = "用户ID", required = true) @PathVariable UUID id,
            @Parameter(description = "角色ID", required = true) @RequestParam UUID roleId) {
        log.info("为用户分配角色: userId={}, roleId={}", id, roleId);
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentUsername = authentication.getName();
        User currentUser = userService.findByUsername(currentUsername)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        
        Role role = roleService.findById(roleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROLE_NOT_FOUND));
        
        if (userRoleRepository.existsByUserIdAndRoleId(id, roleId)) {
            return ApiResponse.error("用户已拥有该角色");
        }
        
        UserRole userRole = UserRole.builder()
                .userId(id)
                .roleId(roleId)
                .assignedBy(currentUser.getId())
                .assignedAt(LocalDateTime.now())
                .build();
        
        UserRole savedUserRole = userRoleRepository.save(userRole);
        log.info("角色分配成功: userId={}, roleId={}", id, roleId);
        
        return ApiResponse.success(savedUserRole);
    }

    /**
     * 撤销用户角色
     */
    @DeleteMapping("/{id}/roles/{roleId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "撤销用户角色", description = "管理员撤销用户的某个角色")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "撤销成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "无权限"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "用户角色关联不存在")
    })
    public ApiResponse<Void> revokeUserRole(
            @Parameter(description = "用户ID", required = true) @PathVariable UUID id,
            @Parameter(description = "角色ID", required = true) @PathVariable UUID roleId) {
        log.info("撤销用户角色: userId={}, roleId={}", id, roleId);
        
        UserRole userRole = userRoleRepository.findByUserIdAndRoleId(id, roleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_ROLE_EXISTS));
        
        userRoleRepository.delete(userRole);
        log.info("角色撤销成功: userId={}, roleId={}", id, roleId);
        
        return ApiResponse.success(null);
    }

    // ==================== 用户权限管理 ====================

    /**
     * 直接分配权限给用户
     */
    @PostMapping("/{id}/permissions")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "直接分配权限给用户", description = "管理员直接分配特定权限给用户（可设置过期时间）")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "分配成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "权限不存在"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "无权限"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "用户不存在")
    })
    public ApiResponse<UserPermission> assignPermissionToUser(
            @Parameter(description = "用户ID", required = true) @PathVariable UUID id,
            @Parameter(description = "权限ID", required = true) @RequestParam UUID permissionId,
            @Parameter(description = "过期时间（可选）") @RequestParam(required = false) LocalDateTime expiresAt) {
        log.info("为用户分配权限: userId={}, permissionId={}, expiresAt={}", id, permissionId, expiresAt);
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentUsername = authentication.getName();
        User currentUser = userService.findByUsername(currentUsername)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        
        Permission permission = permissionService.findById(permissionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PERMISSION_NOT_FOUND));
        
        if (userPermissionRepository.existsByUserIdAndPermissionId(id, permissionId)) {
            return ApiResponse.error("用户已拥有该权限");
        }
        
        UserPermission userPermission = UserPermission.builder()
                .userId(id)
                .permissionId(permissionId)
                .grantedBy(currentUser.getId())
                .grantedAt(LocalDateTime.now())
                .expiresAt(expiresAt)
                .build();
        
        UserPermission savedUserPermission = userPermissionRepository.save(userPermission);
        log.info("权限分配成功: userId={}, permissionId={}", id, permissionId);
        
        return ApiResponse.success(savedUserPermission);
    }

    /**
     * 撤销用户权限
     */
    @DeleteMapping("/{id}/permissions/{permissionId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "撤销用户权限", description = "管理员撤销直接分配给用户的权限")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "撤销成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "无权限"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "用户权限关联不存在")
    })
    public ApiResponse<Void> revokeUserPermission(
            @Parameter(description = "用户ID", required = true) @PathVariable UUID id,
            @Parameter(description = "权限ID", required = true) @PathVariable UUID permissionId) {
        log.info("撤销用户权限: userId={}, permissionId={}", id, permissionId);
        
        UserPermission userPermission = userPermissionRepository.findByUserIdAndPermissionId(id, permissionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_PERMISSION_EXISTS));
        
        userPermissionRepository.delete(userPermission);
        log.info("权限撤销成功: userId={}, permissionId={}", id, permissionId);
        
        return ApiResponse.success(null);
    }

    /**
     * 获取当前用户权限
     */
    @GetMapping("/me/permissions")
    @Operation(summary = "获取当前用户权限", description = "获取当前登录用户的权限列表（角色权限+直接分配权限的并集）")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权")
    })
    public ApiResponse<Map<String, Object>> getCurrentUserPermissions() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            log.warn("未找到认证信息");
            return ApiResponse.error("未授权");
        }
        
        String username = authentication.getName();
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        
        List<String> roleCodes = userService.getUserRoleCodes(user.getId());
        List<String> permissionCodes = userService.getUserPermissionCodes(user.getId());
        
        Map<String, Object> result = Map.of(
                "userId", user.getId(),
                "username", user.getUsername(),
                "roles", roleCodes,
                "permissions", permissionCodes
        );
        
        return ApiResponse.success(result);
    }

}
