package com.vulnaudit.repository;

import com.vulnaudit.entity.AssetTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 资产标签数据访问层接口
 * 
 * 功能说明：
 * - 提供资产标签实体的CRUD操作
 * - 提供标签查询相关的自定义方法
 */
@Repository
public interface AssetTagRepository extends JpaRepository<AssetTag, UUID> {

    /**
     * 根据资产ID查询所有标签
     * 
     * @param assetId 资产ID
     * @return 标签列表
     */
    List<AssetTag> findByAssetId(UUID assetId);

    /**
     * 根据标签名称查询所有关联
     * 
     * @param tag 标签名称
     * @return 标签列表
     */
    List<AssetTag> findByTag(String tag);

    /**
     * 检查资产是否有指定标签
     * 
     * @param assetId 资产ID
     * @param tag 标签名称
     * @return 如果存在返回true
     */
    boolean existsByAssetIdAndTag(UUID assetId, String tag);

    /**
     * 删除资产的所有标签
     * 
     * @param assetId 资产ID
     * @return 删除的记录数
     */
    @Modifying
    @Query("DELETE FROM AssetTag at WHERE at.assetId = :assetId")
    int deleteByAssetId(@Param("assetId") UUID assetId);

    /**
     * 删除资产的指定标签
     * 
     * @param assetId 资产ID
     * @param tag 标签名称
     * @return 删除的记录数
     */
    @Modifying
    @Query("DELETE FROM AssetTag at WHERE at.assetId = :assetId AND at.tag = :tag")
    int deleteByAssetIdAndTag(@Param("assetId") UUID assetId, @Param("tag") String tag);

    /**
     * 根据资产ID查询标签名称列表
     * 
     * @param assetId 资产ID
     * @return 标签名称列表
     */
    @Query("SELECT at.tag FROM AssetTag at WHERE at.assetId = :assetId")
    List<String> findTagsByAssetId(@Param("assetId") UUID assetId);

    /**
     * 查询所有不重复的标签
     * 
     * @return 标签名称列表
     */
    @Query("SELECT DISTINCT at.tag FROM AssetTag at ORDER BY at.tag")
    List<String> findAllDistinctTags();

}
