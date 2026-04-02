package com.vulnaudit.service;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.AuditLog;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.UUID;

/**
 * 审计日志服务接口
 */
public interface AuditLogService {

    void log(@Nullable UUID userId, @Nullable String action, @Nullable String resourceType, @Nullable UUID resourceId, @Nullable String detail);

    void log(@Nullable UUID userId, @Nullable String action, @Nullable String resourceType, @Nullable UUID resourceId, @Nullable String detail, @Nullable String ipAddress);

    @Nonnull
    AuditLog findById(@Nullable UUID id);

    @Nonnull
    PageResponse<AuditLog> findAll(int page, int size, @Nullable UUID userId, @Nullable String action, @Nullable String resourceType);

    @Nonnull
    PageResponse<AuditLog> findByUserId(@Nullable UUID userId, int page, int size);

    @Nonnull
    PageResponse<AuditLog> findByResource(@Nullable String resourceType, @Nullable UUID resourceId, int page, int size);

    void cleanOldLogs(int daysToKeep);
}