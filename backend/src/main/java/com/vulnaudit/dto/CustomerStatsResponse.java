package com.vulnaudit.dto;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 客户统计视图（t70）
 *
 * <p><b>计数口径差异（重要）</b>：
 * <ul>
 *   <li>项目类计数（{@code projectCount} / {@code activeProjectCount} / {@code phasedProjectCount} /
 *       {@code byStatus}）反映「组织结构事实」，<b>不随调用者资产可见范围变化</b>——项目本身对所有登录用户可见；</li>
 *   <li>资产/漏洞类计数（{@code assetCount} / {@code distinctAssetCount} / {@code vulnerabilityCount}）
 *       按 {@code AccessGuard.visibleAssetIds} <b>收敛</b>：非管理层只统计自己可见的资产与漏洞，管理层全量。</li>
 * </ul>
 *
 * @param projectCount       项目总数
 * @param activeProjectCount 进行中（ACTIVE）项目数
 * @param phasedProjectCount 启用分期的项目数
 * @param assetCount         该客户下资产挂靠记录数（含跨期次重复挂靠）
 * @param distinctAssetCount 该客户下去重资产数（可见范围内）
 * @param vulnerabilityCount 该客户下资产关联的漏洞数（可见范围内）
 * @param byStatus           项目状态分布（DRAFT/ACTIVE/PAUSED/CLOSED）
 */
public record CustomerStatsResponse(
        long projectCount,
        long activeProjectCount,
        long phasedProjectCount,
        long assetCount,
        long distinctAssetCount,
        long vulnerabilityCount,
        Map<String, Long> byStatus) {

    /**
     * 期次级资产分布条目
     *
     * @param phaseId    期次ID
     * @param phaseName  期次名称
     * @param assetCount 该期次下的去重资产数（可见范围内）
     */
    public record PhaseAssetCount(UUID phaseId, String phaseName, long assetCount) {
    }

    /** 便于前端消费的空期次列表占位（保持结构稳定） */
    public static List<PhaseAssetCount> emptyPhases() {
        return List.of();
    }
}
