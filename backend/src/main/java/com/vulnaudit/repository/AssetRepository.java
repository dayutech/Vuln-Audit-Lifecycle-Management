package com.vulnaudit.repository;

import com.vulnaudit.entity.Asset;
import jakarta.annotation.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 资产数据访问层接口
 *
 * 功能说明：
 * - 提供资产实体的CRUD操作
 * - 提供资产查询相关的自定义方法
 * - 支持复杂条件查询
 */
@Repository
public interface AssetRepository extends JpaRepository<Asset, UUID>, JpaSpecificationExecutor<Asset> {

    /**
     * 根据资产编号查询资产
     *
     * @param code 资产编号
     * @return 资产实体（如果存在）
     */
    Optional<Asset> findByCode(@Nullable String code);

    /**
     * 根据团队ID查询资产列表
     *
     * @param teamId 团队ID
     * @return 资产列表
     */
    List<Asset> findByTeamId(@Nullable UUID teamId);

    /**
     * 根据负责人ID查询资产列表
     *
     * @param ownerId 负责人ID
     * @return 资产列表
     */
    List<Asset> findByOwnerId(@Nullable UUID ownerId);

    /**
     * 根据状态查询资产列表
     *
     * @param status 状态
     * @return 资产列表
     */
    List<Asset> findByStatus(@Nullable String status);

    /**
     * 根据优先级查询资产列表
     *
     * @param priority 优先级
     * @return 资产列表
     */
    List<Asset> findByPriority(@Nullable String priority);

    /**
     * 根据团队ID和状态查询资产列表
     *
     * @param teamId 团队ID
     * @param status 状态
     * @return 资产列表
     */
    List<Asset> findByTeamIdAndStatus(@Nullable UUID teamId, @Nullable String status);

    /**
     * 检查资产编号是否已存在
     *
     * @param code 资产编号
     * @return 如果已存在返回true
     */
    boolean existsByCode(@Nullable String code);

    /**
     * 统计团队下的资产数量
     * 
     * @param teamId 团队ID
     * @return 资产数量
     */
    long countByTeamId(UUID teamId);

    /**
     * 统计指定状态的资产数量
     * 
     * @param status 状态
     * @return 资产数量
     */
    long countByStatus(String status);

    @Query("SELECT DISTINCT a.targetType FROM Asset a WHERE a.targetType IS NOT NULL AND a.targetType <> '' ORDER BY a.targetType ASC")
    List<String> findDistinctTargetTypes();

    @Query("SELECT DISTINCT a.targetSource FROM Asset a WHERE a.targetSource IS NOT NULL AND a.targetSource <> '' ORDER BY a.targetSource ASC")
    List<String> findDistinctTargetSources();

}
