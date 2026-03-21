package com.vulnaudit.service;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.AuditLog;

import java.util.UUID;

/**
 * 审计日志服务接口
 */
public interface AuditLogService {

    /**
     * 记录审计日志
     */
    void log(UUID userId, String action, String resourceType, UUID resourceId, String detail);

    /**
     * 记录审计日志（带IP地址）
     */
    void log(UUID userId, String action, String resourceType, UUID resourceId, String detail, String ipAddress);

    /**
     * 根据ID查询审计日志
     */
    AuditLog findById(UUID id);

    /**
     * 分页查询审计日志
     */
    PageResponse<AuditLog> findAll(int page, int size, UUID userId, String action, String resourceType);

    /**
     * 根据用户ID查询审计日志
     */
    PageResponse<AuditLog> findByUserId(UUID userId, int page, int size);

    /**
     * 根据资源类型和ID查询审计日志
     */
    PageResponse<AuditLog> findByResource(String resourceType, UUID resourceId, int page, int size);

    /**
     * 清理过期日志
     */
    void cleanOldLogs(int daysToKeep);
}
