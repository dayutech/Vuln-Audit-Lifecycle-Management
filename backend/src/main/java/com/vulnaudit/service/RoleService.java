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
 * - 提供角色权限分配、用户角色分配
 *
 * <p><b>双轨说明</b>：角色仍是当前 API 准入的第一轨（{@code @PreAuthorize("hasRole(...)")}），
 * 角色与权限点的关联（{@code role_permissions}）构成权限点轨的基础数据，
 * 供后续 ABAC 按 {@code 资源:操作} 判定。分配接口统一要求：
 * 关联对象必须存在（用户/角色/权限点），重复调用必须幂等。
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
     * 覆盖式同步角色权限（差量写入）。
     *
     * <p>语义：以 {@code permissionIds} 为目标集合——缺失的补上、多余的移除。
     * 幂等：目标集合与现有一致时不产生任何写入。权限点不存在时整体失败。
     *
     * @param roleId        角色ID
     * @param permissionIds 目标权限ID集合（为空表示清空该角色的权限点）
     */
    void syncPermissions(@Nullable UUID roleId, @Nullable List<UUID> permissionIds);

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
     * <p>存在性校验：用户、角色都必须存在，否则抛出业务异常；
     * 幂等：已分配时不做任何变更，返回 {@code false} 而不报错。
     *
     * @param userId 用户ID
     * @param roleId 角色ID
     * @return 是否实际新建了关联
     */
    boolean assignRoleToUser(@Nullable UUID userId, @Nullable UUID roleId);

    /**
     * 为用户分配角色，并显式记录分配人。
     *
     * @param userId     用户ID
     * @param roleId     角色ID
     * @param assignedBy 分配人ID（为空时回退为当前登录用户，再回退为用户本人）
     * @return 是否实际新建了关联
     */
    boolean assignRoleToUser(@Nullable UUID userId, @Nullable UUID roleId, @Nullable UUID assignedBy);

    /**
     * 移除用户的角色
     *
     * <p>幂等：关联不存在时静默返回 {@code false}。
     *
     * @param userId 用户ID
     * @param roleId 角色ID
     * @return 是否实际删除了关联
     */
    boolean removeRoleFromUser(@Nullable UUID userId, @Nullable UUID roleId);

    /**
     * 获取角色的权限编码集合
     *
     * @param roleId 角色ID
     * @return 权限编码列表（去重、字典序）
     */
    @Nonnull
    List<String> getRolePermissionCodes(@Nullable UUID roleId);

}
