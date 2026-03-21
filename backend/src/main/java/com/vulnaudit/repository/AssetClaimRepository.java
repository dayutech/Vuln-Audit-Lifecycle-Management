package com.vulnaudit.repository;

import com.vulnaudit.entity.AssetClaim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 资产认领数据访问层接口
 * 
 * 功能说明：
 * - 提供资产认领实体的CRUD操作
 * - 提供认领查询相关的自定义方法
 */
@Repository
public interface AssetClaimRepository extends JpaRepository<AssetClaim, UUID>, JpaSpecificationExecutor<AssetClaim> {

    /**
     * 根据资产ID查询所有认领记录
     * 
     * @param assetId 资产ID
     * @return 认领记录列表
     */
    List<AssetClaim> findByAssetId(UUID assetId);

    /**
     * 根据用户ID查询所有认领记录
     * 
     * @param userId 用户ID
     * @return 认领记录列表
     */
    List<AssetClaim> findByUserId(UUID userId);

    /**
     * 根据资产ID和用户ID查询认领记录
     * 
     * @param assetId 资产ID
     * @param userId 用户ID
     * @return 认领记录（如果存在）
     */
    Optional<AssetClaim> findByAssetIdAndUserId(UUID assetId, UUID userId);

    /**
     * 根据资产ID和状态查询认领记录
     * 
     * @param assetId 资产ID
     * @param status 状态
     * @return 认领记录列表
     */
    List<AssetClaim> findByAssetIdAndStatus(UUID assetId, String status);

    /**
     * 根据用户ID和状态查询认领记录
     * 
     * @param userId 用户ID
     * @param status 状态
     * @return 认领记录列表
     */
    List<AssetClaim> findByUserIdAndStatus(UUID userId, String status);

    /**
     * 检查资产是否有待审核的认领申请
     * 
     * @param assetId 资产ID
     * @return 如果存在返回true
     */
    boolean existsByAssetIdAndStatus(UUID assetId, String status);

    /**
     * 检查用户是否有资产的活跃认领（approved状态）
     * 
     * @param assetId 资产ID
     * @param userId 用户ID
     * @param status 状态
     * @return 如果存在返回true
     */
    boolean existsByAssetIdAndUserIdAndStatus(UUID assetId, UUID userId, String status);

    /**
     * 查询资产的当前活跃认领（approved状态）
     * 
     * @param assetId 资产ID
     * @return 认领记录（如果存在）
     */
    Optional<AssetClaim> findByAssetIdAndStatusOrderByCreatedAtDesc(UUID assetId, String status);

    /**
     * 统计用户指定状态的认领数量
     * 
     * @param userId 用户ID
     * @param status 状态
     * @return 认领数量
     */
    long countByUserIdAndStatus(UUID userId, String status);

    /**
     * 删除资产的所有认领记录
     * 
     * @param assetId 资产ID
     * @return 删除的记录数
     */
    @Modifying
    @Query("DELETE FROM AssetClaim ac WHERE ac.assetId = :assetId")
    int deleteByAssetId(@Param("assetId") UUID assetId);

}
