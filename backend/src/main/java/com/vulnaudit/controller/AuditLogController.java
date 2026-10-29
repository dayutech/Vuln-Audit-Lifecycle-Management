package com.vulnaudit.controller;

import com.vulnaudit.dto.*;
import com.vulnaudit.entity.AuditLog;
import com.vulnaudit.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 审计日志控制器
 *
 * <p>本轮增强：
 * <ul>
 *   <li>列表支持 {@code startTime} / {@code endTime} / {@code keyword} 过滤；</li>
 *   <li>新增 {@code GET /export} 导出 CSV（UTF-8 + BOM，与仪表盘导出同口径）。</li>
 * </ul>
 *
 * <p>准入仍走角色轨（{@code @PreAuthorize}）：审计日志仅 admin / project_leader 可读，
 * 与权限矩阵 2.11 以及权限点 {@code audit:read} / {@code audit:export} 的默认授权一致。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
@Tag(name = "审计日志", description = "审计日志查询与导出接口")
@PreAuthorize("hasAnyRole('ADMIN','PROJECT_LEADER')")
@SecurityRequirement(name = "bearerAuth")
public class AuditLogController {

    private final AuditLogService auditLogService;

    @GetMapping("/{id}")
    @Operation(summary = "获取审计日志详情", description = "根据ID获取审计日志详细信息")
    public ResponseEntity<ApiResponse<AuditLog>> getAuditLog(@PathVariable UUID id) {
        log.info("获取审计日志详情: id={}", id);
        
        AuditLog auditLog = auditLogService.findById(id);
        if (auditLog == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(404, "审计日志不存在"));
        }
        return ResponseEntity.ok(ApiResponse.success(auditLog));
    }

    /**
     * 分页查询审计日志
     *
     * <p>时间参数使用 ISO-8601（如 {@code 2026-10-29T00:00:00}），均为闭区间端点。
     */
    @GetMapping
    @Operation(summary = "分页查询审计日志",
            description = "支持按用户、操作、资源类型、时间范围（闭区间）、关键词（action/resourceType/details 模糊）筛选")
    public ResponseEntity<ApiResponse<PageResponse<AuditLog>>> listAuditLogs(
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "用户ID") @RequestParam(required = false) UUID userId,
            @Parameter(description = "操作类型") @RequestParam(required = false) String action,
            @Parameter(description = "资源类型") @RequestParam(required = false) String resourceType,
            @Parameter(description = "起始时间（含），ISO-8601")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @Parameter(description = "结束时间（含），ISO-8601")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @Parameter(description = "关键词，模糊匹配操作/资源类型/详情")
            @RequestParam(required = false) String keyword) {

        log.info("分页查询审计日志: page={}, size={}, userId={}, action={}, resourceType={}, startTime={}, endTime={}, keyword={}",
                page, size, userId, action, resourceType, startTime, endTime, keyword);

        PageResponse<AuditLog> result = auditLogService.findAll(page, size, userId, action, resourceType,
                startTime, endTime, keyword);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 导出审计日志 CSV
     *
     * <p>筛选参数与列表接口一致；最多导出 {@link AuditLogService#EXPORT_LIMIT} 条。
     * 响应首三字节为 UTF-8 BOM，Excel 直接双击打开不乱码。
     */
    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_LEADER')")
    @Operation(summary = "导出审计日志 CSV", description = "按当前筛选条件导出 CSV（UTF-8 BOM），仅管理层可用")
    public ResponseEntity<byte[]> exportAuditLogs(
            @Parameter(description = "用户ID") @RequestParam(required = false) UUID userId,
            @Parameter(description = "操作类型") @RequestParam(required = false) String action,
            @Parameter(description = "资源类型") @RequestParam(required = false) String resourceType,
            @Parameter(description = "起始时间（含），ISO-8601")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @Parameter(description = "结束时间（含），ISO-8601")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @Parameter(description = "关键词") @RequestParam(required = false) String keyword) {

        log.info("导出审计日志: userId={}, action={}, resourceType={}, startTime={}, endTime={}, keyword={}",
                userId, action, resourceType, startTime, endTime, keyword);

        byte[] data = auditLogService.exportCsv(userId, action, resourceType, startTime, endTime, keyword);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header("Content-Disposition", "attachment; filename=\"audit-logs.csv\"")
                .body(data);
    }

    @GetMapping("/users/{userId}")
    @Operation(summary = "获取用户的审计日志", description = "获取指定用户的所有审计日志")
    public ResponseEntity<ApiResponse<PageResponse<AuditLog>>> listUserAuditLogs(
            @PathVariable UUID userId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int size) {
        
        log.info("获取用户的审计日志: userId={}, page={}, size={}", userId, page, size);
        
        PageResponse<AuditLog> result = auditLogService.findByUserId(userId, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/resources/{resourceType}/{resourceId}")
    @Operation(summary = "获取资源的审计日志", description = "获取指定资源的所有审计日志")
    public ResponseEntity<ApiResponse<PageResponse<AuditLog>>> listResourceAuditLogs(
            @PathVariable String resourceType,
            @PathVariable UUID resourceId,
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int size) {
        
        log.info("获取资源的审计日志: resourceType={}, resourceId={}, page={}, size={}", 
                resourceType, resourceId, page, size);
        
        PageResponse<AuditLog> result = auditLogService.findByResource(resourceType, resourceId, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }
}
