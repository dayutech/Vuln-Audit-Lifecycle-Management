package com.vulnaudit.repository;

import com.vulnaudit.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 团队数据访问层接口
 * 
 * 功能说明：
 * - 提供团队实体的CRUD操作
 * - 提供团队查询相关的自定义方法
 * - 支持团队层级结构查询
 */
@Repository
public interface TeamRepository extends JpaRepository<Team, UUID>, JpaSpecificationExecutor<Team> {

    /**
     * 根据团队名称查询团队
     * 
     * @param name 团队名称
     * @return 团队实体（如果存在）
     */
    Optional<Team> findByName(String name);

    /**
     * 根据团队负责人ID查询团队列表
     * 用于获取用户负责的所有团队
     * 
     * @param leaderId 负责人ID
     * @return 团队列表
     */
    List<Team> findByLeaderId(UUID leaderId);

    /**
     * 根据上级团队ID查询子团队列表
     * 用于获取团队的下级团队
     * 
     * @param parentId 上级团队ID
     * @return 子团队列表
     */
    List<Team> findByParentId(UUID parentId);

    /**
     * 查询所有顶级团队
     * 用于获取没有上级团队的团队列表
     * 
     * @return 顶级团队列表
     */
    @Query("SELECT t FROM Team t WHERE t.parentId IS NULL")
    List<Team> findTopLevelTeams();

    /**
     * 根据创建者ID查询团队列表
     * 用于获取用户创建的所有团队
     * 
     * @param createdBy 创建者ID
     * @return 团队列表
     */
    List<Team> findByCreatedBy(UUID createdBy);

    /**
     * 检查团队名称是否已存在
     * 
     * @param name 团队名称
     * @return 如果团队名称已存在返回true，否则返回false
     */
    boolean existsByName(String name);

    List<Team> findByParentIdIsNull();

    /**
     * 统计团队下的子团队数量
     * 
     * @param parentId 上级团队ID
     * @return 子团队数量
     */
    long countByParentId(UUID parentId);

    /**
     * 递归查询团队及其所有子团队ID
     * 用于权限检查和资源关联
     * 
     * @param teamId 团队ID
     * @return 团队及其所有子团队的ID列表
     */
    @Query(value = """
        WITH RECURSIVE team_tree AS (
            SELECT id FROM teams WHERE id = :teamId
            UNION ALL
            SELECT t.id FROM teams t
            INNER JOIN team_tree tt ON t.parent_id = tt.id
        )
        SELECT id FROM team_tree
        """, nativeQuery = true)
    List<UUID> findAllDescendantIds(@Param("teamId") UUID teamId);

}
