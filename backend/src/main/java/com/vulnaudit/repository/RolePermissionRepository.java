package com.vulnaudit.repository;

import com.vulnaudit.entity.RolePermission;
import jakarta.annotation.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 角色权限关联数据访问层接口
 *
 * 功能说明：
 * - 提供角色权限关联的CRUD操作
 * - 提供角色权限查询相关的自定义方法
 * - 支持批量删除角色权限
 */
@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, UUID> {

    /**
     * 根据角色ID查询所有权限关联
     * 用于获取角色拥有的所有权限
     *
     * @param roleId 角色ID
     * @return 角色权限关联列表
     */
    List<RolePermission> findByRoleId(@Nullable UUID roleId);

    /**
     * 根据权限ID查询所有角色关联
     * 用于获取拥有该权限的所有角色
     *
     * @param permissionId 权限ID
     * @return 角色权限关联列表
     */
    List<RolePermission> findByPermissionId(@Nullable UUID permissionId);

    /**
     * 检查角色是否拥有指定权限
     * 用于验证角色权限
     *
     * @param roleId 角色ID
     * @param permissionId 权限ID
     * @return 如果存在关联返回true，否则返回false
     */
    boolean existsByRoleIdAndPermissionId(@Nullable UUID roleId, @Nullable UUID permissionId);

    /**
     * 删除角色的所有权限关联
     * 用于角色删除或重置权限
     *
     * @param roleId 角色ID
     * @return 删除的记录数
     */
    @Modifying
    @Query("DELETE FROM RolePermission rp WHERE rp.roleId = :roleId")
    int deleteByRoleId(@Param("roleId") UUID roleId);

    /**
     * 删除权限的所有角色关联
     * 用于权限删除时清理关联
     *
     * @param permissionId 权限ID
     * @return 删除的记录数
     */
    @Modifying
    @Query("DELETE FROM RolePermission rp WHERE rp.permissionId = :permissionId")
    int deleteByPermissionId(@Param("permissionId") UUID permissionId);

    /**
     * 根据角色ID查询权限ID列表
     * 用于快速获取角色的所有权限ID
     *
     * @param roleId 角色ID
     * @return 权限ID列表
     */
    @Query("SELECT rp.permissionId FROM RolePermission rp WHERE rp.roleId = :roleId")
    List<UUID> findPermissionIdsByRoleId(@Param("roleId") UUID roleId);

    /**
     * 根据角色ID和权限ID查询关联
     */
    Optional<RolePermission> findByRoleIdAndPermissionId(UUID roleId, UUID permissionId);

}
