package com.vulnaudit.service.impl;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.AuditLog;
import com.vulnaudit.repository.AuditLogRepository;
import com.vulnaudit.service.AuditLogService;
import com.vulnaudit.util.Paging;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import jakarta.annotation.Nullable;

/**
 * 审计日志服务实现类
 *
 * <p>查询用 JPA Specification 动态拼条件（等值 + 时间闭区间 + 关键词 OR 模糊），
 * 分页参数统一经 {@link Paging} 收敛；导出为 UTF-8 + BOM 的 CSV，与仪表盘导出口径一致。
 * 过期清理返回实际删除条数，供定时任务与运维核查。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    /** CSV 表头（与导出行保持同序） */
    private static final String CSV_HEADER = "时间,用户ID,操作,资源类型,资源ID,IP地址,详情";

    /** 时间列输出格式：秒级、无时区，Excel 可直接识别 */
    private static final DateTimeFormatter CSV_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.ROOT);

    private final AuditLogRepository auditLogRepository;

    @Override
    @Async
    @Transactional
    public void log(UUID userId, String action, String resourceType, UUID resourceId, String detail) {
        log(userId, action, resourceType, resourceId, detail, null);
    }

    @Override
    @Async
    @Transactional
    public void log(UUID userId, String action, String resourceType, UUID resourceId, String detail, String ipAddress) {
        log.info("记录审计日志: userId={}, action={}, resourceType={}, resourceId={}",
                userId, action, resourceType, resourceId);

        AuditLog auditLog = AuditLog.builder()
                .userId(userId)
                .action(action)
                .resourceType(resourceType)
                .resourceId(resourceId)
                .details(detail)
                .ipAddress(ipAddress)
                .build();

        auditLogRepository.save(Objects.requireNonNull(auditLog));
    }

    @Override
    public AuditLog findById(@Nullable UUID id) {
        if (id == null) {
            return null;
        }
        Optional<AuditLog> auditLog = auditLogRepository.findById(id);
        return auditLog.map(Objects::requireNonNull).orElse(null);
    }

    @Override
    public PageResponse<AuditLog> findAll(int page, int size,
                                          @Nullable UUID userId,
                                          @Nullable String action,
                                          @Nullable String resourceType,
                                          @Nullable LocalDateTime startTime,
                                          @Nullable LocalDateTime endTime,
                                          @Nullable String keyword) {
        Pageable pageable = PageRequest.of(Paging.page(page), Paging.size(size), Sort.by("createdAt").descending());
        Page<AuditLog> logPage = auditLogRepository.findAll(buildSpec(userId, action, resourceType,
                startTime, endTime, keyword), pageable);
        return toPageResponse(logPage);
    }

    @Override
    public PageResponse<AuditLog> findByUserId(UUID userId, int page, int size) {
        Pageable pageable = PageRequest.of(Paging.page(page), Paging.size(size), Sort.by("createdAt").descending());
        Page<AuditLog> logPage = auditLogRepository.findByUserId(userId, pageable);
        return toPageResponse(logPage);
    }

    @Override
    public PageResponse<AuditLog> findByResource(String resourceType, UUID resourceId, int page, int size) {
        Pageable pageable = PageRequest.of(Paging.page(page), Paging.size(size), Sort.by("createdAt").descending());
        Page<AuditLog> logPage = auditLogRepository.findByResourceTypeAndResourceId(resourceType, resourceId, pageable);
        return toPageResponse(logPage);
    }

    @Override
    public byte[] exportCsv(@Nullable UUID userId,
                            @Nullable String action,
                            @Nullable String resourceType,
                            @Nullable LocalDateTime startTime,
                            @Nullable LocalDateTime endTime,
                            @Nullable String keyword) {
        Pageable pageable = PageRequest.of(0, EXPORT_LIMIT, Sort.by("createdAt").descending());
        List<AuditLog> logs = auditLogRepository.findAll(
                buildSpec(userId, action, resourceType, startTime, endTime, keyword), pageable).getContent();

        StringBuilder sb = new StringBuilder(CSV_HEADER).append('\n');
        for (AuditLog item : logs) {
            sb.append(csv(formatTime(item.getCreatedAt()))).append(',')
                    .append(csv(stringify(item.getUserId()))).append(',')
                    .append(csv(item.getAction())).append(',')
                    .append(csv(item.getResourceType())).append(',')
                    .append(csv(stringify(item.getResourceId()))).append(',')
                    .append(csv(item.getIpAddress())).append(',')
                    .append(csv(item.getDetails())).append('\n');
        }

        log.info("导出审计日志: 条数={}", logs.size());
        return withUtf8Bom(sb.toString());
    }

    @Override
    @Transactional
    public int cleanOldLogs(int daysToKeep) {
        int days = daysToKeep > 0 ? daysToKeep : 180;
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(days);
        int deleted = auditLogRepository.deleteByCreatedAtBefore(cutoffDate);
        log.info("清理过期审计日志: 保留天数={}, 截止时间={}, 清理条数={}", days, cutoffDate, deleted);
        return deleted;
    }

    // ------------------------------------------------------------------ 内部工具

    /**
     * 动态条件：等值筛选 + 时间闭区间 + 关键词模糊（action / resourceType / details 任一命中）
     */
    private Specification<AuditLog> buildSpec(@Nullable UUID userId,
                                              @Nullable String action,
                                              @Nullable String resourceType,
                                              @Nullable LocalDateTime startTime,
                                              @Nullable LocalDateTime endTime,
                                              @Nullable String keyword) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            if (StringUtils.hasText(action)) {
                predicates.add(cb.equal(root.get("action"), action.trim()));
            }
            if (StringUtils.hasText(resourceType)) {
                predicates.add(cb.equal(root.get("resourceType"), resourceType.trim()));
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), endTime));
            }
            if (StringUtils.hasText(keyword)) {
                String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("action")), like),
                        cb.like(cb.lower(root.get("resourceType")), like),
                        cb.like(cb.lower(root.get("details")), like)
                ));
            }

            return predicates.isEmpty()
                    ? cb.conjunction()
                    : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private PageResponse<AuditLog> toPageResponse(Page<AuditLog> logPage) {
        return PageResponse.<AuditLog>builder()
                .content(logPage.getContent())
                .totalElements(logPage.getTotalElements())
                .totalPages(logPage.getTotalPages())
                .currentPage(logPage.getNumber())
                .pageSize(logPage.getSize())
                .build();
    }

    private String formatTime(@Nullable LocalDateTime time) {
        return time == null ? "" : CSV_TIME_FORMAT.format(time);
    }

    private String stringify(@Nullable UUID value) {
        return value == null ? "" : value.toString();
    }

    /**
     * CSV 字段转义：换行折成空格，含逗号或引号时加引号包裹并将内部引号翻倍
     */
    private String csv(@Nullable String value) {
        if (value == null) {
            return "";
        }
        String cleaned = value.replace("\r", " ").replace("\n", " ");
        if (cleaned.contains(",") || cleaned.contains("\"")) {
            return "\"" + cleaned.replace("\"", "\"\"") + "\"";
        }
        return cleaned;
    }

    /**
     * 追加 UTF-8 BOM，保证 Excel 打开中文不乱码（与仪表盘导出一致）
     */
    private byte[] withUtf8Bom(String content) {
        byte[] bom = new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF};
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[bom.length + body.length];
        System.arraycopy(bom, 0, result, 0, bom.length);
        System.arraycopy(body, 0, result, bom.length, body.length);
        return result;
    }
}
