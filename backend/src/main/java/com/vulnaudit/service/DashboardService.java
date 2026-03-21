package com.vulnaudit.service;

import com.vulnaudit.entity.DashboardConfig;

import java.util.Map;
import java.util.UUID;

/**
 * 仪表盘服务接口
 */
public interface DashboardService {

    // 资产统计
    Map<String, Object> getAssetStats();

    Map<String, Long> getAssetCountByStatus();

    Map<String, Long> getAssetCountByType();

    Map<String, Long> getAssetCountByPriority();

    // 漏洞统计
    Map<String, Object> getVulnerabilityStats();

    Map<String, Long> getVulnerabilityCountByWorkflowStatus();

    Map<String, Long> getVulnerabilityCountByCustomerStatus();

    Map<String, Long> getVulnerabilityCountByDisclosureStatus();

    Map<String, Long> getVulnerabilityCountBySeverity();

    // 工作量统计
    Map<String, Object> getWorkloadStats(UUID userId);

    Map<String, Long> getTaskCountByStatus(UUID userId);

    Map<String, Long> getClaimCountByStatus(UUID userId);

    // 趋势分析
    Map<String, Object> getAssetTrend(int days);

    Map<String, Object> getVulnerabilityTrend(int days);

    Map<String, Object> getWorkloadTrend(UUID userId, int days);

    // 仪表盘配置
    DashboardConfig getConfig(UUID userId);

    DashboardConfig saveConfig(UUID userId, DashboardConfig config);

    void deleteConfig(UUID userId);

    // 综合统计
    Map<String, Object> getDashboardOverview();

    // 导出
    byte[] exportStats(String type, String format);
}
