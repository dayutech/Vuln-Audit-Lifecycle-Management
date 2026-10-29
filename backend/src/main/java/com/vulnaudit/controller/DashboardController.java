package com.vulnaudit.controller;

import com.vulnaudit.dto.*;
import com.vulnaudit.entity.DashboardConfig;
import com.vulnaudit.service.AccessGuard;
import com.vulnaudit.service.DashboardService;
import com.vulnaudit.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.UUID;

/**
 * 仪表盘控制器
 *
 * <p>趋势与排行的口径见 {@code docs/design/dashboard.md}；排行的数据边界沿用现有授权体系：
 * 管理类角色（admin / project_leader / team_leader）看全量，普通用户只看自己那条。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "仪表盘", description = "统计数据、趋势分析等接口")
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {

    private final DashboardService dashboardService;
    private final UserService userService;
    private final AccessGuard accessGuard;

    /**
     * 获取我的仪表盘
     *
     * <p>概览部分按当前用户可见资产范围收敛（管理层为全量），不新增角色门槛，
     * 保证任何登录用户都能看到自己的首屏。
     */
    @GetMapping("/my")
    @Operation(summary = "获取我的仪表盘", description = "获取当前用户的个性化仪表盘数据（按可见资产范围收敛）")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMyDashboard() {
        log.info("获取我的仪表盘");
        
        UUID userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(401, "未授权"));
        }
        Map<String, Object> dashboard = dashboardService.getUserDashboard(userId, currentVisibleAssets());
        return ResponseEntity.ok(ApiResponse.success(dashboard));
    }

    /**
     * 保存我的仪表盘配置
     */
    @PutMapping("/my")
    @Operation(summary = "保存我的仪表盘配置", description = "保存当前用户的仪表盘配置")
    public ResponseEntity<ApiResponse<DashboardConfig>> saveMyConfig(@RequestBody DashboardConfig config) {
        log.info("保存我的仪表盘配置");
        
        UUID userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(401, "未授权"));
        }
        DashboardConfig savedConfig = dashboardService.saveConfig(userId, config);
        return ResponseEntity.ok(ApiResponse.success(savedConfig));
    }

    @GetMapping("/overview")
    @Operation(summary = "获取仪表盘概览", description = "获取仪表盘综合统计数据（资产与漏洞均按可见资产范围收敛）")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOverview() {
        log.info("获取仪表盘概览");
        
        Map<String, Object> overview = dashboardService.getDashboardOverview(currentVisibleAssets());
        return ResponseEntity.ok(ApiResponse.success(overview));
    }

    @GetMapping("/assets/stats")
    @Operation(summary = "获取资产统计", description = "获取资产统计数据（按可见资产范围收敛）")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAssetStats() {
        log.info("获取资产统计");
        
        Map<String, Object> stats = dashboardService.getAssetStats(currentVisibleAssets());
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/assets/stats/status")
    @Operation(summary = "获取资产状态统计", description = "按状态统计资产数量（按可见资产范围收敛）")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getAssetStatsByStatus() {
        log.info("获取资产状态统计");
        
        Map<String, Long> stats = dashboardService.getAssetCountByStatus(currentVisibleAssets());
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/assets/stats/type")
    @Operation(summary = "获取资产类型统计", description = "按类型统计资产数量（按可见资产范围收敛）")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getAssetStatsByType() {
        log.info("获取资产类型统计");
        
        Map<String, Long> stats = dashboardService.getAssetCountByType(currentVisibleAssets());
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/assets/stats/priority")
    @Operation(summary = "获取资产优先级统计", description = "按优先级统计资产数量（按可见资产范围收敛）")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getAssetStatsByPriority() {
        log.info("获取资产优先级统计");
        
        Map<String, Long> stats = dashboardService.getAssetCountByPriority(currentVisibleAssets());
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    /**
     * 漏洞统计（总览）
     *
     * <p>权限矩阵 2.13：仅 admin / project_leader / team_leader 可访问。
     * 通过角色门槛后仍按可见资产范围收敛，team_leader 只能看到自己团队相关的数据。
     */
    @GetMapping("/vulnerabilities/stats")
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_LEADER','TEAM_LEADER')")
    @Operation(summary = "获取漏洞统计", description = "获取漏洞统计数据（仅管理类角色；按可见资产范围收敛）")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getVulnerabilityStats() {
        log.info("获取漏洞统计");
        
        Map<String, Object> stats = dashboardService.getVulnerabilityStats(currentVisibleAssets());
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    /**
     * 漏洞工作流状态统计
     *
     * <p>角色门槛 + 可见资产范围收敛，口径与 {@code /vulnerabilities/statistics/*} 一致。
     */
    @GetMapping("/vulnerabilities/stats/workflow-status")
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_LEADER','TEAM_LEADER')")
    @Operation(summary = "获取漏洞工作流状态统计", description = "按工作流状态统计漏洞数量（仅管理类角色；按可见资产范围收敛）")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getVulnerabilityStatsByWorkflowStatus() {
        log.info("获取漏洞工作流状态统计");
        
        Map<String, Long> stats = dashboardService.getVulnerabilityCountByWorkflowStatus(currentVisibleAssets());
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    /**
     * 漏洞严重程度统计
     *
     * <p>角色门槛 + 可见资产范围收敛，口径与 {@code /vulnerabilities/statistics/*} 一致。
     */
    @GetMapping("/vulnerabilities/stats/severity")
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_LEADER','TEAM_LEADER')")
    @Operation(summary = "获取漏洞严重程度统计", description = "按严重程度统计漏洞数量（仅管理类角色；按可见资产范围收敛）")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getVulnerabilityStatsBySeverity() {
        log.info("获取漏洞严重程度统计");
        
        Map<String, Long> stats = dashboardService.getVulnerabilityCountBySeverity(currentVisibleAssets());
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/workload/stats")
    @Operation(summary = "获取工作量统计", description = "获取用户工作量统计数据")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getWorkloadStats() {
        log.info("获取工作量统计");
        
        UUID userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(401, "未授权"));
        }
        Map<String, Object> stats = dashboardService.getWorkloadStats(userId);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/workload/stats/tasks")
    @Operation(summary = "获取任务统计", description = "按状态统计用户任务数量")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getTaskStats() {
        log.info("获取任务统计");
        
        UUID userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(401, "未授权"));
        }
        Map<String, Long> stats = dashboardService.getTaskCountByStatus(userId);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/workload/stats/claims")
    @Operation(summary = "获取认领统计", description = "按状态统计用户认领数量")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getClaimStats() {
        log.info("获取认领统计");
        
        UUID userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(401, "未授权"));
        }
        Map<String, Long> stats = dashboardService.getClaimCountByStatus(userId);
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    /**
     * 跨用户工作量排行
     *
     * <p>统计窗口内任务数与工时，按任务数倒序。数据边界：管理类角色（admin / project_leader /
     * team_leader）返回全量排行；其余角色仅返回自己那条（无任务时 taskCount=0）。
     */
    @GetMapping("/workload/ranking")
    @Operation(summary = "获取跨用户工作量排行",
            description = "按任务数倒序返回 {userId, username, taskCount, estimatedHours, actualHours}；"
                    + "管理类角色看全量，普通用户只看自己那条")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getWorkloadRanking(
            @Parameter(description = "返回条数（默认 10，上限 100）") @RequestParam(defaultValue = "10") int limit,
            @Parameter(description = "统计窗口天数（默认 30，上限 365）") @RequestParam(defaultValue = "30") int days) {

        log.info("获取工作量排行: limit={}, days={}", limit, days);

        UUID userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(401, "未授权"));
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        boolean allUsers = accessGuard.isManager(authentication);

        List<Map<String, Object>> ranking = dashboardService.getWorkloadRanking(limit, days, allUsers, userId);
        return ResponseEntity.ok(ApiResponse.success(ranking));
    }

    @GetMapping("/trends/assets")
    @Operation(summary = "获取资产趋势", description = "获取资产数量趋势数据")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAssetTrend(
            @Parameter(description = "天数") @RequestParam(defaultValue = "30") int days) {
        
        log.info("获取资产趋势: days={}", days);
        
        Map<String, Object> trend = dashboardService.getAssetTrend(days);
        return ResponseEntity.ok(ApiResponse.success(trend));
    }

    @GetMapping("/trends/vulnerabilities")
    @Operation(summary = "获取漏洞趋势", description = "获取漏洞数量趋势数据")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getVulnerabilityTrend(
            @Parameter(description = "天数") @RequestParam(defaultValue = "30") int days) {
        
        log.info("获取漏洞趋势: days={}", days);
        
        Map<String, Object> trend = dashboardService.getVulnerabilityTrend(days);
        return ResponseEntity.ok(ApiResponse.success(trend));
    }

    @GetMapping("/trends/workload")
    @Operation(summary = "获取工作量趋势", description = "获取工作量趋势数据")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getWorkloadTrend(
            @Parameter(description = "天数") @RequestParam(defaultValue = "30") int days) {
        
        log.info("获取工作量趋势: days={}", days);
        
        UUID userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(401, "未授权"));
        }
        Map<String, Object> trend = dashboardService.getWorkloadTrend(userId, days);
        return ResponseEntity.ok(ApiResponse.success(trend));
    }

    @GetMapping("/config")
    @Operation(summary = "获取仪表盘配置", description = "获取用户的仪表盘配置")
    public ResponseEntity<ApiResponse<DashboardConfig>> getConfig() {
        log.info("获取仪表盘配置");
        
        UUID userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(401, "未授权"));
        }
        DashboardConfig config = dashboardService.getConfig(userId);
        return ResponseEntity.ok(ApiResponse.success(config));
    }

    @PostMapping("/config")
    @Operation(summary = "保存仪表盘配置", description = "保存用户的仪表盘配置")
    public ResponseEntity<ApiResponse<DashboardConfig>> saveConfig(@RequestBody DashboardConfig config) {
        log.info("保存仪表盘配置");
        
        UUID userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(401, "未授权"));
        }
        DashboardConfig savedConfig = dashboardService.saveConfig(userId, config);
        return ResponseEntity.ok(ApiResponse.success(savedConfig));
    }

    @DeleteMapping("/config")
    @Operation(summary = "删除仪表盘配置", description = "删除用户的仪表盘配置")
    public ResponseEntity<ApiResponse<Void>> deleteConfig() {
        log.info("删除仪表盘配置");
        
        UUID userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(401, "未授权"));
        }
        dashboardService.deleteConfig(userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 导出统计数据（CSV）
     *
     * <p><b>授权口径（重要）</b>：导出内容是<b>全量</b>资产/漏洞清单（含不在调用者可见范围内的明细），
     * 因此仅限 admin / project_leader（权限矩阵 2.13），方法级角色门槛为唯一准入。
     * <b>若日后放开给 team_leader</b>，必须改为按 {@code accessGuard.visibleAssetIds(authentication)}
     * 收敛导出范围（参照 {@code /vulnerabilities/statistics/*} 与仪表盘统计的口径），
     * 否则等同于一次性泄露全库明细——这正是本轮（t31）修复前的问题。
     *
     * @param type   导出类型（assets / vulnerabilities / workload）
     * @param format 导出格式，目前支持 csv
     */
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_LEADER')")
    @Operation(summary = "导出统计数据",
            description = "导出统计数据为 CSV（assets / vulnerabilities / workload）；导出为全量，仅 admin/project_leader 可用")
    public ResponseEntity<byte[]> exportStats(
            @Parameter(description = "统计类型") @RequestParam String type,
            @Parameter(description = "导出格式，目前支持 csv") @RequestParam(defaultValue = "csv") String format) {
        
        log.info("导出统计数据: type={}, format={}", type, format);
        
        byte[] data = dashboardService.exportStats(type, format);
        String filename = type + "." + (format == null ? "csv" : format.toLowerCase());
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header("Content-Disposition", "attachment; filename=\"" + filename + "\"")
                .body(data);
    }

    /**
     * 获取当前用户ID的辅助方法
     */
    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || "anonymousUser".equals(authentication.getName())) {
            return null;
        }
        return userService.findByUsername(authentication.getName())
                .map(user -> user.getId())
                .orElse(null);
    }

    /**
     * 当前用户的可见资产集合（统计口径的唯一来源）
     *
     * <p>委托 {@link AccessGuard#visibleAssetIds(Authentication)}：
     * {@code null} 表示不限制（admin / project_leader 全量）；否则为
     * 「本人负责 ∪ 已获批访问 ∪ 所带团队」的资产集合，与资产/漏洞列表的可见性判定完全一致。
     */
    private java.util.Set<UUID> currentVisibleAssets() {
        return accessGuard.visibleAssetIds(SecurityContextHolder.getContext().getAuthentication());
    }
}
