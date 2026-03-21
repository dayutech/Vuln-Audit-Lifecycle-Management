package com.vulnaudit.repository;

import com.vulnaudit.entity.UserPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 用户权限关联数据访问层接口
 * 
 * 功能说明：
 * - 提供用户权限关联的CRUD操作
 * - 提供用户权限查询相关的自定义方法
 * - 支持临时权限和过期时间管理
 */
@Repository
public interface UserPermissionRepository extends JpaRepository<UserPermission, UUID> {

    /**
     * 根据用户ID查询所有权限关联
     * 用于获取用户直接拥有的所有权限
     * 
     * @param userId 用户ID
     * @return 用户权限关联列表
     */
    List<UserPermission> findByUserId(UUID userId);

    /**
     * 根据权限ID查询所有用户关联
     * 用于获取拥有该权限的所有用户
     * 
     * @param permissionId 权限ID
     * @return 用户权限关联列表
     */
    List<UserPermission> findByPermissionId(UUID permissionId);

    /**
     * 根据用户ID和权限ID查询关联
     * 用于验证用户是否拥有指定权限
     * 
     * @param userId 用户ID
     * @param permissionId 权限ID
     * @return 用户权限关联（如果存在）
     */
    Optional<UserPermission> findByUserIdAndPermissionId(UUID userId, UUID permissionId);

    /**
     * 检查用户是否拥有指定权限
     * 用于验证用户权限
     * 
     * @param userId 用户ID
     * @param permissionId 权限ID
     * @return 如果存在关联返回true，否则返回false
     */
    boolean existsByUserIdAndPermissionId(UUID userId, UUID permissionId);

    /**
     * 查询用户的有效权限（未过期的权限）
     * 用于获取用户当前有效的直接权限
     * 
     * @param userId 用户ID
     * @param now 当前时间
     * @return 有效的用户权限关联列表
     */
    @Query("SELECT up FROM UserPermission up WHERE up.userId = :userId AND (up.expiresAt IS NULL OR up.expiresAt > :now)")
    List<UserPermission> findValidPermissionsByUserId(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

    /**
     * 删除用户的所有权限关联
     * 用于用户删除或重置权限
     * 
     * @param userId 用户ID
     * @return 删除的记录数
     */
    @Modifying
    @Query("DELETE FROM UserPermission up WHERE up.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);

    /**
     * 删除权限的所有用户关联
     * 用于权限删除时清理关联
     * 
     * @param permissionId 权限ID
     * @return 删除的记录数
     */
    @Modifying
    @Query("DELETE FROM UserPermission up WHERE up.permissionId = :permissionId")
    int deleteByPermissionId(@Param("permissionId") UUID permissionId);

    /**
     * 删除已过期的权限关联
     * 用于定时清理过期权限
     * 
     * @param now 当前时间
     * @return 删除的记录数
     */
    @Modifying
    @Query("DELETE FROM UserPermission up WHERE up.expiresAt IS NOT NULL AND up.expiresAt <= :now")
    int deleteExpiredPermissions(@Param("now") LocalDateTime now);

    /**
     * 根据用户ID查询权限ID列表（仅有效权限）
     * 用于快速获取用户的所有有效权限ID
     * 
     * @param userId 用户ID
     * @param now 当前时间
     * @return 权限ID列表
     */
    @Query("SELECT up.permissionId FROM UserPermission up WHERE up.userId = :userId AND (up.expiresAt IS NULL OR up.expiresAt > :now)")
    List<UUID> findValidPermissionIdsByUserId(@Param("userId") UUID userId, @Param("now") LocalDateTime now);

}
