package com.vulnaudit.service.impl;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.Permission;
import com.vulnaudit.entity.Role;
import com.vulnaudit.entity.RolePermission;
import com.vulnaudit.entity.UserRole;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.PermissionRepository;
import com.vulnaudit.repository.RolePermissionRepository;
import com.vulnaudit.repository.RoleRepository;
import com.vulnaudit.repository.UserRepository;
import com.vulnaudit.repository.UserRoleRepository;
import com.vulnaudit.service.RoleService;
import com.vulnaudit.util.Paging;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * 角色服务实现类
 *
 * 功能说明：
 * - 实现角色管理的业务逻辑
 * - 提供角色CRUD操作
 * - 提供角色权限分配、用户角色分配
 *
 * <p><b>双轨说明</b>：角色轨（{@code @PreAuthorize("hasRole(...)")}）语义保持不变；
 * 本类维护的 {@code role_permissions} 是权限点轨的数据来源，权限点是后续 ABAC 的基础。
 * 分配类方法统一做「存在性校验 + 幂等」：对象不存在直接报错，重复调用不产生重复行。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserRepository userRepository;

    /**
     * 创建角色
     */
    @Override
    @Transactional
    public Role createRole(@Nonnull Role role) {
        log.info("创建角色: code={}, name={}", role.getCode(), role.getName());

        // 检查角色代码是否已存在
        if (roleRepository.existsByCode(role.getCode())) {
            log.warn("角色代码已存在: {}", role.getCode());
            throw new BusinessException(ErrorCode.ROLE_CODE_EXISTS);
        }

        // 设置默认值
        if (role.getIsSystem() == null) {
            role.setIsSystem(false);
        }

        Role savedRole = roleRepository.save(role);
        log.info("角色创建成功: id={}", savedRole.getId());

        return savedRole;
    }

    /**
     * 根据ID查询角色
     */
    @Override
    public Optional<Role> findById(@Nullable UUID id) {
        log.debug("查询角色: id={}", id);
        return id != null ? roleRepository.findById(id) : Optional.empty();
    }

    /**
     * 根据角色代码查询角色
     */
    @Override
    public Optional<Role> findByCode(@Nullable String code) {
        log.debug("查询角色: code={}", code);
        return roleRepository.findByCode(code);
    }

    /**
     * 查询所有角色
     */
    @Override
    @Nonnull
    public List<Role> findAll() {
        log.debug("查询所有角色");
        return roleRepository.findAll();
    }

    /**
     * 分页查询角色列表
     */
    @Override
    @Nonnull
    public PageResponse<Role> findAll(int page, int size, @Nullable String keyword) {
        log.debug("分页查询角色: page={}, size={}, keyword={}", page, size, keyword);

        Pageable pageable = PageRequest.of(Paging.page(page), Paging.size(size), Sort.by("createdAt").descending());
        Page<Role> rolePage;

        if (StringUtils.hasText(keyword)) {
            rolePage = roleRepository.findAll(
                (root, query, cb) -> cb.or(
                    cb.like(root.get("name"), "%" + keyword + "%"),
                    cb.like(root.get("code"), "%" + keyword + "%"),
                    cb.like(root.get("description"), "%" + keyword + "%")
                ),
                pageable
            );
        } else {
            rolePage = roleRepository.findAll(pageable);
        }

        return PageResponse.<Role>builder()
                .content(rolePage.getContent())
                .totalElements(rolePage.getTotalElements())
                .totalPages(rolePage.getTotalPages())
                .currentPage(rolePage.getNumber())
                .pageSize(rolePage.getSize())
                .build();
    }

    /**
     * 更新角色
     */
    @Override
    @Transactional
    public Role updateRole(@Nullable UUID id, @Nonnull Role role) {
        log.info("更新角色: id={}", id);

        if (id == null) {
            throw new BusinessException(ErrorCode.ROLE_NOT_FOUND);
        }
        Role existingRole = roleRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("角色不存在: id={}", id);
                    return new BusinessException(ErrorCode.ROLE_NOT_FOUND);
                });

        // 系统内置角色不能修改代码（未提交 code 视为不做变更）
        if (Boolean.TRUE.equals(existingRole.getIsSystem())
                && role.getCode() != null
                && !existingRole.getCode().equals(role.getCode())) {
            log.warn("系统内置角色不能修改代码: id={}", id);
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        // 更新可修改的字段
        if (role.getName() != null) {
            existingRole.setName(role.getName());
        }
        if (role.getDescription() != null) {
            existingRole.setDescription(role.getDescription());
        }

        Role updatedRole = roleRepository.save(existingRole);
        log.info("角色更新成功: id={}", updatedRole.getId());

        return updatedRole;
    }

    /**
     * 删除角色
     */
    @Override
    @Transactional
    public void deleteRole(@Nullable UUID id) {
        log.info("删除角色: id={}", id);

        if (id == null) {
            throw new BusinessException(ErrorCode.ROLE_NOT_FOUND);
        }
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("角色不存在: id={}", id);
                    return new BusinessException(ErrorCode.ROLE_NOT_FOUND);
                });

        // 系统内置角色不能删除
        if (role.getIsSystem()) {
            log.warn("系统内置角色不能删除: id={}", id);
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        // 删除角色权限关联
        rolePermissionRepository.deleteByRoleId(id);

        // 删除用户角色关联
        userRoleRepository.deleteByRoleId(id);

        // 删除角色
        roleRepository.delete(role);

        log.info("角色删除成功: id={}", id);
    }

    /**
     * 为角色分配权限
     *
     * <p>幂等：已存在的角色权限关联跳过；权限点ID重复传入只处理一次。
     * 存在性：角色必须存在，任一权限点不存在则整体失败，避免部分成功的脏授权。
     */
    @Override
    @Transactional
    public void assignPermissions(@Nullable UUID roleId, @Nullable List<UUID> permissionIds) {
        log.info("为角色分配权限: roleId={}, permissionCount={}",
                roleId, permissionIds == null ? 0 : permissionIds.size());

        if (roleId == null) {
            throw new BusinessException(ErrorCode.ROLE_NOT_FOUND);
        }
        // 验证角色存在
        if (!roleRepository.existsById(roleId)) {
            log.warn("角色不存在: id={}", roleId);
            throw new BusinessException(ErrorCode.ROLE_NOT_FOUND);
        }

        if (permissionIds == null || permissionIds.isEmpty()) {
            // 幂等语义：没有需要分配的权限点，视为空操作
            log.info("权限ID列表为空，跳过分配: roleId={}", roleId);
            return;
        }

        // 去重，避免同一次请求内的重复ID被反复处理
        Set<UUID> distinctIds = new LinkedHashSet<>();
        permissionIds.stream().filter(Objects::nonNull).forEach(distinctIds::add);

        // 验证权限全部存在
        List<Permission> permissions = permissionRepository.findAllById(distinctIds);
        if (permissions.size() != distinctIds.size()) {
            log.warn("部分权限不存在: roleId={}, requested={}, found={}",
                    roleId, distinctIds.size(), permissions.size());
            throw new BusinessException(ErrorCode.PERMISSION_NOT_FOUND);
        }

        // 已有关联集合，用于幂等跳过
        Set<UUID> grantedIds = new LinkedHashSet<>(rolePermissionRepository.findPermissionIdsByRoleId(roleId));
        int created = 0;
        for (UUID permissionId : distinctIds) {
            if (grantedIds.add(permissionId)) {
                rolePermissionRepository.save(Objects.requireNonNull(RolePermission.builder()
                        .roleId(roleId)
                        .permissionId(permissionId)
                        .build()));
                created++;
            }
        }

        log.info("角色权限分配完成: roleId={}, 新增关联={}, 已存在跳过={}",
                roleId, created, distinctIds.size() - created);
    }

    /**
     * 移除角色权限
     *
     * <p>幂等：关联不存在时静默跳过。
     */
    @Override
    @Transactional
    public void removePermissions(@Nullable UUID roleId, @Nullable List<UUID> permissionIds) {
        log.info("移除角色权限: roleId={}, permissionCount={}",
                roleId, permissionIds == null ? 0 : permissionIds.size());

        if (roleId == null || permissionIds == null || permissionIds.isEmpty()) {
            return;
        }

        Set<UUID> distinctIds = new LinkedHashSet<>();
        permissionIds.stream().filter(Objects::nonNull).forEach(distinctIds::add);

        for (UUID permissionId : distinctIds) {
            rolePermissionRepository.findByRoleIdAndPermissionId(roleId, permissionId)
                    .ifPresent(rolePermissionRepository::delete);
        }

        log.info("角色权限移除完成: roleId={}", roleId);
    }

    /**
     * 获取角色的所有权限
     */
    @Override
    @Nonnull
    public List<Permission> getRolePermissions(@Nullable UUID roleId) {
        log.debug("获取角色权限: roleId={}", roleId);

        List<UUID> permissionIds = rolePermissionRepository.findPermissionIdsByRoleId(roleId);
        if (permissionIds == null || permissionIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<Permission> permissions = permissionRepository.findAllById(permissionIds);
        return new ArrayList<>(permissions);
    }

    /**
     * 覆盖式同步角色权限（差量写入，幂等）
     */
    @Override
    @Transactional
    public void syncPermissions(@Nullable UUID roleId, @Nullable List<UUID> permissionIds) {
        log.info("同步角色权限: roleId={}, targetCount={}",
                roleId, permissionIds == null ? 0 : permissionIds.size());

        if (roleId == null) {
            throw new BusinessException(ErrorCode.ROLE_NOT_FOUND);
        }
        if (!roleRepository.existsById(roleId)) {
            log.warn("角色不存在: id={}", roleId);
            throw new BusinessException(ErrorCode.ROLE_NOT_FOUND);
        }

        Set<UUID> target = new LinkedHashSet<>();
        if (permissionIds != null) {
            permissionIds.stream().filter(Objects::nonNull).forEach(target::add);
        }

        // 存在性校验：目标集合中的权限点必须全部存在
        if (!target.isEmpty()) {
            List<Permission> permissions = permissionRepository.findAllById(target);
            if (permissions.size() != target.size()) {
                log.warn("部分权限不存在: roleId={}, requested={}, found={}",
                        roleId, target.size(), permissions.size());
                throw new BusinessException(ErrorCode.PERMISSION_NOT_FOUND);
            }
        }

        Set<UUID> current = new LinkedHashSet<>(rolePermissionRepository.findPermissionIdsByRoleId(roleId));

        // 移除多余
        Set<UUID> toRemove = new LinkedHashSet<>(current);
        toRemove.removeAll(target);
        for (UUID permissionId : toRemove) {
            rolePermissionRepository.findByRoleIdAndPermissionId(roleId, permissionId)
                    .ifPresent(rolePermissionRepository::delete);
        }

        // 补充缺失
        Set<UUID> toAdd = new LinkedHashSet<>(target);
        toAdd.removeAll(current);
        int created = 0;
        for (UUID permissionId : toAdd) {
            rolePermissionRepository.save(Objects.requireNonNull(RolePermission.builder()
                    .roleId(roleId)
                    .permissionId(permissionId)
                    .build()));
            created++;
        }

        log.info("角色权限同步完成: roleId={}, 新增={}, 移除={}", roleId, created, toRemove.size());
    }

    /**
     * 获取角色的权限编码集合（去重、字典序）
     */
    @Override
    @Nonnull
    public List<String> getRolePermissionCodes(@Nullable UUID roleId) {
        if (roleId == null) {
            return new ArrayList<>();
        }
        Set<String> codes = new TreeSet<>();
        for (Permission permission : getRolePermissions(roleId)) {
            if (permission.getCode() != null) {
                codes.add(permission.getCode());
            }
        }
        return new ArrayList<>(codes);
    }

    /**
     * 检查角色代码是否存在
     */
    @Override
    public boolean existsByCode(@Nullable String code) {
        return roleRepository.existsByCode(code);
    }

    /**
     * 为用户分配角色
     */
    @Override
    @Transactional
    public boolean assignRoleToUser(@Nullable UUID userId, @Nullable UUID roleId) {
        return assignRoleToUser(userId, roleId, null);
    }

    /**
     * 为用户分配角色，并记录分配人
     *
     * <p>此前实现直接 {@code save} 而没有写 {@code assigned_by}，而该列在库里是 NOT NULL，
     * 走 RoleService 分配角色会直接抛约束异常；此处一并修正。
     */
    @Override
    @Transactional
    public boolean assignRoleToUser(@Nullable UUID userId, @Nullable UUID roleId, @Nullable UUID assignedBy) {
        log.info("为用户分配角色: userId={}, roleId={}, assignedBy={}", userId, roleId, assignedBy);

        if (userId == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (roleId == null) {
            throw new BusinessException(ErrorCode.ROLE_NOT_FOUND);
        }
        // 存在性校验：用户
        if (!userRepository.existsById(userId)) {
            log.warn("用户不存在: userId={}", userId);
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        // 存在性校验：角色
        if (!roleRepository.existsById(roleId)) {
            log.warn("角色不存在: roleId={}", roleId);
            throw new BusinessException(ErrorCode.ROLE_NOT_FOUND);
        }
        // 幂等：已分配则不改动
        if (userRoleRepository.existsByUserIdAndRoleId(userId, roleId)) {
            log.info("用户已拥有该角色，跳过: userId={}, roleId={}", userId, roleId);
            return false;
        }

        UserRole userRole = UserRole.builder()
                .userId(userId)
                .roleId(roleId)
                .assignedBy(resolveAssigner(assignedBy, userId))
                .assignedAt(LocalDateTime.now())
                .build();

        userRoleRepository.save(Objects.requireNonNull(userRole));
        log.info("角色分配成功: userId={}, roleId={}", userId, roleId);
        return true;
    }

    /**
     * 移除用户的角色
     */
    @Override
    @Transactional
    public boolean removeRoleFromUser(@Nullable UUID userId, @Nullable UUID roleId) {
        log.info("移除用户角色: userId={}, roleId={}", userId, roleId);

        if (userId == null || roleId == null) {
            return false;
        }
        return userRoleRepository.findByUserIdAndRoleId(userId, roleId)
                .map(userRole -> {
                    userRoleRepository.delete(userRole);
                    log.info("角色移除成功: userId={}, roleId={}", userId, roleId);
                    return true;
                })
                .orElseGet(() -> {
                    log.info("用户未拥有该角色，跳过: userId={}, roleId={}", userId, roleId);
                    return false;
                });
    }

    /**
     * 解析分配人：显式传入 → 当前登录用户 → 用户本人（保证 assigned_by 非空）
     */
    private UUID resolveAssigner(UUID assignedBy, UUID userId) {
        if (assignedBy != null) {
            return assignedBy;
        }
        UUID currentUserId = currentUserId();
        return currentUserId != null ? currentUserId : userId;
    }

    /** 从安全上下文解析当前用户ID（未认证时返回 null） */
    private UUID currentUserId() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || authentication.getName() == null) {
                return null;
            }
            return userRepository.findByUsername(authentication.getName())
                    .map(user -> user.getId())
                    .orElse(null);
        } catch (RuntimeException ex) {
            log.debug("解析当前用户失败，回退为被分配用户: {}", ex.getMessage());
            return null;
        }
    }

}
