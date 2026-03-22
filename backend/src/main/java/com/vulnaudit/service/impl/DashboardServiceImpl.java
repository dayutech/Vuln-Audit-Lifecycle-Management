package com.vulnaudit.service.impl;

import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.DashboardConfig;
import com.vulnaudit.entity.Vulnerability;
import com.vulnaudit.repository.*;
import com.vulnaudit.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 仪表盘服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final DashboardConfigRepository dashboardConfigRepository;
    private final AssetRepository assetRepository;
    private final VulnerabilityRepository vulnerabilityRepository;
    private final TaskRepository taskRepository;
    private final AssetClaimRepository assetClaimRepository;
    private final StatCacheRepository statCacheRepository;

    @Override
    public Map<String, Object> getAssetStats() {
        log.debug("获取资产统计");
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", assetRepository.count());
        stats.put("by_status", getAssetCountByStatus());
        stats.put("by_type", getAssetCountByType());
        stats.put("by_priority", getAssetCountByPriority());
        
        return stats;
    }

    @Override
    public Map<String, Long> getAssetCountByStatus() {
        Map<String, Long> result = new HashMap<>();
        result.put("active", assetRepository.countByStatus("active"));
        result.put("under_test", assetRepository.countByStatus("under_test"));
        result.put("suspended", assetRepository.countByStatus("suspended"));
        result.put("archived", assetRepository.countByStatus("archived"));
        return result;
    }

    @Override
    public Map<String, Long> getAssetCountByType() {
        List<Asset> assets = assetRepository.findAll();
        Map<String, Long> result = new HashMap<>();
        for (Asset asset : assets) {
            String type = asset.getTargetType();
            result.put(type, result.getOrDefault(type, 0L) + 1);
        }
        return result;
    }

    @Override
    public Map<String, Long> getAssetCountByPriority() {
        Map<String, Long> result = new HashMap<>();
        result.put("P0", assetRepository.count(
                (root, query, cb) -> cb.equal(root.get("priority"), "P0")));
        result.put("P1", assetRepository.count(
                (root, query, cb) -> cb.equal(root.get("priority"), "P1")));
        result.put("P2", assetRepository.count(
                (root, query, cb) -> cb.equal(root.get("priority"), "P2")));
        result.put("P3", assetRepository.count(
                (root, query, cb) -> cb.equal(root.get("priority"), "P3")));
        return result;
    }

    @Override
    public Map<String, Object> getVulnerabilityStats() {
        log.debug("获取漏洞统计");
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", vulnerabilityRepository.count());
        stats.put("by_workflow_status", getVulnerabilityCountByWorkflowStatus());
        stats.put("by_customer_status", getVulnerabilityCountByCustomerStatus());
        stats.put("by_disclosure_status", getVulnerabilityCountByDisclosureStatus());
        stats.put("by_severity", getVulnerabilityCountBySeverity());
        
        return stats;
    }

    @Override
    public Map<String, Long> getVulnerabilityCountByWorkflowStatus() {
        Map<String, Long> result = new HashMap<>();
        result.put("new", vulnerabilityRepository.count(
                (root, query, cb) -> cb.equal(root.get("workflowStatus"), "new")));
        result.put("in_progress", vulnerabilityRepository.count(
                (root, query, cb) -> cb.equal(root.get("workflowStatus"), "in_progress")));
        result.put("resolved", vulnerabilityRepository.count(
                (root, query, cb) -> cb.equal(root.get("workflowStatus"), "resolved")));
        result.put("closed", vulnerabilityRepository.count(
                (root, query, cb) -> cb.equal(root.get("workflowStatus"), "closed")));
        return result;
    }

    @Override
    public Map<String, Long> getVulnerabilityCountByCustomerStatus() {
        Map<String, Long> result = new HashMap<>();
        result.put("pending_review", vulnerabilityRepository.count(
                (root, query, cb) -> cb.equal(root.get("customerStatus"), "pending_review")));
        result.put("accepted", vulnerabilityRepository.count(
                (root, query, cb) -> cb.equal(root.get("customerStatus"), "accepted")));
        result.put("rejected", vulnerabilityRepository.count(
                (root, query, cb) -> cb.equal(root.get("customerStatus"), "rejected")));
        return result;
    }

    @Override
    public Map<String, Long> getVulnerabilityCountByDisclosureStatus() {
        Map<String, Long> result = new HashMap<>();
        result.put("not_disclosed", vulnerabilityRepository.count(
                (root, query, cb) -> cb.equal(root.get("disclosureStatus"), "not_disclosed")));
        result.put("disclosed", vulnerabilityRepository.count(
                (root, query, cb) -> cb.equal(root.get("disclosureStatus"), "disclosed")));
        return result;
    }

    @Override
    public Map<String, Long> getVulnerabilityCountBySeverity() {
        Map<String, Long> result = new HashMap<>();
        result.put("critical", vulnerabilityRepository.count(
                (root, query, cb) -> cb.equal(root.get("severity"), "critical")));
        result.put("high", vulnerabilityRepository.count(
                (root, query, cb) -> cb.equal(root.get("severity"), "high")));
        result.put("medium", vulnerabilityRepository.count(
                (root, query, cb) -> cb.equal(root.get("severity"), "medium")));
        result.put("low", vulnerabilityRepository.count(
                (root, query, cb) -> cb.equal(root.get("severity"), "low")));
        return result;
    }

    @Override
    public Map<String, Object> getWorkloadStats(UUID userId) {
        log.debug("获取工作量统计: userId={}", userId);
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("tasks", getTaskCountByStatus(userId));
        stats.put("claims", getClaimCountByStatus(userId));
        
        return stats;
    }

    @Override
    public Map<String, Long> getTaskCountByStatus(UUID userId) {
        Map<String, Long> result = new HashMap<>();
        result.put("pending", taskRepository.count(
                (root, query, cb) -> cb.and(
                        cb.equal(root.get("assigneeId"), userId),
                        cb.equal(root.get("status"), "pending"))));
        result.put("in_progress", taskRepository.count(
                (root, query, cb) -> cb.and(
                        cb.equal(root.get("assigneeId"), userId),
                        cb.equal(root.get("status"), "in_progress"))));
        result.put("completed", taskRepository.count(
                (root, query, cb) -> cb.and(
                        cb.equal(root.get("assigneeId"), userId),
                        cb.equal(root.get("status"), "completed"))));
        return result;
    }

    @Override
    public Map<String, Long> getClaimCountByStatus(UUID userId) {
        Map<String, Long> result = new HashMap<>();
        result.put("pending", assetClaimRepository.countByUserIdAndStatus(userId, "pending"));
        result.put("approved", assetClaimRepository.countByUserIdAndStatus(userId, "approved"));
        result.put("completed", assetClaimRepository.countByUserIdAndStatus(userId, "completed"));
        return result;
    }

    @Override
    public Map<String, Object> getAssetTrend(int days) {
        log.debug("获取资产趋势: days={}", days);
        // 简化实现，返回空数据
        Map<String, Object> result = new HashMap<>();
        result.put("days", days);
        result.put("data", Collections.emptyList());
        return result;
    }

    @Override
    public Map<String, Object> getVulnerabilityTrend(int days) {
        log.debug("获取漏洞趋势: days={}", days);
        Map<String, Object> result = new HashMap<>();
        result.put("days", days);
        result.put("data", Collections.emptyList());
        return result;
    }

    @Override
    public Map<String, Object> getWorkloadTrend(UUID userId, int days) {
        log.debug("获取工作量趋势: userId={}, days={}", userId, days);
        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("days", days);
        result.put("data", Collections.emptyList());
        return result;
    }

    @Override
    public DashboardConfig getConfig(UUID userId) {
        return dashboardConfigRepository.findByUserId(userId).orElse(null);
    }

    @Override
    @Transactional
    public DashboardConfig saveConfig(UUID userId, DashboardConfig config) {
        log.info("保存仪表盘配置: userId={}", userId);
        
        DashboardConfig existingConfig = dashboardConfigRepository.findByUserId(userId)
                .orElse(DashboardConfig.builder().userId(userId).build());
        
        if (config.getLayout() != null) {
            existingConfig.setLayout(config.getLayout());
        }
        if (config.getCards() != null) {
            existingConfig.setCards(config.getCards());
        }
        
        return dashboardConfigRepository.save(existingConfig);
    }

    @Override
    @Transactional
    public void deleteConfig(UUID userId) {
        log.info("删除仪表盘配置: userId={}", userId);
        dashboardConfigRepository.deleteByUserId(userId);
    }

    @Override
    public Map<String, Object> getDashboardOverview() {
        log.debug("获取仪表盘概览");
        
        Map<String, Object> overview = new HashMap<>();
        overview.put("assets", getAssetStats());
        overview.put("vulnerabilities", getVulnerabilityStats());
        
        return overview;
    }

    /**
     * 获取用户个性化仪表盘数据
     */
    @Override
    public Map<String, Object> getUserDashboard(UUID userId) {
        log.debug("获取用户仪表盘: userId={}", userId);
        
        Map<String, Object> dashboard = new HashMap<>();
        dashboard.put("userId", userId);
        dashboard.put("overview", getDashboardOverview());
        dashboard.put("workload", getWorkloadStats(userId));
        dashboard.put("config", getConfig(userId));
        
        return dashboard;
    }

    @Override
    public byte[] exportStats(String type, String format) {
        log.info("导出统计: type={}, format={}", type, format);
        // 简化实现
        return new byte[0];
    }
}
