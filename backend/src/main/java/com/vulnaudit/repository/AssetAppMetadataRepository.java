package com.vulnaudit.repository;

import com.vulnaudit.entity.AssetAppMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * 资产应用元数据数据访问层接口
 * 
 * 功能说明：
 * - 提供元数据实体的CRUD操作
 * - 提供元数据查询相关的自定义方法
 */
@Repository
public interface AssetAppMetadataRepository extends JpaRepository<AssetAppMetadata, UUID> {

    /**
     * 根据资产ID查询元数据
     * 
     * @param assetId 资产ID
     * @return 元数据实体（如果存在）
     */
    Optional<AssetAppMetadata> findByAssetId(UUID assetId);

    Optional<AssetAppMetadata> findByAssetIdAndAssetVersionId(UUID assetId, UUID assetVersionId);

    /**
     * 根据认领人ID查询元数据列表
     * 
     * @param claimedBy 认领人ID
     * @return 元数据列表
     */
    java.util.List<AssetAppMetadata> findByClaimedBy(UUID claimedBy);

    /**
     * 检查资产是否有元数据
     * 
     * @param assetId 资产ID
     * @return 如果存在返回true
     */
    boolean existsByAssetId(UUID assetId);

    boolean existsByAssetIdAndAssetVersionId(UUID assetId, UUID assetVersionId);

}
