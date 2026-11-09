package com.vulnaudit.dto;

import java.util.List;
import java.util.UUID;

/**
 * 客户详情聚合视图（t76）
 *
 * <p>把「客户本体 + 统计 + 项目列表（含期次数/资产数）+ 最近资产」一次性返回，
 * 取代前端原来的 N+1 调用链：{@code GET /projects?customerId=} 再对每个项目 {@code GET /projects/{id}}。
 *
 * <p><b>数据边界（重要）</b>：
 * <ul>
 *   <li>{@code stats.assetCount} 与 {@code recentAssets} 按 {@code AccessGuard.visibleAssetIds(auth)} 收敛
 *       —— 资产是权限边界，非管理层只能看到自己可见的资产（与 {@code GET /assets} 列表口径一致）；</li>
 *   <li>{@code projects} 列表与每个项目的 {@code phaseCount} / {@code assetCount} <b>不</b>做收敛
 *       —— 客户与项目是组织维度（与 {@code GET /projects} 读口径一致），项目级的资产数用全量口径，
 *       否则「客户下有几项目、每项目几期」这类组织信息会对非管理层失真。</li>
 * </ul>
 */
public record CustomerSummaryResponse(
        CustomerResponse customer,
        Stats stats,
        List<ProjectSummary> projects,
        List<RecentAsset> recentAssets) {

    /**
     * 统计块
     *
     * @param projectCount       项目总数（组织维度，不受可见性影响）
     * @param activeProjectCount 进行中（ACTIVE）项目数
     * @param phasedProjectCount 启用分期的项目数
     * @param assetCount         客户下去重资产数（**按可见范围收敛**）
     * @param vulnerabilityCount 客户下资产关联的漏洞数（**按可见范围收敛**）
     */
    public record Stats(
            long projectCount,
            long activeProjectCount,
            long phasedProjectCount,
            long assetCount,
            long vulnerabilityCount) {
    }

    /**
     * 项目摘要（含两个聚合计数，均由 group by 一次算出）
     *
     * @param phaseCount 期次数（全量口径）
     * @param assetCount 该项目下去重资产数（全量口径）
     */
    public record ProjectSummary(
            UUID id,
            String code,
            String name,
            String type,
            String status,
            Boolean isPhased,
            long phaseCount,
            long assetCount,
            String managerName) {
    }

    /**
     * 最近资产摘要
     *
     * @param projectNames 该资产在本客户下的项目名（可多个；跨客户挂靠只取本客户的项目）
     */
    public record RecentAsset(
            UUID id,
            String code,
            String name,
            String targetType,
            String status,
            List<String> projectNames,
            /** t145：标签摘要（与资产列表同一批查询口径） */
            List<com.vulnaudit.dto.AssetTagBrief> tags) {
    }
}
