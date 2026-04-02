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
import com.vulnaudit.repository.UserRoleRepository;
import com.vulnaudit.service.RoleService;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 角色服务实现类
 *
 * 功能说明：
 * - 实现角色管理的业务逻辑
 * - 提供角色CRUD操作
 * - 提供角色权限分配
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoleServiceImpl implements RoleService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRoleRepository userRoleRepository;

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

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
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

        // 系统内置角色不能修改代码
        if (existingRole.getIsSystem() && !existingRole.getCode().equals(role.getCode())) {
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
     */
    @Override
    @Transactional
    public void assignPermissions(@Nullable UUID roleId, @Nullable List<UUID> permissionIds) {
        log.info("为角色分配权限: roleId={}, permissionIds={}", roleId, permissionIds);

        if (roleId == null) {
            throw new BusinessException(ErrorCode.ROLE_NOT_FOUND);
        }
        // 验证角色存在
        if (!roleRepository.existsById(roleId)) {
            log.warn("角色不存在: id={}", roleId);
            throw new BusinessException(ErrorCode.ROLE_NOT_FOUND);
        }

        // 验证权限存在
        if (permissionIds == null || permissionIds.isEmpty()) {
            log.warn("权限ID列表为空");
            throw new BusinessException(ErrorCode.PERMISSION_NOT_FOUND);
        }

        List<Permission> permissions = permissionRepository.findAllById(permissionIds);
        if (permissions.size() != permissionIds.size()) {
            log.warn("部分权限不存在");
            throw new BusinessException(ErrorCode.PERMISSION_NOT_FOUND);
        }

        // 创建角色权限关联
        for (UUID permissionId : permissionIds) {
            if (!rolePermissionRepository.existsByRoleIdAndPermissionId(roleId, permissionId)) {
                RolePermission rolePermission = RolePermission.builder()
                        .roleId(roleId)
                        .permissionId(permissionId)
                        .build();
                rolePermissionRepository.save(Objects.requireNonNull(rolePermission));
            }
        }

        log.info("角色权限分配成功: roleId={}", roleId);
    }

    /**
     * 移除角色权限
     */
    @Override
    @Transactional
    public void removePermissions(@Nullable UUID roleId, @Nullable List<UUID> permissionIds) {
        log.info("移除角色权限: roleId={}, permissionIds={}", roleId, permissionIds);

        if (permissionIds == null) {
            return;
        }

        for (UUID permissionId : permissionIds) {
            rolePermissionRepository.findByRoleIdAndPermissionId(roleId, permissionId)
                    .ifPresent(rolePermissionRepository::delete);
        }

        log.info("角色权限移除成功: roleId={}", roleId);
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
    public void assignRoleToUser(@Nullable UUID userId, @Nullable UUID roleId) {
        log.info("为用户分配角色: userId={}, roleId={}", userId, roleId);

        if (userId == null || roleId == null) {
            return;
        }
        if (userRoleRepository.existsByUserIdAndRoleId(userId, roleId)) {
            log.warn("用户已拥有该角色: userId={}, roleId={}", userId, roleId);
            return;
        }

        UserRole userRole = UserRole.builder()
                .userId(userId)
                .roleId(roleId)
                .assignedAt(LocalDateTime.now())
                .build();

        userRoleRepository.save(Objects.requireNonNull(userRole));
        log.info("角色分配成功: userId={}, roleId={}", userId, roleId);
    }

    /**
     * 移除用户的角色
     */
    @Override
    @Transactional
    public void removeRoleFromUser(@Nullable UUID userId, @Nullable UUID roleId) {
        log.info("移除用户角色: userId={}, roleId={}", userId, roleId);

        if (userId != null && roleId != null) {
            userRoleRepository.findByUserIdAndRoleId(userId, roleId)
                    .ifPresent(userRoleRepository::delete);
        }

        log.info("角色移除成功: userId={}, roleId={}", userId, roleId);
    }

}
