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
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 审计日志控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/audit-logs")
@RequiredArgsConstructor
@Tag(name = "审计日志", description = "审计日志查询接口")
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

    @GetMapping
    @Operation(summary = "分页查询审计日志", description = "支持按用户、操作、资源类型筛选")
    public ResponseEntity<ApiResponse<PageResponse<AuditLog>>> listAuditLogs(
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "用户ID") @RequestParam(required = false) UUID userId,
            @Parameter(description = "操作类型") @RequestParam(required = false) String action,
            @Parameter(description = "资源类型") @RequestParam(required = false) String resourceType) {
        
        log.info("分页查询审计日志: page={}, size={}, userId={}, action={}, resourceType={}", 
                page, size, userId, action, resourceType);
        
        PageResponse<AuditLog> result = auditLogService.findAll(page, size, userId, action, resourceType);
        return ResponseEntity.ok(ApiResponse.success(result));
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
