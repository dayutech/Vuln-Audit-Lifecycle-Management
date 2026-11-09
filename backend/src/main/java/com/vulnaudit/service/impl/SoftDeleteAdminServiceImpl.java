package com.vulnaudit.service.impl;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.dto.SoftDeleteAdminItem;
import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.Customer;
import com.vulnaudit.entity.Project;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.CustomerRepository;
import com.vulnaudit.repository.ProjectRepository;
import com.vulnaudit.service.AuditLogService;
import com.vulnaudit.service.SoftDeleteAdminService;
import com.vulnaudit.util.Paging;
import jakarta.annotation.Nullable;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;
import java.util.function.Function;

/**
 * 软删管理实现（t140）
 *
 * <p><b>为什么用原生 SQL</b>：三个实体都带 {@code @SQLRestriction("deleted_at IS NULL")}，
 * JPQL / 派生查询一律看不到已删行；「含已删列表」与「恢复」必须绕过该限制，因此走各仓库里的
 * {@code searchAdmin(...)} 与 {@code restoreById(...)} 原生语句，并在恢复后 {@link EntityManager#clear()}
 * 清一级缓存，避免同一事务内再读到旧的（已删）快照。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoftDeleteAdminServiceImpl implements SoftDeleteAdminService {

    private final CustomerRepository customerRepository;
    private final ProjectRepository projectRepository;
    private final AssetRepository assetRepository;
    private final AuditLogService auditLogService;
    private final EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<SoftDeleteAdminItem> page(Kind kind, @Nullable String keyword, boolean includeDeleted,
                                                  int page, int size) {
        int safePage = Paging.page(page);
        int safeSize = Paging.size(size);
        String kw = keyword == null ? "" : keyword.trim();
        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "created_at"));

        return switch (kind) {
            case CUSTOMER -> toPage(customerRepository.searchAdmin(kw, includeDeleted, pageable),
                    c -> new SoftDeleteAdminItem(c.getId(), c.getCode(), c.getName(),
                            c.getDeletedAt(), c.getDeletedBy()), safePage, safeSize);
            case PROJECT -> toPage(projectRepository.searchAdmin(kw, includeDeleted, pageable),
                    p -> new SoftDeleteAdminItem(p.getId(), p.getCode(), p.getName(),
                            p.getDeletedAt(), p.getDeletedBy()), safePage, safeSize);
            case ASSET -> toPage(assetRepository.searchAdmin(kw, includeDeleted, pageable),
                    a -> new SoftDeleteAdminItem(a.getId(), a.getCode(), a.getName(),
                            a.getDeletedAt(), a.getDeletedBy()), safePage, safeSize);
        };
    }

    private <T> PageResponse<SoftDeleteAdminItem> toPage(Page<T> page, Function<T, SoftDeleteAdminItem> mapper,
                                                        int safePage, int safeSize) {
        return PageResponse.<SoftDeleteAdminItem>builder()
                .content(page.getContent().stream().map(mapper).toList())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .currentPage(safePage)
                .pageSize(safeSize)
                .build();
    }

    @Override
    @Transactional
    public boolean restore(Kind kind, @Nullable UUID id, @Nullable UUID operatorId) {
        if (id == null) {
            throw new BusinessException(notFoundCode(kind));
        }
        // 含已删地确认对象存在（否则 404，不泄露「曾存在但已删」之外的信息）
        boolean exists = switch (kind) {
            case CUSTOMER -> customerRepository.findByIdIncludingDeleted(id).isPresent();
            case PROJECT -> projectRepository.findByIdIncludingDeleted(id).isPresent();
            case ASSET -> assetRepository.findByIdIncludingDeleted(id).isPresent();
        };
        if (!exists) {
            throw new BusinessException(notFoundCode(kind));
        }

        int rows = switch (kind) {
            case CUSTOMER -> customerRepository.restoreById(id);
            case PROJECT -> projectRepository.restoreById(id);
            case ASSET -> assetRepository.restoreById(id);
        };
        // 原生 UPDATE 不会刷新一级缓存：清掉，保证同一事务后续读取看到「已恢复」的状态
        entityManager.clear();

        if (rows == 0) {
            log.info("恢复软删对象：本来就未删（幂等返回）: kind={}, id={}", kind, id);
            return false;
        }
        auditLogService.log(operatorId, auditAction(kind), resourceType(kind), id,
                "恢复软删对象: " + kind + " id=" + id);
        log.info("恢复软删对象: kind={}, id={}, operatorId={}", kind, id, operatorId);
        return true;
    }

    private static ErrorCode notFoundCode(Kind kind) {
        return switch (kind) {
            case CUSTOMER -> ErrorCode.CUSTOMER_NOT_FOUND;
            case PROJECT -> ErrorCode.PROJECT_NOT_FOUND;
            case ASSET -> ErrorCode.ASSET_NOT_FOUND;
        };
    }

    private static String auditAction(Kind kind) {
        return switch (kind) {
            case CUSTOMER -> "RESTORE_CUSTOMER";
            case PROJECT -> "RESTORE_PROJECT";
            case ASSET -> "RESTORE_ASSET";
        };
    }

    private static String resourceType(Kind kind) {
        return switch (kind) {
            case CUSTOMER -> "customer";
            case PROJECT -> "project";
            case ASSET -> "asset";
        };
    }
}
