package com.vulnaudit.service.impl;

import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.DashboardConfig;
import com.vulnaudit.repository.*;
import com.vulnaudit.service.DashboardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.Objects;

import jakarta.annotation.Nullable;

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
    public Map<String, Object> getWorkloadStats(@Nullable UUID userId) {
        log.debug("获取工作量统计: userId={}", userId);

        Map<String, Object> stats = new HashMap<>();
        stats.put("tasks", getTaskCountByStatus(userId));
        stats.put("claims", getClaimCountByStatus(userId));

        return stats;
    }

    @Override
    public Map<String, Long> getTaskCountByStatus(@Nullable UUID userId) {
        Map<String, Long> result = new HashMap<>();
        if (userId == null) {
            return result;
        }
        result.put("pending", taskRepository.count(
                (root, query, cb) -> cb.and(
                        cb.equal(root.get("assignedTo"), userId),
                        cb.equal(root.get("status"), "pending"))));
        result.put("in_progress", taskRepository.count(
                (root, query, cb) -> cb.and(
                        cb.equal(root.get("assignedTo"), userId),
                        cb.equal(root.get("status"), "in_progress"))));
        result.put("completed", taskRepository.count(
                (root, query, cb) -> cb.and(
                        cb.equal(root.get("assignedTo"), userId),
                        cb.equal(root.get("status"), "completed"))));
        return result;
    }

    @Override
    public Map<String, Long> getClaimCountByStatus(@Nullable UUID userId) {
        Map<String, Long> result = new HashMap<>();
        if (userId == null) {
            return result;
        }
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
        
        return dashboardConfigRepository.save(Objects.requireNonNull(existingConfig));
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

    /**
     * 导出统计数据（CSV）。
     *
     * <p>此前该方法直接返回空字节数组，前端下载到的是 0 字节文件。
     * 现按类型输出 UTF-8（含 BOM，Excel 可直接打开）的 CSV：
     * <ul>
     *   <li>{@code assets}：资产清单</li>
     *   <li>{@code vulnerabilities}：漏洞清单</li>
     *   <li>{@code workload}：工作量（按负责人聚合任务数与工时）</li>
     * </ul>
     *
     * @param type   导出类型
     * @param format 仅支持 csv（xlsx 需引入 POI，暂不提供）
     */
    @Override
    public byte[] exportStats(String type, String format) {
        log.info("导出统计: type={}, format={}", type, format);
        String fmt = format == null ? "csv" : format.trim().toLowerCase(java.util.Locale.ROOT);
        if (!"csv".equals(fmt)) {
            throw new com.vulnaudit.exception.BusinessException(
                    com.vulnaudit.exception.ErrorCode.BAD_REQUEST,
                    "暂只支持 csv 格式导出（收到: " + format + "）");
        }

        StringBuilder sb = new StringBuilder();
        switch (type == null ? "" : type.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "assets" -> {
                sb.append("资产编号,资产名称,目标类型,目标来源,优先级,状态,负责人,创建时间\n");
                for (var asset : assetRepository.findAll()) {
                    sb.append(csv(asset.getCode())).append(',')
                            .append(csv(asset.getName())).append(',')
                            .append(csv(asset.getTargetType())).append(',')
                            .append(csv(asset.getTargetSource())).append(',')
                            .append(csv(asset.getPriority())).append(',')
                            .append(csv(asset.getStatus())).append(',')
                            .append(csv(String.valueOf(asset.getOwnerId()))).append(',')
                            .append(csv(String.valueOf(asset.getCreatedAt()))).append('\n');
                }
            }
            case "vulnerabilities" -> {
                sb.append("漏洞编号,标题,CVE,类型,严重程度,CVSS,工作流状态,客户确认,披露状态,资产ID,发现时间\n");
                for (var vuln : vulnerabilityRepository.findAll()) {
                    sb.append(csv(vuln.getVulnerabilityNo())).append(',')
                            .append(csv(vuln.getTitle())).append(',')
                            .append(csv(vuln.getCveId())).append(',')
                            .append(csv(vuln.getType())).append(',')
                            .append(csv(vuln.getSeverity())).append(',')
                            .append(csv(vuln.getCvssScore() == null ? null : vuln.getCvssScore().toPlainString())).append(',')
                            .append(csv(vuln.getWorkflowStatus())).append(',')
                            .append(csv(vuln.getCustomerStatus())).append(',')
                            .append(csv(vuln.getDisclosureStatus())).append(',')
                            .append(csv(String.valueOf(vuln.getAssetId()))).append(',')
                            .append(csv(String.valueOf(vuln.getCreatedAt()))).append('\n');
                }
            }
            case "workload" -> {
                sb.append("负责人ID,任务数,预估工时,实际工时\n");
                java.util.Map<UUID, long[]> taskCount = new java.util.HashMap<>();
                java.util.Map<UUID, java.math.BigDecimal> estimated = new java.util.HashMap<>();
                java.util.Map<UUID, java.math.BigDecimal> actual = new java.util.HashMap<>();
                for (var task : taskRepository.findAll()) {
                    UUID uid = task.getAssignedTo();
                    if (uid == null) {
                        continue;
                    }
                    taskCount.computeIfAbsent(uid, k -> new long[1])[0]++;
                    if (task.getEstimatedHours() != null) {
                        estimated.merge(uid, task.getEstimatedHours(), java.math.BigDecimal::add);
                    }
                    if (task.getActualHours() != null) {
                        actual.merge(uid, task.getActualHours(), java.math.BigDecimal::add);
                    }
                }
                for (var entry : taskCount.entrySet()) {
                    sb.append(csv(String.valueOf(entry.getKey()))).append(',')
                            .append(entry.getValue()[0]).append(',')
                            .append(csv(String.valueOf(estimated.getOrDefault(entry.getKey(), java.math.BigDecimal.ZERO)))).append(',')
                            .append(csv(String.valueOf(actual.getOrDefault(entry.getKey(), java.math.BigDecimal.ZERO)))).append('\n');
                }
            }
            default -> throw new com.vulnaudit.exception.BusinessException(
                    com.vulnaudit.exception.ErrorCode.BAD_REQUEST,
                    "不支持的导出类型: " + type + "（支持 assets / vulnerabilities / workload）");
        }

        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = sb.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] result = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, result, 0, bom.length);
        System.arraycopy(body, 0, result, bom.length, body.length);
        log.info("导出完成: type={}, rows≈{}", type, sb.toString().split("\n").length - 1);
        return result;
    }

    /** CSV 字段转义：包含逗号/引号/换行时用双引号包裹并转义内部引号 */
    private String csv(String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replace("\r", " ").replace("\n", " ");
        if (cleaned.contains(",") || cleaned.contains("\"")) {
            return "\"" + cleaned.replace("\"", "\"\"") + "\"";
        }
        return cleaned;
    }
}
