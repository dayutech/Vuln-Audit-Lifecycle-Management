package com.vulnaudit.service.impl;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.Permission;
import com.vulnaudit.entity.UserPermission;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.PermissionRepository;
import com.vulnaudit.repository.RolePermissionRepository;
import com.vulnaudit.repository.UserPermissionRepository;
import com.vulnaudit.repository.UserRepository;
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
import java.util.TreeSet;
import java.util.UUID;

/**
 * 权限服务实现类
 *
 * 功能说明：
 * - 实现权限管理的业务逻辑
 * - 提供权限CRUD操作
 * - 提供权限验证与用户直接授权（含过期时间）
 *
 * <p><b>双轨说明</b>：控制器仍以 {@code @PreAuthorize("hasRole(...)")} 做粗粒度准入，
 * 本实现只维护「权限点轨」的数据与判定能力（{@link #getUserPermissionCodes(UUID)}、
 * {@link #hasPermission(UUID, String, String)}）。权限点是后续 ABAC 的基础：
 * 后续可按 {@code (apiPath, httpMethod)} 反查所需权限编码，再比对用户有效权限集合，
 * 即可在不改动既有角色注解语义的情况下渐进替换硬编码角色名。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionServiceImpl implements PermissionService {

    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserPermissionRepository userPermissionRepository;
    private final UserRoleRepository userRoleRepository;
    private final UserRepository userRepository;

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
     * 获取用户的所有有效权限代码（角色并集 + 用户单独授权）
     *
     * <p>直接授权按 {@code expires_at} 过滤（{@code NULL} 表示永久有效）；
     * 角色授权不设过期时间。返回结果去重并排序，保证同一用户多次调用结果稳定。
     */
    @Override
    @Nonnull
    public List<String> getUserPermissionCodes(@Nullable UUID userId) {
        log.debug("获取用户权限代码: userId={}", userId);

        if (userId == null) {
            return new ArrayList<>();
        }

        Set<String> permissionCodes = new TreeSet<>();

        // 1) 直接授予用户的权限（排除已过期）
        List<UUID> directPermissionIds = userPermissionRepository
                .findValidPermissionIdsByUserId(userId, LocalDateTime.now());
        if (directPermissionIds != null && !directPermissionIds.isEmpty()) {
            permissionRepository.findAllById(directPermissionIds).stream()
                    .map(Permission::getCode)
                    .filter(Objects::nonNull)
                    .forEach(permissionCodes::add);
        }

        // 2) 通过角色继承的权限（多角色取并集）
        List<UUID> roleIds = userRoleRepository.findRoleIdsByUserId(userId);
        if (roleIds != null && !roleIds.isEmpty()) {
            Set<UUID> rolePermissionIds = new HashSet<>();
            for (UUID roleId : roleIds) {
                rolePermissionIds.addAll(rolePermissionRepository.findPermissionIdsByRoleId(roleId));
            }
            if (!rolePermissionIds.isEmpty()) {
                permissionRepository.findAllById(rolePermissionIds).stream()
                        .map(Permission::getCode)
                        .filter(Objects::nonNull)
                        .forEach(permissionCodes::add);
            }
        }

        return new ArrayList<>(permissionCodes);
    }

    /**
     * 直接授予用户权限点（幂等）
     */
    @Override
    @Transactional
    public boolean assignPermissionToUser(@Nullable UUID userId,
                                          @Nullable UUID permissionId,
                                          @Nullable UUID grantedBy,
                                          @Nullable LocalDateTime expiresAt) {
        log.info("为用户分配权限: userId={}, permissionId={}, expiresAt={}", userId, permissionId, expiresAt);

        if (userId == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (permissionId == null) {
            throw new BusinessException(ErrorCode.PERMISSION_NOT_FOUND);
        }
        // 存在性校验：用户
        if (!userRepository.existsById(userId)) {
            log.warn("用户不存在: userId={}", userId);
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        // 存在性校验：权限点
        permissionRepository.findById(permissionId)
                .orElseThrow(() -> {
                    log.warn("权限点不存在: permissionId={}", permissionId);
                    return new BusinessException(ErrorCode.PERMISSION_NOT_FOUND);
                });

        UserPermission existing = userPermissionRepository
                .findByUserIdAndPermissionId(userId, permissionId)
                .orElse(null);

        if (existing == null) {
            userPermissionRepository.save(Objects.requireNonNull(UserPermission.builder()
                    .userId(userId)
                    .permissionId(permissionId)
                    .grantedBy(grantedBy != null ? grantedBy : userId)
                    .expiresAt(expiresAt)
                    .build()));
            log.info("权限分配成功: userId={}, permissionId={}", userId, permissionId);
            return true;
        }

        if (!existing.isExpired()) {
            // 幂等：已生效的授权保持不变，不因重复调用续期或重复插入
            log.info("用户已拥有该权限，跳过: userId={}, permissionId={}", userId, permissionId);
            return false;
        }

        // 过期授权：复用同一行重新激活，避免唯一键冲突
        existing.setExpiresAt(expiresAt);
        existing.setGrantedBy(grantedBy != null ? grantedBy : userId);
        userPermissionRepository.save(existing);
        log.info("已过期的用户权限重新激活: userId={}, permissionId={}, expiresAt={}",
                userId, permissionId, expiresAt);
        return true;
    }

    /**
     * 撤销用户直接授权的权限点（幂等）
     */
    @Override
    @Transactional
    public boolean removePermissionFromUser(@Nullable UUID userId, @Nullable UUID permissionId) {
        log.info("撤销用户权限: userId={}, permissionId={}", userId, permissionId);

        if (userId == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        if (permissionId == null) {
            throw new BusinessException(ErrorCode.PERMISSION_NOT_FOUND);
        }
        if (!userRepository.existsById(userId)) {
            log.warn("用户不存在: userId={}", userId);
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        permissionRepository.findById(permissionId)
                .orElseThrow(() -> {
                    log.warn("权限点不存在: permissionId={}", permissionId);
                    return new BusinessException(ErrorCode.PERMISSION_NOT_FOUND);
                });

        return userPermissionRepository.findByUserIdAndPermissionId(userId, permissionId)
                .map(userPermission -> {
                    userPermissionRepository.delete(userPermission);
                    log.info("权限撤销成功: userId={}, permissionId={}", userId, permissionId);
                    return true;
                })
                .orElseGet(() -> {
                    log.info("用户未直接拥有该权限，跳过: userId={}, permissionId={}", userId, permissionId);
                    return false;
                });
    }

    /**
     * 查询用户当前生效的直接授权
     */
    @Override
    @Nonnull
    public List<UserPermission> getUserDirectPermissions(@Nullable UUID userId) {
        if (userId == null) {
            return new ArrayList<>();
        }
        return userPermissionRepository.findValidPermissionsByUserId(userId, LocalDateTime.now());
    }

}
