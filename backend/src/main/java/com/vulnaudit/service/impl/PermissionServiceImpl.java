package com.vulnaudit.service.impl;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.Permission;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.PermissionRepository;
import com.vulnaudit.repository.RolePermissionRepository;
import com.vulnaudit.repository.UserPermissionRepository;
import com.vulnaudit.repository.UserRoleRepository;
import com.vulnaudit.service.PermissionService;
import com.vulnaudit.util.Paging;
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
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 权限服务实现类
 *
 * 功能说明：
 * - 实现权限管理的业务逻辑
 * - 提供权限CRUD操作
 * - 提供权限验证
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserPermissionRepository userPermissionRepository;
    private final UserRoleRepository userRoleRepository;

    /**
     * 创建权限
     */
    @Override
    @Transactional
    public Permission createPermission(@Nonnull Permission permission) {
        log.info("创建权限: code={}, name={}", permission.getCode(), permission.getName());

        // 检查权限代码是否已存在
        if (permissionRepository.existsByCode(permission.getCode())) {
            log.warn("权限代码已存在: {}", permission.getCode());
            throw new BusinessException(ErrorCode.PERMISSION_CODE_EXISTS);
        }

        Permission savedPermission = permissionRepository.save(permission);
        log.info("权限创建成功: id={}", savedPermission.getId());

        return savedPermission;
    }

    /**
     * 根据ID查询权限
     */
    @Override
    public Optional<Permission> findById(@Nullable UUID id) {
        log.debug("查询权限: id={}", id);
        return id != null ? permissionRepository.findById(id) : Optional.empty();
    }

    /**
     * 根据权限代码查询权限
     */
    @Override
    public Optional<Permission> findByCode(@Nullable String code) {
        log.debug("查询权限: code={}", code);
        return permissionRepository.findByCode(code);
    }

    /**
     * 查询所有权限
     */
    @Override
    @Nonnull
    public List<Permission> findAll() {
        log.debug("查询所有权限");
        return permissionRepository.findAll();
    }

    /**
     * 分页查询权限列表
     */
    @Override
    @Nonnull
    public PageResponse<Permission> findAll(int page, int size, @Nullable String keyword) {
        log.debug("分页查询权限: page={}, size={}, keyword={}", page, size, keyword);

        Pageable pageable = PageRequest.of(Paging.page(page), Paging.size(size), Sort.by("createdAt").descending());
        Page<Permission> permissionPage;

        if (StringUtils.hasText(keyword)) {
            permissionPage = permissionRepository.findAll(
                (root, query, cb) -> cb.or(
                    cb.like(root.get("name"), "%" + keyword + "%"),
                    cb.like(root.get("code"), "%" + keyword + "%"),
                    cb.like(root.get("apiPath"), "%" + keyword + "%")
                ),
                pageable
            );
        } else {
            permissionPage = permissionRepository.findAll(pageable);
        }

        return PageResponse.<Permission>builder()
                .content(permissionPage.getContent())
                .totalElements(permissionPage.getTotalElements())
                .totalPages(permissionPage.getTotalPages())
                .currentPage(permissionPage.getNumber())
                .pageSize(permissionPage.getSize())
                .build();
    }

    /**
     * 更新权限
     */
    @Override
    @Transactional
    public Permission updatePermission(@Nullable UUID id, @Nonnull Permission permission) {
        log.info("更新权限: id={}", id);

        if (id == null) {
            throw new BusinessException(ErrorCode.PERMISSION_NOT_FOUND);
        }
        Permission existingPermission = permissionRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("权限不存在: id={}", id);
                    return new BusinessException(ErrorCode.PERMISSION_NOT_FOUND);
                });

        // 更新可修改的字段
        if (permission.getName() != null) {
            existingPermission.setName(permission.getName());
        }
        if (permission.getApiPath() != null) {
            existingPermission.setApiPath(permission.getApiPath());
        }
        if (permission.getHttpMethod() != null) {
            existingPermission.setHttpMethod(permission.getHttpMethod());
        }
        if (permission.getDescription() != null) {
            existingPermission.setDescription(permission.getDescription());
        }

        Permission updatedPermission = permissionRepository.save(Objects.requireNonNull(existingPermission));
        log.info("权限更新成功: id={}", updatedPermission.getId());

        return updatedPermission;
    }

    /**
     * 删除权限
     */
    @Override
    @Transactional
    public void deletePermission(@Nullable UUID id) {
        log.info("删除权限: id={}", id);

        if (id == null) {
            throw new BusinessException(ErrorCode.PERMISSION_NOT_FOUND);
        }
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("权限不存在: id={}", id);
                    return new BusinessException(ErrorCode.PERMISSION_NOT_FOUND);
                });

        // 删除角色权限关联
        rolePermissionRepository.deleteByPermissionId(id);

        // 删除用户权限关联
        userPermissionRepository.deleteByPermissionId(id);

        // 删除权限
        permissionRepository.delete(Objects.requireNonNull(permission));

        log.info("权限删除成功: id={}", id);
    }

    /**
     * 检查权限代码是否存在
     */
    @Override
    public boolean existsByCode(@Nullable String code) {
        return permissionRepository.existsByCode(code);
    }

    /**
     * 验证用户是否有指定API的访问权限
     */
    @Override
    public boolean hasPermission(@Nullable UUID userId, @Nullable String apiPath, @Nullable String httpMethod) {
        log.debug("验证用户权限: userId={}, apiPath={}, httpMethod={}", userId, apiPath, httpMethod);

        // 获取用户的所有权限代码
        List<String> permissionCodes = getUserPermissionCodes(userId);

        // 查找匹配的权限
        Optional<Permission> permission = permissionRepository.findByApiPathAndHttpMethod(apiPath, httpMethod);
        if (permission.isEmpty()) {
            // 如果没有定义该API的权限，默认允许访问
            return true;
        }

        Permission foundPermission = permission.orElse(null);
        if (foundPermission == null) {
            return true;
        }
        return permissionCodes.contains(foundPermission.getCode());
    }

    /**
     * 获取用户的所有权限代码
     */
    @Override
    @Nonnull
    public List<String> getUserPermissionCodes(@Nullable UUID userId) {
        log.debug("获取用户权限代码: userId={}", userId);

        Set<String> permissionCodes = new HashSet<>();

        // 获取直接授予用户的权限（有效权限）
        List<UUID> directPermissionIds = userPermissionRepository.findValidPermissionIdsByUserId(userId, LocalDateTime.now());
        if (directPermissionIds != null && !directPermissionIds.isEmpty()) {
            List<Permission> directPermissions = permissionRepository.findAllById(directPermissionIds);
            if (directPermissions != null) {
                directPermissions.stream()
                        .map(Permission::getCode)
                        .forEach(permissionCodes::add);
            }
        }

        // 获取通过角色获得的权限
        List<UUID> roleIds = userRoleRepository.findRoleIdsByUserId(userId);
        if (roleIds != null) {
            for (UUID roleId : roleIds) {
                List<UUID> rolePermissionIds = rolePermissionRepository.findPermissionIdsByRoleId(roleId);
                if (rolePermissionIds != null && !rolePermissionIds.isEmpty()) {
                    permissionRepository.findAllById(rolePermissionIds).stream()
                            .map(Permission::getCode)
                            .forEach(permissionCodes::add);
                }
            }
        }

        return new ArrayList<>(permissionCodes);
    }

}
