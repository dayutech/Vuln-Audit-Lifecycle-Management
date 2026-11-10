package com.vulnaudit.repository;

import com.vulnaudit.entity.AssetProjectLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 资产—项目挂靠仓储（t67）
 *
 * <p>注意 {@code phaseId} 可空：{@code findByProjectIdAndPhaseId(projectId, null)} 需要
 * 「期次为空」的语义，因此用显式 JPQL（{@code phase_id IS NULL}），不能依赖派生查询的
 * {@code = NULL}（永远不成立）。
 */
@Repository
public interface AssetProjectLinkRepository extends JpaRepository<AssetProjectLink, UUID> {

    List<AssetProjectLink> findByAssetId(UUID assetId);

    List<AssetProjectLink> findByProjectId(UUID projectId);

    List<AssetProjectLink> findByCustomerId(UUID customerId);

    /** phaseId 为 null 时按「项目级挂靠（无期次）」匹配 */
    @Query("select l from AssetProjectLink l where l.projectId = :projectId "
            + "and ((:phaseId is null and l.phaseId is null) or l.phaseId = :phaseId)")
    List<AssetProjectLink> findByProjectIdAndPhaseId(@Param("projectId") UUID projectId,
                                                     @Param("phaseId") UUID phaseId);

    /** 同 asset + project + phase 是否已存在（phaseId 为 null 表示项目级） */
    @Query("select count(l) > 0 from AssetProjectLink l where l.assetId = :assetId "
            + "and l.projectId = :projectId "
            + "and ((:phaseId is null and l.phaseId is null) or l.phaseId = :phaseId)")
    boolean existsByAssetIdAndProjectIdAndPhaseId(@Param("assetId") UUID assetId,
                                                  @Param("projectId") UUID projectId,
                                                  @Param("phaseId") UUID phaseId);

    Optional<AssetProjectLink> findByAssetIdAndProjectIdAndPhaseKey(UUID assetId, UUID projectId, UUID phaseKey);

    long countByProjectId(UUID projectId);

    long countByAssetId(UUID assetId);

    // ------------------------------------------------------------------ t69

    /** 按期次查挂靠（只给 phaseId 筛选时用） */
    List<AssetProjectLink> findByPhaseId(UUID phaseId);

    /** 批量取挂靠：列表接口一次查询组装客户/项目摘要，避免 N+1 */
    List<AssetProjectLink> findByAssetIdIn(Collection<UUID> assetIds);

    /** 项目下资产分页（**仅**项目级挂靠，即 phaseId 为空），按挂靠时间倒序 */
    @Query("select l from AssetProjectLink l where l.projectId = :projectId "
            + "and ((:phaseId is null and l.phaseId is null) or l.phaseId = :phaseId) "
            + "order by l.linkedAt desc")
    org.springframework.data.domain.Page<AssetProjectLink> findPageByProjectAndPhase(
            @Param("projectId") UUID projectId,
            @Param("phaseId") UUID phaseId,
            org.springframework.data.domain.Pageable pageable);

    /**
     * 项目下**全部**挂靠（含项目级与各期次），按挂靠时间倒序。
     *
     * <p>与 {@link #findPageByProjectAndPhase} 的区别：后者在 phaseId 为空时只匹配「项目级」，
     * 而「查项目全部资产」需要把期次挂靠也算进来。
     */
    org.springframework.data.domain.Page<AssetProjectLink> findByProjectIdOrderByLinkedAtDesc(
            UUID projectId, org.springframework.data.domain.Pageable pageable);

    // ------------------------------------------------------------------ t76（客户详情聚合）

    /**
     * 批量统计各项目下挂靠的<b>去重资产数</b>（一次 group by）
     *
     * @return 每行 {@code [projectId(UUID), count(Long)]}；无挂靠的项目不出现
     */
    @Query("select l.projectId, count(distinct l.assetId) from AssetProjectLink l "
            + "where l.projectId in :projectIds "
            + "and l.assetId in (select a.id from Asset a) "
            + "group by l.projectId")
    List<Object[]> countAssetsGroupedByProjectIds(@Param("projectIds") Collection<UUID> projectIds);

    /** 客户下的全部挂靠（客户详情聚合用；按挂靠时间倒序，便于取「最近资产」） */
    List<AssetProjectLink> findByCustomerIdOrderByLinkedAtDesc(UUID customerId);

    // ------------------------------------------------------------------ t82（项目列表聚合）

    /**
     * 批量统计各项目下的去重资产数，**按可见资产收敛**（一次 group by）
     *
     * <p>与 {@link #countAssetsGroupedByProjectIds} 的区别：多一个可见范围条件。
     * {@code visibleAssetIds == null} 表示不限制（管理层）；调用方必须保证非空集合
     * （空集合走「返回全 0」的分支，避免生成非法的 {@code in ()}）。
     *
     * @return 每行 {@code [projectId(UUID), count(Long)]}
     */
    @Query("select l.projectId, count(distinct l.assetId) from AssetProjectLink l "
            + "where l.projectId in :projectIds "
            + "and (:visibleAssetIds is null or l.assetId in :visibleAssetIds) "
            + "and l.assetId in (select a.id from Asset a) "
            + "group by l.projectId")
    List<Object[]> countVisibleAssetsGroupedByProjectIds(
            @Param("projectIds") Collection<UUID> projectIds,
            @Param("visibleAssetIds") Collection<UUID> visibleAssetIds);

    /**
     * 项目下「未被软删的资产」挂靠数（t140）—— 删除项目的阻塞检查
     *
     * <p>口径：**只有明确软删（assets.deleted_at 非空）的资产不算阻塞**；资产行不存在（历史脏数据/夹具）
     * 一律按「存在」处理，宁可拦住删除也不放过脏数据。用原生 SQL，因为需要绕开实体上的
     * {@code @SQLRestriction("deleted_at IS NULL")}（它会隐藏已删资产，导致脏数据看起来「不阻塞」）。
     */
    @Query(value = "select count(*) from asset_project_links l where l.project_id = :projectId "
            + "and not exists (select 1 from assets a where a.id = l.asset_id and a.deleted_at is not null)",
            nativeQuery = true)
    long countByProjectIdAndAssetNotDeleted(@Param("projectId") UUID projectId);

    /** t154：一次分组查询取多个资产的挂靠数（可选资产列表用，禁止 per-asset 查询） */
    @Query("select l.assetId, count(l) from AssetProjectLink l where l.assetId in :assetIds group by l.assetId")
    List<Object[]> countLinksGroupedByAssetIds(@Param("assetIds") java.util.Collection<UUID> assetIds);
}
