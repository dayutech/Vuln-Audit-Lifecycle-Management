package com.vulnaudit.service.impl;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.AuditLog;
import com.vulnaudit.repository.AuditLogRepository;
import com.vulnaudit.service.AuditLogService;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import jakarta.annotation.Nullable;

/**
 * 审计日志服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

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
    public PageResponse<AuditLog> findAll(int page, int size, @Nullable UUID userId, @Nullable String action, @Nullable String resourceType) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        
        Specification<AuditLog> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            
            if (action != null && !action.isEmpty()) {
                predicates.add(cb.equal(root.get("action"), action));
            }
            
            if (resourceType != null && !resourceType.isEmpty()) {
                predicates.add(cb.equal(root.get("resourceType"), resourceType));
            }
            
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        
        Page<AuditLog> logPage = auditLogRepository.findAll(spec, pageable);
        
        return PageResponse.<AuditLog>builder()
                .content(logPage.getContent())
                .totalElements(logPage.getTotalElements())
                .totalPages(logPage.getTotalPages())
                .currentPage(logPage.getNumber())
                .pageSize(logPage.getSize())
                .build();
    }

    @Override
    public PageResponse<AuditLog> findByUserId(UUID userId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<AuditLog> logPage = auditLogRepository.findByUserId(userId, pageable);
        
        return PageResponse.<AuditLog>builder()
                .content(logPage.getContent())
                .totalElements(logPage.getTotalElements())
                .totalPages(logPage.getTotalPages())
                .currentPage(logPage.getNumber())
                .pageSize(logPage.getSize())
                .build();
    }

    @Override
    public PageResponse<AuditLog> findByResource(String resourceType, UUID resourceId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<AuditLog> logPage = auditLogRepository.findByResourceTypeAndResourceId(resourceType, resourceId, pageable);
        
        return PageResponse.<AuditLog>builder()
                .content(logPage.getContent())
                .totalElements(logPage.getTotalElements())
                .totalPages(logPage.getTotalPages())
                .currentPage(logPage.getNumber())
                .pageSize(logPage.getSize())
                .build();
    }

    @Override
    @Transactional
    public void cleanOldLogs(int daysToKeep) {
        log.info("清理过期审计日志: daysToKeep={}", daysToKeep);
        
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysToKeep);
        auditLogRepository.deleteByCreatedAtBefore(cutoffDate);
    }
}
