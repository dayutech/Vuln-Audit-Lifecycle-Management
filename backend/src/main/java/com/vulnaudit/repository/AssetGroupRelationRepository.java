package com.vulnaudit.repository;

import com.vulnaudit.entity.AssetGroupRelation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 资产分组关联数据访问层接口
 * 
 * 功能说明：
 * - 提供资产分组关联的CRUD操作
 * - 提供关联查询相关的自定义方法
 */
@Repository
public interface AssetGroupRelationRepository extends JpaRepository<AssetGroupRelation, UUID> {

    /**
     * 根据资产ID查询所有分组关联
     * 
     * @param assetId 资产ID
     * @return 分组关联列表
     */
    List<AssetGroupRelation> findByAssetId(UUID assetId);

    /**
     * 根据分组ID查询所有资产关联
     * 
     * @param groupId 分组ID
     * @return 分组关联列表
     */
    List<AssetGroupRelation> findByGroupId(UUID groupId);

    /**
     * 检查资产是否在分组中
     * 
     * @param assetId 资产ID
     * @param groupId 分组ID
     * @return 如果存在关联返回true
     */
    boolean existsByAssetIdAndGroupId(UUID assetId, UUID groupId);

    /**
     * 删除资产的所有分组关联
     * 
     * @param assetId 资产ID
     * @return 删除的记录数
     */
    @Modifying
    @Query("DELETE FROM AssetGroupRelation agr WHERE agr.assetId = :assetId")
    int deleteByAssetId(@Param("assetId") UUID assetId);

    /**
     * 删除分组的所有资产关联
     * 
     * @param groupId 分组ID
     * @return 删除的记录数
     */
    @Modifying
    @Query("DELETE FROM AssetGroupRelation agr WHERE agr.groupId = :groupId")
    int deleteByGroupId(@Param("groupId") UUID groupId);

    /**
     * 根据资产ID查询分组ID列表
     * 
     * @param assetId 资产ID
     * @return 分组ID列表
     */
    @Query("SELECT agr.groupId FROM AssetGroupRelation agr WHERE agr.assetId = :assetId")
    List<UUID> findGroupIdsByAssetId(@Param("assetId") UUID assetId);

    /**
     * 根据分组ID查询资产ID列表
     * 
     * @param groupId 分组ID
     * @return 资产ID列表
     */
    @Query("SELECT agr.assetId FROM AssetGroupRelation agr WHERE agr.groupId = :groupId")
    List<UUID> findAssetIdsByGroupId(@Param("groupId") UUID groupId);

    /**
     * 检查分组中是否包含指定资产
     */
    boolean existsByGroupIdAndAssetId(UUID groupId, UUID assetId);

    /**
     * 删除分组中的指定资产
     */
    @Modifying
    @Query("DELETE FROM AssetGroupRelation agr WHERE agr.groupId = :groupId AND agr.assetId = :assetId")
    int deleteByGroupIdAndAssetId(@Param("groupId") UUID groupId, @Param("assetId") UUID assetId);

}
