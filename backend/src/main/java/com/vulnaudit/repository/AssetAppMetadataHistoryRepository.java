package com.vulnaudit.repository;

import com.vulnaudit.entity.AssetAppMetadataHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 资产应用元数据历史数据访问层接口
 * 
 * 功能说明：
 * - 提供元数据历史实体的CRUD操作
 * - 提供历史查询相关的自定义方法
 */
@Repository
public interface AssetAppMetadataHistoryRepository extends JpaRepository<AssetAppMetadataHistory, UUID> {

    /**
     * 根据元数据ID查询所有历史记录
     * 
     * @param metadataId 元数据ID
     * @return 历史记录列表（按版本号倒序）
     */
    @Query("SELECT h FROM AssetAppMetadataHistory h WHERE h.metadataId = :metadataId ORDER BY h.version DESC")
    List<AssetAppMetadataHistory> findByMetadataIdOrderByVersionDesc(@Param("metadataId") UUID metadataId);

    /**
     * 根据元数据ID和版本号查询历史记录
     * 
     * @param metadataId 元数据ID
     * @param version 版本号
     * @return 历史记录（如果存在）
     */
    AssetAppMetadataHistory findByMetadataIdAndVersion(UUID metadataId, Integer version);

    /**
     * 根据修改人ID查询历史记录
     * 
     * @param changedBy 修改人ID
     * @return 历史记录列表
     */
    List<AssetAppMetadataHistory> findByChangedBy(UUID changedBy);

    /**
     * 统计元数据的变更次数
     * 
     * @param metadataId 元数据ID
     * @return 变更次数
     */
    long countByMetadataId(UUID metadataId);

}
