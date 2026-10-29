package com.vulnaudit.service;

import com.vulnaudit.entity.DashboardConfig;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 仪表盘服务接口
 *
 * <p>趋势与排行口径（详见 {@code docs/design/dashboard.md}）：
 * <ul>
 *   <li>趋势返回 {@code {days, startDate, endDate, data:[{date:"yyyy-MM-dd", count:N}]}}，
 *       无数据的天补 0，保证曲线连续；</li>
 *   <li>{@code assets} / {@code vulnerabilities} 按各自 {@code created_at} 归日；</li>
 *   <li>{@code workload} 按任务的「完成时间」归日：优先 {@code completed_at}，为空时回退 {@code updated_at}，
 *       且只统计 {@code status=completed} 的任务；</li>
 *   <li>排行统计窗口内「活动时间」落在窗口内的任务，跨用户聚合任务数与工时，按任务数倒序。</li>
 * </ul>
 */
public interface DashboardService {

    @Nonnull
    Map<String, Object> getAssetStats();

    @Nonnull
    Map<String, Long> getAssetCountByStatus();

    @Nonnull
    Map<String, Long> getAssetCountByType();

    @Nonnull
    Map<String, Long> getAssetCountByPriority();

    @Nonnull
    Map<String, Object> getVulnerabilityStats();

    @Nonnull
    Map<String, Long> getVulnerabilityCountByWorkflowStatus();

    @Nonnull
    Map<String, Long> getVulnerabilityCountByCustomerStatus();

    @Nonnull
    Map<String, Long> getVulnerabilityCountByDisclosureStatus();

    @Nonnull
    Map<String, Long> getVulnerabilityCountBySeverity();

    // ---------------- 带可见资产范围的统计（null = 管理层看全量） ----------------

    @Nonnull
    Map<String, Object> getAssetStats(@Nullable Set<UUID> allowedAssetIds);

    @Nonnull
    Map<String, Long> getAssetCountByStatus(@Nullable Set<UUID> allowedAssetIds);

    @Nonnull
    Map<String, Long> getAssetCountByType(@Nullable Set<UUID> allowedAssetIds);

    @Nonnull
    Map<String, Long> getAssetCountByPriority(@Nullable Set<UUID> allowedAssetIds);

    @Nonnull
    Map<String, Object> getVulnerabilityStats(@Nullable Set<UUID> allowedAssetIds);

    @Nonnull
    Map<String, Long> getVulnerabilityCountByWorkflowStatus(@Nullable Set<UUID> allowedAssetIds);

    @Nonnull
    Map<String, Long> getVulnerabilityCountByCustomerStatus(@Nullable Set<UUID> allowedAssetIds);

    @Nonnull
    Map<String, Long> getVulnerabilityCountByDisclosureStatus(@Nullable Set<UUID> allowedAssetIds);

    @Nonnull
    Map<String, Long> getVulnerabilityCountBySeverity(@Nullable Set<UUID> allowedAssetIds);

    @Nonnull
    Map<String, Object> getDashboardOverview(@Nullable Set<UUID> allowedAssetIds);

    @Nonnull
    Map<String, Object> getUserDashboard(@Nullable UUID userId, @Nullable Set<UUID> allowedAssetIds);

    @Nonnull
    Map<String, Object> getWorkloadStats(@Nullable UUID userId);

    @Nonnull
    Map<String, Long> getTaskCountByStatus(@Nullable UUID userId);

    @Nonnull
    Map<String, Long> getClaimCountByStatus(@Nullable UUID userId);

    /**
     * 资产新增趋势：按 {@code assets.created_at} 归日统计
     *
     * @param days 天数（&lt;=0 取默认 30，&gt;365 收敛为 365）
     */
    @Nonnull
    Map<String, Object> getAssetTrend(int days);

    /**
     * 漏洞发现趋势：按 {@code vulnerabilities.created_at} 归日统计
     *
     * @param days 天数（规则同上）
     */
    @Nonnull
    Map<String, Object> getVulnerabilityTrend(int days);

    /**
     * 工作量趋势：按任务完成时间（{@code completed_at}，回退 {@code updated_at}）归日，
     * 只统计已完成任务；{@code userId} 非空时只统计该用户。
     *
     * @param userId 用户ID，可为 null 表示全员
     * @param days   天数（规则同上）
     */
    @Nonnull
    Map<String, Object> getWorkloadTrend(@Nullable UUID userId, int days);

    /**
     * 跨用户工作量排行
     *
     * <p>统计窗口内「活动时间」落在窗口内的任务（{@code completed_at} 优先，回退 {@code updated_at}），
     * 按负责人聚合任务数与工时，按 {@code taskCount} 倒序（同数按用户名升序）。
     *
     * @param limit       返回条数（&lt;=0 取默认 10，&gt;100 收敛为 100）
     * @param days        统计窗口天数（规则同趋势）
     * @param allUsers    true 返回全量排行（管理类角色）；false 仅返回 {@code currentUserId} 自己那条
     * @param currentUserId 当前用户ID
     * @return 排行列表，元素形如
     *         {@code {userId, username, taskCount, estimatedHours, actualHours}}
     */
    @Nonnull
    List<Map<String, Object>> getWorkloadRanking(int limit, int days, boolean allUsers, @Nullable UUID currentUserId);

    /**
     * 读取仪表盘配置
     *
     * <p>不存在时返回带默认值（{@code layout="[]"}、{@code cards="[]"}）的空白配置对象，不落库。
     */
    @Nonnull
    DashboardConfig getConfig(@Nullable UUID userId);

    /**
     * 保存仪表盘配置
     *
     * <p>{@code layout} / {@code cards} 为空时以默认值兜底（{@code "[]"}），避免触发 NOT NULL 约束；
     * 同一用户重复保存只有一行记录（幂等）。
     */
    @Nonnull
    DashboardConfig saveConfig(@Nullable UUID userId, @Nonnull DashboardConfig config);

    void deleteConfig(@Nullable UUID userId);

    @Nonnull
    Map<String, Object> getDashboardOverview();

    @Nonnull
    Map<String, Object> getUserDashboard(@Nullable UUID userId);

    @Nonnull
    byte[] exportStats(@Nullable String type, @Nullable String format);
}
