package com.vulnaudit.repository;

import com.vulnaudit.entity.UserRole;
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
 * 用户角色关联数据访问层接口
 *
 * 功能说明：
 * - 提供用户角色关联的CRUD操作
 * - 提供用户角色查询相关的自定义方法
 * - 支持批量删除用户角色
 */
@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {

    /**
     * 根据用户ID查询所有角色关联
     * 用于获取用户拥有的所有角色
     *
     * @param userId 用户ID
     * @return 用户角色关联列表
     */
    List<UserRole> findByUserId(@Nullable UUID userId);

    /**
     * 根据角色ID查询所有用户关联
     * 用于获取拥有该角色的所有用户
     *
     * @param roleId 角色ID
     * @return 用户角色关联列表
     */
    List<UserRole> findByRoleId(@Nullable UUID roleId);

    /**
     * 根据用户ID和角色ID查询关联
     * 用于验证用户是否拥有指定角色
     *
     * @param userId 用户ID
     * @param roleId 角色ID
     * @return 用户角色关联（如果存在）
     */
    Optional<UserRole> findByUserIdAndRoleId(@Nullable UUID userId, @Nullable UUID roleId);

    /**
     * 检查用户是否拥有指定角色
     * 用于验证用户角色
     *
     * @param userId 用户ID
     * @param roleId 角色ID
     * @return 如果存在关联返回true，否则返回false
     */
    boolean existsByUserIdAndRoleId(@Nullable UUID userId, @Nullable UUID roleId);

    /**
     * 删除用户的所有角色关联
     * 用于用户删除或重置角色
     *
     * @param userId 用户ID
     * @return 删除的记录数
     */
    @Modifying
    @Query("DELETE FROM UserRole ur WHERE ur.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);

    /**
     * 删除角色的所有用户关联
     * 用于角色删除时清理关联
     *
     * @param roleId 角色ID
     * @return 删除的记录数
     */
    @Modifying
    @Query("DELETE FROM UserRole ur WHERE ur.roleId = :roleId")
    int deleteByRoleId(@Param("roleId") UUID roleId);

    /**
     * 根据用户ID查询角色ID列表
     * 用于快速获取用户的所有角色ID
     * 
     * @param userId 用户ID
     * @return 角色ID列表
     */
    @Query("SELECT ur.roleId FROM UserRole ur WHERE ur.userId = :userId")
    List<UUID> findRoleIdsByUserId(@Param("userId") UUID userId);

}
