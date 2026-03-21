package com.vulnaudit.repository;

import com.vulnaudit.entity.AssetFile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 资产文件数据访问层接口
 * 
 * 功能说明：
 * - 提供资产文件实体的CRUD操作
 * - 提供文件查询相关的自定义方法
 */
@Repository
public interface AssetFileRepository extends JpaRepository<AssetFile, UUID> {

    /**
     * 根据资产ID查询所有文件
     * 
     * @param assetId 资产ID
     * @return 文件列表
     */
    List<AssetFile> findByAssetId(UUID assetId);

    /**
     * 根据资产ID和状态查询文件
     * 
     * @param assetId 资产ID
     * @param status 状态
     * @return 文件列表
     */
    List<AssetFile> findByAssetIdAndStatus(UUID assetId, String status);

    /**
     * 根据资产ID和文件类型查询文件
     * 
     * @param assetId 资产ID
     * @param fileType 文件类型
     * @return 文件列表
     */
    List<AssetFile> findByAssetIdAndFileType(UUID assetId, String fileType);

    /**
     * 根据资产ID和分类查询文件
     * 
     * @param assetId 资产ID
     * @param category 分类
     * @return 文件列表
     */
    List<AssetFile> findByAssetIdAndCategory(UUID assetId, String category);

    /**
     * 根据上传人ID查询文件
     * 
     * @param uploadedBy 上传人ID
     * @return 文件列表
     */
    List<AssetFile> findByUploadedBy(UUID uploadedBy);

    /**
     * 统计资产文件数量
     * 
     * @param assetId 资产ID
     * @return 文件数量
     */
    long countByAssetId(UUID assetId);

    /**
     * 删除资产的所有文件
     * 
     * @param assetId 资产ID
     * @return 删除的记录数
     */
    @Modifying
    @Query("DELETE FROM AssetFile af WHERE af.assetId = :assetId")
    int deleteByAssetId(@Param("assetId") UUID assetId);

    List<AssetFile> findByAssetIdOrderByCreatedAtDesc(UUID assetId);

}
