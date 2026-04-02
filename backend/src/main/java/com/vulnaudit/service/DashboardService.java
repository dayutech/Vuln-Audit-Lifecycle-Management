package com.vulnaudit.service;

import com.vulnaudit.entity.DashboardConfig;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.Map;
import java.util.UUID;

/**
 * 仪表盘服务接口
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

    @Nonnull
    Map<String, Object> getWorkloadStats(@Nullable UUID userId);

    @Nonnull
    Map<String, Long> getTaskCountByStatus(@Nullable UUID userId);

    @Nonnull
    Map<String, Long> getClaimCountByStatus(@Nullable UUID userId);

    @Nonnull
    Map<String, Object> getAssetTrend(int days);

    @Nonnull
    Map<String, Object> getVulnerabilityTrend(int days);

    @Nonnull
    Map<String, Object> getWorkloadTrend(@Nullable UUID userId, int days);

    @Nonnull
    DashboardConfig getConfig(@Nullable UUID userId);

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