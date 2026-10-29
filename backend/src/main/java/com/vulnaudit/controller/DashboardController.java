package com.vulnaudit.controller;

import com.vulnaudit.dto.*;
import com.vulnaudit.entity.DashboardConfig;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * 仪表盘控制器
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

    /**
     * 获取我的仪表盘
     */
    @GetMapping("/my")
    @Operation(summary = "获取我的仪表盘", description = "获取当前用户的个性化仪表盘数据")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getMyDashboard() {
        log.info("获取我的仪表盘");
        
        UUID userId = getCurrentUserId();
        if (userId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(401, "未授权"));
        }
        Map<String, Object> dashboard = dashboardService.getUserDashboard(userId);
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
    @Operation(summary = "获取仪表盘概览", description = "获取仪表盘综合统计数据")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getOverview() {
        log.info("获取仪表盘概览");
        
        Map<String, Object> overview = dashboardService.getDashboardOverview();
        return ResponseEntity.ok(ApiResponse.success(overview));
    }

    @GetMapping("/assets/stats")
    @Operation(summary = "获取资产统计", description = "获取资产统计数据")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAssetStats() {
        log.info("获取资产统计");
        
        Map<String, Object> stats = dashboardService.getAssetStats();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/assets/stats/status")
    @Operation(summary = "获取资产状态统计", description = "按状态统计资产数量")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getAssetStatsByStatus() {
        log.info("获取资产状态统计");
        
        Map<String, Long> stats = dashboardService.getAssetCountByStatus();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/assets/stats/type")
    @Operation(summary = "获取资产类型统计", description = "按类型统计资产数量")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getAssetStatsByType() {
        log.info("获取资产类型统计");
        
        Map<String, Long> stats = dashboardService.getAssetCountByType();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/assets/stats/priority")
    @Operation(summary = "获取资产优先级统计", description = "按优先级统计资产数量")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getAssetStatsByPriority() {
        log.info("获取资产优先级统计");
        
        Map<String, Long> stats = dashboardService.getAssetCountByPriority();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/vulnerabilities/stats")
    @Operation(summary = "获取漏洞统计", description = "获取漏洞统计数据")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getVulnerabilityStats() {
        log.info("获取漏洞统计");
        
        Map<String, Object> stats = dashboardService.getVulnerabilityStats();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/vulnerabilities/stats/workflow-status")
    @Operation(summary = "获取漏洞工作流状态统计", description = "按工作流状态统计漏洞数量")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getVulnerabilityStatsByWorkflowStatus() {
        log.info("获取漏洞工作流状态统计");
        
        Map<String, Long> stats = dashboardService.getVulnerabilityCountByWorkflowStatus();
        return ResponseEntity.ok(ApiResponse.success(stats));
    }

    @GetMapping("/vulnerabilities/stats/severity")
    @Operation(summary = "获取漏洞严重程度统计", description = "按严重程度统计漏洞数量")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getVulnerabilityStatsBySeverity() {
        log.info("获取漏洞严重程度统计");
        
        Map<String, Long> stats = dashboardService.getVulnerabilityCountBySeverity();
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

    @GetMapping("/export")
    @Operation(summary = "导出统计数据", description = "导出统计数据为 CSV（支持 assets / vulnerabilities / workload）")
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
}
