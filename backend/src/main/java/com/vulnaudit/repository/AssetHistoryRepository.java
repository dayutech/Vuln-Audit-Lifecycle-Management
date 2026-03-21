package com.vulnaudit.repository;

import com.vulnaudit.entity.AssetHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 资产历史数据访问层接口
 * 
 * 功能说明：
 * - 提供资产历史实体的CRUD操作
 * - 提供历史查询相关的自定义方法
 */
@Repository
public interface AssetHistoryRepository extends JpaRepository<AssetHistory, UUID> {

    /**
     * 根据资产ID查询所有历史记录
     * 
     * @param assetId 资产ID
     * @return 历史记录列表（按时间倒序）
     */
    @Query("SELECT ah FROM AssetHistory ah WHERE ah.assetId = :assetId ORDER BY ah.createdAt DESC")
    List<AssetHistory> findByAssetIdOrderByCreatedAtDesc(@Param("assetId") UUID assetId);

    /**
     * 根据资产ID和字段名查询历史记录
     * 
     * @param assetId 资产ID
     * @param fieldName 字段名
     * @return 历史记录列表
     */
    List<AssetHistory> findByAssetIdAndFieldName(UUID assetId, String fieldName);

    /**
     * 根据变更人ID查询历史记录
     * 
     * @param changedBy 变更人ID
     * @return 历史记录列表
     */
    List<AssetHistory> findByChangedBy(UUID changedBy);

    /**
     * 统计资产的变更次数
     * 
     * @param assetId 资产ID
     * @return 变更次数
     */
    long countByAssetId(UUID assetId);

}
