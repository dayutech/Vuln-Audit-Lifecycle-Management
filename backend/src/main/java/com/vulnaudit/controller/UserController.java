package com.vulnaudit.controller;

import com.vulnaudit.dto.ApiResponse;
import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.dto.UserCreateRequest;
import com.vulnaudit.dto.UserUpdateRequest;
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
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.annotation.Nullable;

/**
 * 用户控制器
 * 
 * 功能说明：
 * - 处理用户管理相关请求
 * - 提供用户CRUD接口
 * - 提供用户状态管理接口
 * - 提供用户角色/权限分配接口与「我的权限」查询
 *
 * <p><b>双轨说明</b>：接口准入仍由 {@code @PreAuthorize("hasRole(...)")} 做角色校验（语义未变）；
 * 角色与用户单独授权共同产出的权限码集合（{@code /users/me/permissions}）是后续 ABAC 落地的
 * 基础数据，用于按「资源:操作」而非角色名做细粒度判定。
 *
 * <p><b>密码字段约定</b>：响应里「不返回密码哈希」由实体上的
 * {@code @JsonIgnore}（见 {@link User#getPasswordHash()}）保证，<b>不再通过把受管实体的
 * {@code passwordHash} 置 null 来表达</b>——那种写法会污染持久化上下文，在单事务/长会话下把
 * NULL 刷进 {@code users.password_hash}（NOT NULL）导致 500。改密码统一走
 * {@code POST /auth/change-password}（自助）与 {@code POST /users/{id}/reset-password}（管理员），
 * 两者都经 {@code PasswordEncoder} 加密。
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
    public ResponseEntity<ApiResponse<User>> createUser(
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
        
        log.info("用户创建成功: id={}", createdUser.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(createdUser));
    }

    /**
     * 根据ID查询用户
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_LEADER')")
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
        
        return ApiResponse.success(user);
    }

    /**
     * 用户目录（最小暴露，供全员使用）
     *
     * <p>与 {@code GET /users} 的分工：本端点<b>对所有登录用户开放</b>，但只返回
     * {@code id / username / fullName}（专用 DTO {@link com.vulnaudit.dto.UserDirectoryEntry}），
     * 不含 email、phone、状态、角色、最后登录时间；供任务负责人下拉、审批人展示、知识库作者名等场景。
     * 用户管理数据（列表/详情/角色/存在性检查）仍限 admin / project_leader。
     */
    @GetMapping("/directory")
    @Operation(summary = "用户目录（最小暴露）",
            description = "返回 id/username/fullName，供任意登录用户做下拉与展示；不含 email/phone/角色等 PII")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未认证")
    })
    public ApiResponse<List<com.vulnaudit.dto.UserDirectoryEntry>> getUserDirectory(
            @Parameter(description = "关键词（匹配用户名/姓名）") @RequestParam(required = false) String keyword,
            @Parameter(description = "返回条数上限（默认 200，最大 500）") @RequestParam(defaultValue = "0") int limit) {
        log.debug("查询用户目录: keyword={}, limit={}", keyword, limit);

        return ApiResponse.success(userService.findDirectory(keyword, limit));
    }

    /**
     * 分页查询用户列表
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_LEADER')")
    @Operation(summary = "查询用户列表", description = "分页查询用户列表，支持关键词搜索和状态筛选（仅 admin/project_leader）")
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
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_LEADER')")
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
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_LEADER')")
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
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_LEADER')")
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
     *
     * <p>幂等：重复分配不产生重复行，仍返回 200 与现有角色关联；
     * 存在性：用户或角色不存在返回 404。
     */
    @PostMapping("/{id}/roles")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "分配角色给用户", description = "管理员为用户分配角色，支持一个用户多个角色；重复分配幂等")
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

        boolean created = roleService.assignRoleToUser(id, roleId, Objects.requireNonNull(currentUser.getId()));

        UserRole userRole = userRoleRepository.findByUserIdAndRoleId(id, roleId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROLE_NOT_FOUND));

        if (!created) {
            log.info("用户已拥有该角色，返回现有关联: userId={}, roleId={}", id, roleId);
        }
        return ApiResponse.success(userRole);
    }

    /**
     * 撤销用户角色
     *
     * <p>幂等：未分配时同样返回 200，避免重复调用报错。
     */
    @DeleteMapping("/{id}/roles/{roleId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "撤销用户角色", description = "管理员撤销用户的某个角色；重复撤销幂等")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "撤销成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "无权限"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "用户角色关联不存在")
    })
    public ApiResponse<Void> revokeUserRole(
            @Parameter(description = "用户ID", required = true) @PathVariable @Nullable UUID id,
            @Parameter(description = "角色ID", required = true) @PathVariable @Nullable UUID roleId) {
        log.info("撤销用户角色: userId={}, roleId={}", id, roleId);

        if (id == null || roleId == null) {
            return ApiResponse.error("用户ID和角色ID不能为空");
        }

        boolean removed = roleService.removeRoleFromUser(id, roleId);
        if (!removed) {
            log.info("用户未拥有该角色，视为已撤销: userId={}, roleId={}", id, roleId);
        }

        return ApiResponse.success(null);
    }

    // ==================== 用户权限管理 ====================

    /**
     * 直接分配权限给用户
     *
     * <p>幂等：已授予且未过期时保持原样（不续期、不重复插入）；已过期则重新激活。
     * 存在性：用户或权限点不存在返回 404/400。
     */
    @PostMapping("/{id}/permissions")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "直接分配权限给用户", description = "管理员直接分配特定权限给用户（可设置过期时间）；重复分配幂等")
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

        boolean changed = permissionService.assignPermissionToUser(
                id, permissionId, Objects.requireNonNull(currentUser.getId()), expiresAt);

        UserPermission userPermission = userPermissionRepository.findByUserIdAndPermissionId(id, permissionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PERMISSION_NOT_FOUND));

        if (!changed) {
            log.info("用户已拥有该权限，返回现有授权: userId={}, permissionId={}", id, permissionId);
        }
        return ApiResponse.success(userPermission);
    }

    /**
     * 撤销用户权限
     *
     * <p>幂等：未直接授权时同样返回 200。
     */
    @DeleteMapping("/{id}/permissions/{permissionId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "撤销用户权限", description = "管理员撤销直接分配给用户的权限；重复撤销幂等")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "撤销成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "无权限"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "用户权限关联不存在")
    })
    public ApiResponse<Void> revokeUserPermission(
            @Parameter(description = "用户ID", required = true) @PathVariable @Nullable UUID id,
            @Parameter(description = "权限ID", required = true) @PathVariable @Nullable UUID permissionId) {
        log.info("撤销用户权限: userId={}, permissionId={}", id, permissionId);

        if (id == null || permissionId == null) {
            return ApiResponse.error("用户ID和权限ID不能为空");
        }

        boolean removed = permissionService.removePermissionFromUser(id, permissionId);
        if (!removed) {
            log.info("用户未直接拥有该权限，视为已撤销: userId={}, permissionId={}", id, permissionId);
        }

        return ApiResponse.success(null);
    }

    /**
     * 获取当前用户权限
     *
     * <p>返回当前登录用户的真实权限码集合：角色并集 + 用户单独授权（已排除 {@code expires_at} 过期项），
     * 由 {@link PermissionService#getUserPermissionCodes(UUID)} 统一计算并排序。
     *
     * <p>权限点是后续 ABAC 的基础；当前接口准入仍由各控制器的
     * {@code @PreAuthorize("hasRole(...)")} 负责（双轨并存，语义未变）。
     */
    @GetMapping("/me/permissions")
    @Operation(summary = "获取当前用户权限", description = "获取当前登录用户的权限列表（角色权限+直接分配权限的并集）")
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "查询成功"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "未授权")
    })
    public ApiResponse<Map<String, Object>> getCurrentUserPermissions() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            log.warn("未找到认证信息");
            return ApiResponse.error("未授权");
        }

        String username = authentication.getName();
        User user = userService.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        List<String> roleCodes = userService.getUserRoleCodes(user.getId());
        List<String> permissionCodes = permissionService.getUserPermissionCodes(user.getId());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", user.getId());
        result.put("username", user.getUsername());
        result.put("roles", roleCodes);
        result.put("permissions", permissionCodes);
        result.put("permissionCount", permissionCodes.size());

        return ApiResponse.success(result);
    }

}
