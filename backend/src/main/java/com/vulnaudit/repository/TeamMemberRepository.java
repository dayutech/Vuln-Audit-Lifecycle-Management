package com.vulnaudit.repository;

import com.vulnaudit.entity.TeamMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 团队成员关联数据访问层接口
 * 
 * 功能说明：
 * - 提供团队成员关联的CRUD操作
 * - 提供团队成员查询相关的自定义方法
 * - 支持团队成员状态管理
 */
@Repository
public interface TeamMemberRepository extends JpaRepository<TeamMember, UUID> {

    /**
     * 根据团队ID查询所有成员关联
     * 用于获取团队的所有成员
     * 
     * @param teamId 团队ID
     * @return 团队成员关联列表
     */
    List<TeamMember> findByTeamId(UUID teamId);

    /**
     * 根据用户ID查询所有团队关联
     * 用于获取用户所属的所有团队
     * 
     * @param userId 用户ID
     * @return 团队成员关联列表
     */
    List<TeamMember> findByUserId(UUID userId);

    /**
     * 根据团队ID和用户ID查询关联
     * 用于验证用户是否在团队中
     * 
     * @param teamId 团队ID
     * @param userId 用户ID
     * @return 团队成员关联（如果存在）
     */
    Optional<TeamMember> findByTeamIdAndUserId(UUID teamId, UUID userId);

    /**
     * 检查用户是否在团队中
     * 
     * @param teamId 团队ID
     * @param userId 用户ID
     * @return 如果用户在团队中返回true，否则返回false
     */
    boolean existsByTeamIdAndUserId(UUID teamId, UUID userId);

    /**
     * 根据团队ID查询团队负责人列表
     * 用于获取团队的所有负责人
     * 
     * @param teamId 团队ID
     * @return 团队负责人关联列表
     */
    List<TeamMember> findByTeamIdAndIsLeaderTrue(UUID teamId);

    /**
     * 根据用户ID查询用户作为负责人的团队
     * 用于获取用户负责的所有团队
     * 
     * @param userId 用户ID
     * @return 团队成员关联列表
     */
    List<TeamMember> findByUserIdAndIsLeaderTrue(UUID userId);

    /**
     * 根据团队ID和状态查询成员
     * 用于筛选团队成员状态
     * 
     * @param teamId 团队ID
     * @param status 成员状态
     * @return 团队成员关联列表
     */
    List<TeamMember> findByTeamIdAndStatus(UUID teamId, String status);

    /**
     * 统计团队成员数量
     * 
     * @param teamId 团队ID
     * @return 成员数量
     */
    long countByTeamId(UUID teamId);

    /**
     * 统计团队中指定状态的成员数量
     * 
     * @param teamId 团队ID
     * @param status 成员状态
     * @return 成员数量
     */
    long countByTeamIdAndStatus(UUID teamId, String status);

    /**
     * 删除用户的所有团队关联
     * 用于用户删除或退出所有团队
     * 
     * @param userId 用户ID
     * @return 删除的记录数
     */
    @Modifying
    @Query("DELETE FROM TeamMember tm WHERE tm.userId = :userId")
    int deleteByUserId(@Param("userId") UUID userId);

    /**
     * 删除团队的所有成员关联
     * 用于团队删除时清理关联
     * 
     * @param teamId 团队ID
     * @return 删除的记录数
     */
    @Modifying
    @Query("DELETE FROM TeamMember tm WHERE tm.teamId = :teamId")
    int deleteByTeamId(@Param("teamId") UUID teamId);

    @Modifying
    @Query("DELETE FROM TeamMember tm WHERE tm.teamId = :teamId AND tm.userId = :userId")
    int deleteByTeamIdAndUserId(@Param("teamId") UUID teamId, @Param("userId") UUID userId);

    /**
     * 根据团队ID查询用户ID列表
     */
    @Query("SELECT tm.userId FROM TeamMember tm WHERE tm.teamId = :teamId")
    List<UUID> findUserIdsByTeamId(@Param("teamId") UUID teamId);

    /**
     * 根据用户ID查询团队ID列表
     */
    @Query("SELECT tm.teamId FROM TeamMember tm WHERE tm.userId = :userId")
    List<UUID> findTeamIdsByUserId(@Param("userId") UUID userId);

}
