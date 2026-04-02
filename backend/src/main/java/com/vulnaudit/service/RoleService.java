package com.vulnaudit.service;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.Permission;
import com.vulnaudit.entity.Role;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 角色服务接口
 *
 * 功能说明：
 * - 定义角色管理的业务逻辑接口
 * - 提供角色CRUD操作
 * - 提供角色权限分配
 */
public interface RoleService {

    /**
     * 创建角色
     *
     * @param role 角色实体
     * @return 创建后的角色实体
     */
    @Nonnull
    Role createRole(@Nonnull Role role);

    /**
     * 根据ID查询角色
     *
     * @param id 角色ID
     * @return 角色实体
     */
    Optional<Role> findById(@Nullable UUID id);

    /**
     * 根据角色代码查询角色
     *
     * @param code 角色代码
     * @return 角色实体
     */
    Optional<Role> findByCode(@Nullable String code);

    /**
     * 查询所有角色
     *
     * @return 角色列表
     */
    @Nonnull
    List<Role> findAll();

    /**
     * 分页查询角色列表
     *
     * @param page 页码
     * @param size 每页大小
     * @param keyword 搜索关键词
     * @return 分页角色列表
     */
    @Nonnull
    PageResponse<Role> findAll(int page, int size, @Nullable String keyword);

    /**
     * 更新角色
     *
     * @param id 角色ID
     * @param role 更新后的角色信息
     * @return 更新后的角色实体
     */
    @Nonnull
    Role updateRole(@Nullable UUID id, @Nonnull Role role);

    /**
     * 删除角色
     *
     * @param id 角色ID
     */
    void deleteRole(@Nullable UUID id);

    /**
     * 为角色分配权限
     *
     * @param roleId 角色ID
     * @param permissionIds 权限ID列表
     */
    void assignPermissions(@Nullable UUID roleId, @Nullable List<UUID> permissionIds);

    /**
     * 移除角色权限
     *
     * @param roleId 角色ID
     * @param permissionIds 权限ID列表
     */
    void removePermissions(@Nullable UUID roleId, @Nullable List<UUID> permissionIds);

    /**
     * 获取角色的所有权限
     *
     * @param roleId 角色ID
     * @return 权限列表
     */
    @Nonnull
    List<Permission> getRolePermissions(@Nullable UUID roleId);

    /**
     * 检查角色代码是否存在
     *
     * @param code 角色代码
     * @return 是否存在
     */
    boolean existsByCode(@Nullable String code);

    /**
     * 为用户分配角色
     *
     * @param userId 用户ID
     * @param roleId 角色ID
     */
    void assignRoleToUser(@Nullable UUID userId, @Nullable UUID roleId);

    /**
     * 移除用户的角色
     *
     * @param userId 用户ID
     * @param roleId 角色ID
     */
    void removeRoleFromUser(@Nullable UUID userId, @Nullable UUID roleId);

}
