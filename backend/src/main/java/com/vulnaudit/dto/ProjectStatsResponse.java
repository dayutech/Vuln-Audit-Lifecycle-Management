package com.vulnaudit.dto;

import java.util.List;
import java.util.UUID;

/**
 * 项目统计视图（t70）
 *
 * <p>计数口径与 {@link CustomerStatsResponse} 一致：期次类计数（{@code phaseCount}）与项目结构有关、不收敛；
 * 资产/漏洞/任务类计数按 {@code AccessGuard.visibleAssetIds} 收敛（非管理层只看自己可见资产）。
 *
 * @param phaseCount         期次数
 * @param assetCount         该项目下去重资产数（可见范围内）
 * @param vulnerabilityCount 该项目下资产关联的漏洞数（可见范围内）
 * @param taskCount          该项目下资产关联的任务数（可见范围内）
 * @param byPhase            按期次的资产分布（仅 isPhased 项目有意义；无期次时为空数组）
 */
public record ProjectStatsResponse(
        long phaseCount,
        long assetCount,
        long vulnerabilityCount,
        long taskCount,
        List<CustomerStatsResponse.PhaseAssetCount> byPhase) {

    /**
     * 仪表盘「客户分布」条目（t70）
     *
     * @param customerId   客户ID
     * @param customerCode 客户编码
     * @param customerName 客户名称
     * @param assetCount   可见范围内的去重资产数
     * @param projectCount 项目数（结构事实，不收敛）
     */
    public record CustomerDistributionItem(
            UUID customerId,
            String customerCode,
            String customerName,
            long assetCount,
            long projectCount) {
    }

    /**
     * 仪表盘「项目分布」条目（t70）
     *
     * @param projectId   项目ID
     * @param projectCode 项目编码
     * @param projectName 项目名称
     * @param customerId  所属客户
     * @param status      项目状态
     * @param assetCount  可见范围内的去重资产数
     */
    public record ProjectDistributionItem(
            UUID projectId,
            String projectCode,
            String projectName,
            UUID customerId,
            String status,
            long assetCount) {
    }
}
