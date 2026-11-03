package com.vulnaudit.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 项目列表聚合项（t82）
 *
 * <p>一次请求拿到「项目本体 + 三个计数 + 经理/客户名」，取代前端原来的
 * 「先 GET /projects 拿列表 → 再对每个项目 GET /projects/{id} 取期次/资产数」（每页最多 20 个并发请求）。
 *
 * <p><b>口径差异（重要，勿混用）</b>：
 * <ul>
 *   <li>{@code phaseCount}：**不收敛** —— 期次是组织结构事实，对所有登录用户可见；</li>
 *   <li>{@code assetCount}：**按 {@code AccessGuard.visibleAssetIds(auth)} 收敛** —— 资产是权限边界，
 *       与 {@code GET /assets}、{@code GET /customers/{id}/summary} 的资产口径一致；</li>
 *   <li>{@code openTaskCount}：随 {@code assetCount} 的口径（任务挂在资产上，资产不可见则计数不可见）。</li>
 * </ul>
 *
 * @param phaseCount    期次数（全量口径）
 * @param assetCount    去重资产数（**可见范围**）
 * @param openTaskCount 未完成任务数（可见范围；项目下资产关联的任务，排除 completed/cancelled）
 */
public record ProjectSummaryItem(
        UUID id,
        String code,
        String name,
        UUID customerId,
        String customerName,
        String type,
        String status,
        UUID managerId,
        String managerName,
        LocalDate startDate,
        LocalDate endDate,
        Boolean isPhased,
        String contractNo,
        String description,
        long phaseCount,
        long assetCount,
        long openTaskCount,
        UUID createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
