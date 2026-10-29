package com.vulnaudit.service.impl;

import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.DashboardConfig;
import com.vulnaudit.entity.Task;
import com.vulnaudit.entity.User;
import com.vulnaudit.repository.*;
import com.vulnaudit.service.DashboardService;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * 仪表盘服务实现类
 *
 * <p>统计口径（与 docs/design/dashboard.md 保持一致）：
 * <ul>
 *   <li>资产/漏洞趋势按 {@code created_at} 归日；</li>
 *   <li>工作量趋势与排行按任务「活动时间」归日：{@code completed_at} 优先，为空回退 {@code updated_at}
 *       （导出的历史数据里存在已完成但未写 completed_at 的任务，回退可避免这些任务被漏统计）；</li>
 *   <li>趋势窗口内没有数据的天补 0，返回点数恒等于 {@code days}，保证前端曲线连续。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    /** 趋势默认窗口天数 */
    private static final int DEFAULT_TREND_DAYS = 30;

    /** 趋势窗口上限天数（防止一次查询扫全表） */
    private static final int MAX_TREND_DAYS = 365;

    /** 排行默认条数 */
    private static final int DEFAULT_RANKING_LIMIT = 10;

    /** 排行条数上限 */
    private static final int MAX_RANKING_LIMIT = 100;

    /** 仪表盘配置的默认布局与卡片（空 JSON 数组，避免 NOT NULL 约束） */
    private static final String DEFAULT_CONFIG_LAYOUT = "[]";
    private static final String DEFAULT_CONFIG_CARDS = "[]";

    /** 已完成任务状态值 */
    private static final String TASK_STATUS_COMPLETED = "completed";

    private final DashboardConfigRepository dashboardConfigRepository;
    private final AssetRepository assetRepository;
    private final VulnerabilityRepository vulnerabilityRepository;
    private final TaskRepository taskRepository;
    private final AssetClaimRepository assetClaimRepository;
    private final UserRepository userRepository;
    private final com.vulnaudit.service.VulnerabilityService vulnerabilityService;

    // ------------------------------------------------------------------ 资产统计（按可见范围收敛）

    @Override
    public Map<String, Object> getAssetStats() {
        return getAssetStats(null);
    }

    @Override
    public Map<String, Object> getAssetStats(@Nullable Set<UUID> allowedAssetIds) {
        log.debug("获取资产统计: scoped={}", allowedAssetIds != null);

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", countAsset(allowedAssetIds, null));
        stats.put("by_status", getAssetCountByStatus(allowedAssetIds));
        stats.put("by_type", getAssetCountByType(allowedAssetIds));
        stats.put("by_priority", getAssetCountByPriority(allowedAssetIds));

        return stats;
    }

    @Override
    public Map<String, Long> getAssetCountByStatus() {
        return getAssetCountByStatus(null);
    }

    @Override
    public Map<String, Long> getAssetCountByStatus(@Nullable Set<UUID> allowedAssetIds) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (String status : List.of("active", "under_test", "suspended", "archived")) {
            result.put(status, countAsset(allowedAssetIds,
                    (root, cb) -> cb.equal(root.get("status"), status)));
        }
        return result;
    }

    @Override
    public Map<String, Long> getAssetCountByType() {
        return getAssetCountByType(null);
    }

    @Override
    public Map<String, Long> getAssetCountByType(@Nullable Set<UUID> allowedAssetIds) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (Asset asset : assetRepository.findAll(assetSpec(allowedAssetIds, null))) {
            String type = asset.getTargetType();
            if (type == null) {
                continue;
            }
            result.merge(type, 1L, Long::sum);
        }
        return result;
    }

    @Override
    public Map<String, Long> getAssetCountByPriority() {
        return getAssetCountByPriority(null);
    }

    @Override
    public Map<String, Long> getAssetCountByPriority(@Nullable Set<UUID> allowedAssetIds) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (String priority : List.of("P0", "P1", "P2", "P3")) {
            result.put(priority, countAsset(allowedAssetIds,
                    (root, cb) -> cb.equal(root.get("priority"), priority)));
        }
        return result;
    }

    /** 资产可见范围谓词：null = 不限制；空集合 = 无任何可见资产 */
    private jakarta.persistence.criteria.Predicate assetScope(
            jakarta.persistence.criteria.Root<Asset> root,
            jakarta.persistence.criteria.CriteriaBuilder cb,
            @Nullable Set<UUID> allowedAssetIds) {
        if (allowedAssetIds == null) {
            return cb.conjunction();
        }
        if (allowedAssetIds.isEmpty()) {
            return cb.disjunction();
        }
        return root.get("id").in(allowedAssetIds);
    }

    private Specification<Asset> assetSpec(@Nullable Set<UUID> allowedAssetIds,
                                           @Nullable java.util.function.BiFunction<jakarta.persistence.criteria.Root<Asset>,
                                                   jakarta.persistence.criteria.CriteriaBuilder,
                                                   jakarta.persistence.criteria.Predicate> extra) {
        return (root, query, cb) -> {
            jakarta.persistence.criteria.Predicate scope = assetScope(root, cb, allowedAssetIds);
            return extra == null ? scope : cb.and(scope, extra.apply(root, cb));
        };
    }

    private long countAsset(@Nullable Set<UUID> allowedAssetIds,
                            @Nullable java.util.function.BiFunction<jakarta.persistence.criteria.Root<Asset>,
                                    jakarta.persistence.criteria.CriteriaBuilder,
                                    jakarta.persistence.criteria.Predicate> extra) {
        return assetRepository.count(assetSpec(allowedAssetIds, extra));
    }

    // ------------------------------------------------------------------ 漏洞统计（按可见范围收敛）

    @Override
    public Map<String, Object> getVulnerabilityStats() {
        return getVulnerabilityStats(null);
    }

    /**
     * 漏洞统计（按可见资产范围收敛）
     *
     * <p>{@code allowedAssetIds} 为 null 表示管理层看全量；否则仅统计可见资产下的漏洞。
     * 分组计数统一委托 {@link VulnerabilityService} 的同名重载，与漏洞列表/统计共用同一可见性判定，
     * 本类不重复实现过滤规则。
     */
    @Override
    public Map<String, Object> getVulnerabilityStats(@Nullable Set<UUID> allowedAssetIds) {
        log.debug("获取漏洞统计: scoped={}", allowedAssetIds != null);

        Map<String, Long> byWorkflow = getVulnerabilityCountByWorkflowStatus(allowedAssetIds);
        long total = byWorkflow.values().stream().mapToLong(Long::longValue).sum();

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("by_workflow_status", byWorkflow);
        stats.put("by_customer_status", getVulnerabilityCountByCustomerStatus(allowedAssetIds));
        stats.put("by_disclosure_status", getVulnerabilityCountByDisclosureStatus(allowedAssetIds));
        stats.put("by_severity", getVulnerabilityCountBySeverity(allowedAssetIds));

        return stats;
    }

    @Override
    public Map<String, Long> getVulnerabilityCountByWorkflowStatus() {
        return getVulnerabilityCountByWorkflowStatus(null);
    }

    @Override
    public Map<String, Long> getVulnerabilityCountByWorkflowStatus(@Nullable Set<UUID> allowedAssetIds) {
        return toCountMap(vulnerabilityService.countByWorkflowStatus(allowedAssetIds),
                List.of("new", "in_progress", "resolved", "closed"), false);
    }

    @Override
    public Map<String, Long> getVulnerabilityCountByCustomerStatus() {
        return getVulnerabilityCountByCustomerStatus(null);
    }

    @Override
    public Map<String, Long> getVulnerabilityCountByCustomerStatus(@Nullable Set<UUID> allowedAssetIds) {
        return toCountMap(vulnerabilityService.countByCustomerStatus(allowedAssetIds),
                List.of("pending_review", "accepted", "rejected"), false);
    }

    @Override
    public Map<String, Long> getVulnerabilityCountByDisclosureStatus() {
        return getVulnerabilityCountByDisclosureStatus(null);
    }

    @Override
    public Map<String, Long> getVulnerabilityCountByDisclosureStatus(@Nullable Set<UUID> allowedAssetIds) {
        return toCountMap(vulnerabilityService.countByDisclosureStatus(allowedAssetIds),
                List.of("not_disclosed", "disclosed"), false);
    }

    @Override
    public Map<String, Long> getVulnerabilityCountBySeverity() {
        return getVulnerabilityCountBySeverity(null);
    }

    @Override
    public Map<String, Long> getVulnerabilityCountBySeverity(@Nullable Set<UUID> allowedAssetIds) {
        // 库中 severity 已归一为大写，这里按大小写不敏感归一为小写 key，保持既有响应结构
        return toCountMap(vulnerabilityService.countBySeverity(allowedAssetIds),
                List.of("critical", "high", "medium", "low", "info"), true);
    }

    /**
     * 把 {@code List<Object[]>{ {key, count} }} 转成 map：先铺默认 0，再覆盖实际值。
     *
     * @param rows            分组统计结果
     * @param canonicalKeys   对外约定的 key 集合（保证响应结构稳定）
     * @param caseInsensitive 是否把 key 归一为小写（severity 场景）
     */
    private Map<String, Long> toCountMap(List<Object[]> rows, List<String> canonicalKeys, boolean caseInsensitive) {
        Map<String, Long> result = new LinkedHashMap<>();
        for (String key : canonicalKeys) {
            result.put(key, 0L);
        }
        for (Object[] row : rows) {
            if (row == null || row.length < 2 || row[0] == null) {
                continue;
            }
            String key = String.valueOf(row[0]);
            String normalized = caseInsensitive ? key.toLowerCase(Locale.ROOT) : key;
            result.merge(normalized, ((Number) row[1]).longValue(), Long::sum);
        }
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

    // ------------------------------------------------------------------ 趋势

    @Override
    public Map<String, Object> getAssetTrend(int days) {
        int window = normalizeDays(days);
        LocalDateTime from = windowStart(window);
        LocalDateTime to = windowEnd();

        List<Asset> assets = assetRepository.findAll((root, query, cb) -> cb.and(
                cb.isNotNull(root.get("createdAt")),
                cb.greaterThanOrEqualTo(root.get("createdAt"), from),
                cb.lessThan(root.get("createdAt"), to)));

        log.debug("资产趋势: days={}, 命中={}", window, assets.size());
        return buildTrendPayload(window, assets.stream().map(Asset::getCreatedAt).toList());
    }

    @Override
    public Map<String, Object> getVulnerabilityTrend(int days) {
        int window = normalizeDays(days);
        LocalDateTime from = windowStart(window);
        LocalDateTime to = windowEnd();

        List<com.vulnaudit.entity.Vulnerability> vulnerabilities = vulnerabilityRepository.findAll(
                (root, query, cb) -> cb.and(
                        cb.isNotNull(root.get("createdAt")),
                        cb.greaterThanOrEqualTo(root.get("createdAt"), from),
                        cb.lessThan(root.get("createdAt"), to)));

        log.debug("漏洞趋势: days={}, 命中={}", window, vulnerabilities.size());
        return buildTrendPayload(window, vulnerabilities.stream()
                .map(com.vulnaudit.entity.Vulnerability::getCreatedAt)
                .toList());
    }

    @Override
    public Map<String, Object> getWorkloadTrend(@Nullable UUID userId, int days) {
        int window = normalizeDays(days);
        LocalDateTime from = windowStart(window);
        LocalDateTime to = windowEnd();

        List<Task> tasks = taskRepository.findAll((root, query, cb) -> {
            Expression<LocalDateTime> activity = cb.coalesce(root.get("completedAt"), root.get("updatedAt"));
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("status"), TASK_STATUS_COMPLETED));
            predicates.add(cb.greaterThanOrEqualTo(activity, from));
            predicates.add(cb.lessThan(activity, to));
            if (userId != null) {
                predicates.add(cb.equal(root.get("assignedTo"), userId));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        });

        Map<String, Object> payload = buildTrendPayload(window,
                tasks.stream().map(this::taskActivityTime).toList());
        if (userId != null) {
            payload.put("userId", userId);
        }
        log.debug("工作量趋势: userId={}, days={}, 命中={}", userId, window, tasks.size());
        return payload;
    }

    // ------------------------------------------------------------------ 跨用户工作量排行

    @Override
    public List<Map<String, Object>> getWorkloadRanking(int limit, int days,
                                                        boolean allUsers,
                                                        @Nullable UUID currentUserId) {
        int window = normalizeDays(days);
        int max = normalizeLimit(limit);
        LocalDateTime from = windowStart(window);
        LocalDateTime to = windowEnd();

        List<Task> tasks = taskRepository.findAll((root, query, cb) -> {
            Expression<LocalDateTime> activity = cb.coalesce(root.get("completedAt"), root.get("updatedAt"));
            return cb.and(
                    cb.isNotNull(root.get("assignedTo")),
                    cb.greaterThanOrEqualTo(activity, from),
                    cb.lessThan(activity, to));
        });

        Map<UUID, long[]> taskCounts = new HashMap<>();
        Map<UUID, BigDecimal> estimatedHours = new HashMap<>();
        Map<UUID, BigDecimal> actualHours = new HashMap<>();
        for (Task task : tasks) {
            UUID assignee = task.getAssignedTo();
            if (assignee == null) {
                continue;
            }
            taskCounts.computeIfAbsent(assignee, key -> new long[1])[0]++;
            if (task.getEstimatedHours() != null) {
                estimatedHours.merge(assignee, task.getEstimatedHours(), BigDecimal::add);
            }
            if (task.getActualHours() != null) {
                actualHours.merge(assignee, task.getActualHours(), BigDecimal::add);
            }
        }

        List<UUID> userIds;
        if (allUsers) {
            userIds = new ArrayList<>(taskCounts.keySet());
        } else {
            // 非管理类角色只返回自己那条（无任务时也返回 0，保证前端渲染一致）
            userIds = currentUserId == null ? List.of() : List.of(currentUserId);
        }

        Map<UUID, String> usernames = userIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(userIds).stream()
                        .collect(Collectors.toMap(User::getId, User::getUsername, (left, right) -> left));

        List<Map<String, Object>> ranking = new ArrayList<>(userIds.size());
        for (UUID userId : userIds) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("userId", userId);
            row.put("username", usernames.get(userId));
            row.put("taskCount", taskCounts.getOrDefault(userId, new long[1])[0]);
            row.put("estimatedHours", estimatedHours.getOrDefault(userId, BigDecimal.ZERO));
            row.put("actualHours", actualHours.getOrDefault(userId, BigDecimal.ZERO));
            ranking.add(row);
        }

        ranking.sort(Comparator
                .comparingLong((Map<String, Object> row) -> ((Number) row.get("taskCount")).longValue())
                .reversed()
                .thenComparing(row -> String.valueOf(row.get("username"))));

        if (ranking.size() > max) {
            ranking = new ArrayList<>(ranking.subList(0, max));
        }
        log.debug("工作量排行: allUsers={}, days={}, limit={}, 返回={}",
                allUsers, window, max, ranking.size());
        return ranking;
    }

    // ------------------------------------------------------------------ 仪表盘配置

    @Override
    @Nonnull
    public DashboardConfig getConfig(@Nullable UUID userId) {
        if (userId == null) {
            return defaultConfig(null);
        }
        return dashboardConfigRepository.findByUserId(userId)
                .orElseGet(() -> defaultConfig(userId));
    }

    @Override
    @Transactional
    public DashboardConfig saveConfig(UUID userId, DashboardConfig config) {
        if (userId == null) {
            throw new com.vulnaudit.exception.BusinessException(
                    com.vulnaudit.exception.ErrorCode.BAD_REQUEST, "用户ID不能为空");
        }
        log.info("保存仪表盘配置: userId={}", userId);

        DashboardConfig existingConfig = dashboardConfigRepository.findByUserId(userId)
                .orElse(DashboardConfig.builder().userId(userId).build());

        // 未提交的字段保持原值；新建且未提交时用空 JSON 兜底，避免 layout/cards NOT NULL 约束
        if (StringUtils.hasText(config.getLayout())) {
            existingConfig.setLayout(config.getLayout().trim());
        } else if (existingConfig.getLayout() == null) {
            existingConfig.setLayout(DEFAULT_CONFIG_LAYOUT);
        }
        if (StringUtils.hasText(config.getCards())) {
            existingConfig.setCards(config.getCards().trim());
        } else if (existingConfig.getCards() == null) {
            existingConfig.setCards(DEFAULT_CONFIG_CARDS);
        }

        return dashboardConfigRepository.save(Objects.requireNonNull(existingConfig));
    }

    @Override
    @Transactional
    public void deleteConfig(UUID userId) {
        log.info("删除仪表盘配置: userId={}", userId);
        dashboardConfigRepository.deleteByUserId(userId);
    }

    // ------------------------------------------------------------------ 内部工具

    private DashboardConfig defaultConfig(@Nullable UUID userId) {
        return DashboardConfig.builder()
                .userId(userId)
                .layout(DEFAULT_CONFIG_LAYOUT)
                .cards(DEFAULT_CONFIG_CARDS)
                .build();
    }

    /** days 归一：非正数取 30，超过 365 收敛为 365 */
    private int normalizeDays(int days) {
        if (days <= 0) {
            return DEFAULT_TREND_DAYS;
        }
        return Math.min(days, MAX_TREND_DAYS);
    }

    /** limit 归一：非正数取 10，超过 100 收敛为 100 */
    private int normalizeLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_RANKING_LIMIT;
        }
        return Math.min(limit, MAX_RANKING_LIMIT);
    }

    /** 窗口起点（含）：含今天在内共 days 天的 00:00:00 */
    private LocalDateTime windowStart(int days) {
        return LocalDate.now().minusDays(days - 1L).atStartOfDay();
    }

    /** 窗口终点（不含）：明天 00:00:00 */
    private LocalDateTime windowEnd() {
        return LocalDate.now().plusDays(1).atStartOfDay();
    }

    /** 任务活动时间：completed_at 优先，回退 updated_at */
    private LocalDateTime taskActivityTime(Task task) {
        return task.getCompletedAt() != null ? task.getCompletedAt() : task.getUpdatedAt();
    }

    /**
     * 拼装趋势返回体：窗口内逐日输出，无数据的天补 0
     *
     * @param days       窗口天数（已归一）
     * @param timestamps 命中记录的归日时间戳
     * @return {days, startDate, endDate, data:[{date, count}]}
     */
    private Map<String, Object> buildTrendPayload(int days, List<LocalDateTime> timestamps) {
        LocalDate end = LocalDate.now();
        LocalDate start = end.minusDays(days - 1L);

        Map<LocalDate, Long> counts = new HashMap<>();
        for (LocalDateTime timestamp : timestamps) {
            if (timestamp != null) {
                counts.merge(timestamp.toLocalDate(), 1L, Long::sum);
            }
        }

        List<Map<String, Object>> data = new ArrayList<>(days);
        for (int offset = 0; offset < days; offset++) {
            LocalDate day = start.plusDays(offset);
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("date", day.toString());
            point.put("count", counts.getOrDefault(day, 0L));
            data.add(point);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("days", days);
        payload.put("startDate", start.toString());
        payload.put("endDate", end.toString());
        payload.put("data", data);
        return payload;
    }

    @Override
    public Map<String, Object> getDashboardOverview() {
        return getDashboardOverview(null);
    }

    /**
     * 仪表盘概览（按可见资产范围收敛）
     *
     * <p>{@code allowedAssetIds} 为 null 表示管理层看全量；否则资产与漏洞统计都只覆盖可见资产。
     * 概览同时包含资产与漏洞两部分，若只收敛其一会被当作绕过口径，因此两者一起收敛。
     */
    @Override
    public Map<String, Object> getDashboardOverview(@Nullable Set<UUID> allowedAssetIds) {
        log.debug("获取仪表盘概览: scoped={}", allowedAssetIds != null);

        Map<String, Object> overview = new LinkedHashMap<>();
        overview.put("assets", getAssetStats(allowedAssetIds));
        overview.put("vulnerabilities", getVulnerabilityStats(allowedAssetIds));

        return overview;
    }

    /**
     * 获取用户个性化仪表盘数据
     */
    @Override
    public Map<String, Object> getUserDashboard(UUID userId) {
        return getUserDashboard(userId, null);
    }

    @Override
    public Map<String, Object> getUserDashboard(UUID userId, @Nullable Set<UUID> allowedAssetIds) {
        log.debug("获取用户仪表盘: userId={}, scoped={}", userId, allowedAssetIds != null);

        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("userId", userId);
        dashboard.put("overview", getDashboardOverview(allowedAssetIds));
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
