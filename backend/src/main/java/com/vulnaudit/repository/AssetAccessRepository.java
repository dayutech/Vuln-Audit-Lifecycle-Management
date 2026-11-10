package com.vulnaudit.repository;

import com.vulnaudit.entity.AssetAccess;
import jakarta.annotation.Nullable;
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
 * 资产访问申请数据访问层接口
 *
 * 功能说明：
 * - 提供资产访问申请实体的CRUD操作
 * - 提供访问申请查询相关的自定义方法
 */
@Repository
public interface AssetAccessRepository extends JpaRepository<AssetAccess, UUID>, JpaSpecificationExecutor<AssetAccess> {

    /**
     * 根据资产ID查询所有访问申请
     *
     * @param assetId 资产ID
     * @return 访问申请列表
     */
    List<AssetAccess> findByAssetId(@Nullable UUID assetId);

    List<AssetAccess> findByStatus(@Nullable String status);

    /**
     * 根据用户ID查询所有访问申请
     *
     * @param userId 用户ID
     * @return 访问申请列表
     */
    List<AssetAccess> findByUserId(@Nullable UUID userId);

    /**
     * 根据资产ID和用户ID查询访问申请
     *
     * @param assetId 资产ID
     * @param userId 用户ID
     * @return 访问申请（如果存在）
     */
    Optional<AssetAccess> findByAssetIdAndUserId(@Nullable UUID assetId, @Nullable UUID userId);

    /**
     * 根据资产ID和状态查询访问申请
     *
     * @param assetId 资产ID
     * @param status 状态
     * @return 访问申请列表
     */
    List<AssetAccess> findByAssetIdAndStatus(@Nullable UUID assetId, @Nullable String status);

    /**
     * 根据用户ID和状态查询访问申请
     *
     * @param userId 用户ID
     * @param status 状态
     * @return 访问申请列表
     */
    List<AssetAccess> findByUserIdAndStatus(@Nullable UUID userId, @Nullable String status);

    /**
     * 检查用户是否有资产的已批准访问权限
     *
     * @param assetId 资产ID
     * @param userId 用户ID
     * @param status 状态
     * @return 如果存在返回true
     */
    boolean existsByAssetIdAndUserIdAndStatus(@Nullable UUID assetId, @Nullable UUID userId, @Nullable String status);

    /**
     * 查询用户对资产的有效访问权限
     * 
     * @param assetId 资产ID
     * @param userId 用户ID
     * @param status 状态
     * @return 访问申请（如果存在）
     */
    Optional<AssetAccess> findByAssetIdAndUserIdAndStatus(UUID assetId, UUID userId, String status);

    /**
     * 统计用户指定状态的访问申请数量
     * 
     * @param userId 用户ID
     * @param status 状态
     * @return 访问申请数量
     */
    long countByUserIdAndStatus(UUID userId, String status);

    /**
     * 删除资产的所有访问申请
     * 
     * @param assetId 资产ID
     * @return 删除的记录数
     */
    @Modifying
    @Query("DELETE FROM AssetAccess aa WHERE aa.assetId = :assetId")
    int deleteByAssetId(@Param("assetId") UUID assetId);

    /** t156：审批人视角的历史（只含已处理） */
    @Query("select a from AssetAccess a where a.reviewedBy = :userId "
            + "and a.status in :statuses "
            + "and (:assetId is null or a.assetId = :assetId) "
            + "and (:restrictAssets = false or a.assetId in :assetIds)")
    org.springframework.data.domain.Page<AssetAccess> findHistoryAsReviewer(
            @Param("userId") UUID userId,
            @Param("statuses") java.util.Collection<String> statuses,
            @Param("assetId") UUID assetId,
            @Param("restrictAssets") boolean restrictAssets,
            @Param("assetIds") java.util.Collection<UUID> assetIds,
            org.springframework.data.domain.Pageable pageable);

    /** t156：申请人视角的历史（我提交过、且已被处理） */
    @Query("select a from AssetAccess a where a.userId = :userId "
            + "and a.status in :statuses "
            + "and (:assetId is null or a.assetId = :assetId) "
            + "and (:restrictAssets = false or a.assetId in :assetIds)")
    org.springframework.data.domain.Page<AssetAccess> findHistoryAsRequester(
            @Param("userId") UUID userId,
            @Param("statuses") java.util.Collection<String> statuses,
            @Param("assetId") UUID assetId,
            @Param("restrictAssets") boolean restrictAssets,
            @Param("assetIds") java.util.Collection<UUID> assetIds,
            org.springframework.data.domain.Pageable pageable);

    /** t156：角标计数（一次分组，避免前端全量拉取） */
    @Query("select a.status, count(a) from AssetAccess a "
            + "where a.reviewedBy = :userId and a.status in ('approved','rejected') group by a.status")
    java.util.List<Object[]> countHistoryAsReviewer(@Param("userId") UUID userId);

    @Query("select a.status, count(a) from AssetAccess a "
            + "where a.userId = :userId and a.status in ('approved','rejected') group by a.status")
    java.util.List<Object[]> countHistoryAsRequester(@Param("userId") UUID userId);
}
