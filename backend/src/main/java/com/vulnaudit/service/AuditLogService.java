package com.vulnaudit.service;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.AuditLog;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 审计日志服务接口
 *
 * <p>职责：写入审计（供拦截器与需要更丰富上下文的控制器/服务调用）、多维查询、
 * CSV 导出与过期清理。
 *
 * <p>查询口径（{@link #findAll(int, int, UUID, String, String, LocalDateTime, LocalDateTime, String)}）：
 * <ul>
 *   <li>{@code userId} / {@code action} / {@code resourceType}：等值筛选；</li>
 *   <li>{@code startTime} / {@code endTime}：按 {@code created_at} 的闭区间过滤，均为可选；</li>
 *   <li>{@code keyword}：模糊匹配，命中操作、资源类型或详情任一即可。</li>
 * </ul>
 */
public interface AuditLogService {

    /** 导出上限（防止一次导出拖垮进程内存） */
    int EXPORT_LIMIT = 10_000;

    void log(@Nullable UUID userId, @Nullable String action, @Nullable String resourceType, @Nullable UUID resourceId, @Nullable String detail);

    void log(@Nullable UUID userId, @Nullable String action, @Nullable String resourceType, @Nullable UUID resourceId, @Nullable String detail, @Nullable String ipAddress);

    @Nonnull
    AuditLog findById(@Nullable UUID id);

    /**
     * 分页查询审计日志（支持用户/操作/资源类型/时间范围/关键词）
     *
     * @param page         页码（从 0 开始，越界由 Paging 收敛）
     * @param size         每页大小（由 Paging 收敛）
     * @param userId       用户ID，可选
     * @param action       操作，可选（等值）
     * @param resourceType 资源类型，可选（等值）
     * @param startTime    起始时间（含），可选
     * @param endTime      结束时间（含），可选
     * @param keyword      关键词，可选（模糊匹配 action / resourceType / details）
     * @return 分页结果
     */
    @Nonnull
    PageResponse<AuditLog> findAll(int page, int size,
                                   @Nullable UUID userId,
                                   @Nullable String action,
                                   @Nullable String resourceType,
                                   @Nullable LocalDateTime startTime,
                                   @Nullable LocalDateTime endTime,
                                   @Nullable String keyword);

    @Nonnull
    PageResponse<AuditLog> findByUserId(@Nullable UUID userId, int page, int size);

    @Nonnull
    PageResponse<AuditLog> findByResource(@Nullable String resourceType, @Nullable UUID resourceId, int page, int size);

    /**
     * 导出审计日志为 CSV（UTF-8 + BOM，Excel 可直接打开）
     *
     * <p>筛选条件与列表接口一致；最多导出 {@link #EXPORT_LIMIT} 条，按时间倒序。
     *
     * @return CSV 字节流（首三字节为 BOM，首行为表头）
     */
    @Nonnull
    byte[] exportCsv(@Nullable UUID userId,
                     @Nullable String action,
                     @Nullable String resourceType,
                     @Nullable LocalDateTime startTime,
                     @Nullable LocalDateTime endTime,
                     @Nullable String keyword);

    /**
     * 清理早于保留期的审计日志
     *
     * @param daysToKeep 保留天数
     * @return 实际删除的条数
     */
    int cleanOldLogs(int daysToKeep);
}
