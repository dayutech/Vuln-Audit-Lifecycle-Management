package com.vulnaudit.service.impl;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.Document;
import com.vulnaudit.entity.DocumentAccess;
import com.vulnaudit.entity.DocumentVersion;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.DocumentAccessRepository;
import com.vulnaudit.repository.DocumentRepository;
import com.vulnaudit.service.DocumentService;
import com.vulnaudit.util.Paging;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * 文档服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentAccessRepository documentAccessRepository;
    private final com.vulnaudit.repository.DocumentVersionRepository documentVersionRepository;
    private final org.springframework.transaction.PlatformTransactionManager transactionManager;

    /** 文档版本号冲突的最大尝试次数（含首次） */
    private static final int MAX_DOCUMENT_VERSION_ATTEMPTS = 3;

    @Override
    @Transactional
    public Document createDocument(Document document) {
        log.info("创建文档: title={}", document.getTitle());
        return documentRepository.save(document);
    }

    @Override
    public Optional<Document> findById(@Nullable UUID id) {
        return id != null ? documentRepository.findById(id) : Optional.empty();
    }

    @Override
    public PageResponse<Document> findAll(int page, int size, @Nullable String keyword, @Nullable String category, @Nullable UUID assetId, @Nullable UUID taskId) {
        Pageable pageable = PageRequest.of(Paging.page(page), Paging.size(size), Sort.by("createdAt").descending());
        
        Specification<Document> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            
            if (StringUtils.hasText(keyword)) {
                jakarta.persistence.criteria.Predicate titleLike = cb.like(root.get("title"), "%" + keyword + "%");
                jakarta.persistence.criteria.Predicate contentLike = cb.like(root.get("content"), "%" + keyword + "%");
                predicates.add(cb.or(titleLike, contentLike));
            }
            
            if (StringUtils.hasText(category)) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            
            if (assetId != null) {
                predicates.add(cb.equal(root.get("assetId"), assetId));
            }

            if (taskId != null) {
                predicates.add(cb.equal(root.get("taskId"), taskId));
            }
            
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        
        Page<Document> documentPage = documentRepository.findAll(spec, pageable);
        
        return PageResponse.<Document>builder()
                .content(documentPage.getContent())
                .totalElements(documentPage.getTotalElements())
                .totalPages(documentPage.getTotalPages())
                .currentPage(documentPage.getNumber())
                .pageSize(documentPage.getSize())
                .build();
    }

    @Override
    public List<Document> findByAssetId(@Nullable UUID assetId) {
        return assetId != null ? documentRepository.findByAssetId(assetId) : List.of();
    }

    @Override
    public List<Document> findByTaskId(@Nullable UUID taskId) {
        return taskId != null ? documentRepository.findByTaskId(taskId) : List.of();
    }

    @Override
    public List<Document> findByCategory(@Nullable String category) {
        return category != null ? documentRepository.findByCategory(category) : List.of();
    }

    @Override
    @Transactional
    public Document updateDocument(@Nullable UUID id, Document document) {
        log.info("更新文档: id={}", id);

        if (id == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        }

        Document existingDocument = documentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));

        // 先判断正文是否变化（OSIV 下实体被就地更新，必须在赋值前比较）
        boolean contentChanged = document.getContent() != null
                && !Objects.equals(existingDocument.getContent(), document.getContent());

        if (document.getTitle() != null) {
            existingDocument.setTitle(document.getTitle());
        }
        if (document.getContent() != null) {
            existingDocument.setContent(document.getContent());
            existingDocument.setWordCount(countContentLength(document.getContent()));
        }
        if (document.getSummary() != null) {
            existingDocument.setSummary(document.getSummary());
        }
        if (document.getCategory() != null) {
            existingDocument.setCategory(document.getCategory());
        }

        // 仅在正文变化时递增版本号，保证版本历史与内容变更一一对应
        if (contentChanged) {
            int currentVersion = existingDocument.getVersion() == null ? 1 : existingDocument.getVersion();
            existingDocument.setVersion(currentVersion + 1);
        }

        return documentRepository.save(Objects.requireNonNull(existingDocument));
    }

    /**
     * 统计内容长度（去除 HTML 标签后按字符计），用于列表展示字数。
     */
    private int countContentLength(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        String plain = content.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
        return plain.length();
    }

    /**
     * 回滚到指定历史版本（写入一条新快照，历史不可变）
     */
    @Override
    @Nonnull
    public Document rollbackToVersion(@Nullable UUID id, @Nullable Integer version, @Nullable UUID operatorId) {
        if (id == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        if (version == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "目标版本号不能为空");
        }
        log.info("文档回滚请求: id={}, targetVersion={}, operatorId={}", id, version, operatorId);

        for (int attempt = 1; attempt <= MAX_DOCUMENT_VERSION_ATTEMPTS; attempt++) {
            final int currentAttempt = attempt;
            try {
                Document rolledBack = transactionTemplate().execute(status ->
                        applyRollbackOnce(id, version, operatorId, currentAttempt));
                if (rolledBack != null) {
                    return rolledBack;
                }
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                if (!com.vulnaudit.util.PersistenceErrors.isUniqueViolation(e)) {
                    String detail = com.vulnaudit.util.PersistenceErrors.detail(e);
                    log.warn("文档回滚数据完整性校验失败: id={}, detail={}", id, detail);
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "文档数据不完整或不符合约束: " + detail);
                }
                log.warn("文档回滚版本号冲突，准备重试: documentId={}, attempt={}/{}, cause={}",
                        id, attempt, MAX_DOCUMENT_VERSION_ATTEMPTS,
                        com.vulnaudit.util.PersistenceErrors.detail(e));
                if (attempt < MAX_DOCUMENT_VERSION_ATTEMPTS) {
                    backoffQuietly(attempt);
                }
            }
        }

        log.warn("文档回滚版本号冲突重试耗尽: documentId={}, attempts={}", id, MAX_DOCUMENT_VERSION_ATTEMPTS);
        throw new BusinessException(ErrorCode.CONFLICT, "文档版本冲突，请重试");
    }

    /**
     * 单次回滚尝试（独立事务）
     *
     * <p>行级锁串行化 → 读取目标历史版本（只读，不修改历史）→ 计算 max+1 →
     * 更新 documents 当前内容与版本 → saveAndFlush → 写入新快照（冲突点）。
     */
    private Document applyRollbackOnce(UUID id, Integer targetVersion, UUID operatorId, int attempt) {
        Document document = documentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));

        DocumentVersion target = documentVersionRepository.findByDocumentIdAndVersion(id, targetVersion)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "版本不存在: v" + targetVersion));

        // 版本号以「历史最大版本 + 1」为基准，保证连续、不跳号
        int maxVersion = documentVersionRepository.findLatestVersion(id)
                .map(latest -> latest.getVersion() == null ? 0 : latest.getVersion())
                .orElse(0);
        int currentDocumentVersion = document.getVersion() == null ? 0 : document.getVersion();
        int nextVersion = Math.max(maxVersion, currentDocumentVersion) + 1;

        // 历史不可变：只读取 target 的内容，不回写 target 实体
        document.setTitle(target.getTitle());
        document.setSummary(target.getSummary());
        document.setContent(target.getContent());
        document.setWordCount(countContentLength(target.getContent()));
        document.setVersion(nextVersion);

        log.debug("执行文档回滚: id={}, fromVersion={}, newVersion={}, attempt={}",
                id, targetVersion, nextVersion, attempt);

        Document saved = documentRepository.saveAndFlush(Objects.requireNonNull(document));

        documentVersionRepository.saveAndFlush(DocumentVersion.builder()
                .documentId(id)
                .version(nextVersion)
                .title(target.getTitle())
                .summary(target.getSummary())
                .content(target.getContent())
                // 追溯来源：who rollback to which version
                .changelog("rollback from v" + targetVersion)
                .createdBy(operatorId != null ? operatorId : saved.getAuthorId())
                .build());

        return saved;
    }

    @Override
    @Nonnull
    public Document updateDocumentWithSnapshot(@Nullable UUID id, @Nonnull Document patch, @Nullable UUID operatorId) {
        if (id == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        log.info("更新文档（含版本快照）: id={}, operatorId={}", id, operatorId);

        for (int attempt = 1; attempt <= MAX_DOCUMENT_VERSION_ATTEMPTS; attempt++) {
            final int currentAttempt = attempt;
            try {
                Document updated = transactionTemplate().execute(status ->
                        applyUpdateWithSnapshotOnce(id, patch, operatorId, currentAttempt));
                if (updated != null) {
                    return updated;
                }
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                if (!com.vulnaudit.util.PersistenceErrors.isUniqueViolation(e)) {
                    String detail = com.vulnaudit.util.PersistenceErrors.detail(e);
                    log.warn("文档数据完整性校验失败: id={}, detail={}", id, detail);
                    throw new BusinessException(ErrorCode.BAD_REQUEST, "文档数据不完整或不符合约束: " + detail);
                }
                log.warn("文档版本号冲突，准备重试: documentId={}, attempt={}/{}, cause={}",
                        id, attempt, MAX_DOCUMENT_VERSION_ATTEMPTS,
                        com.vulnaudit.util.PersistenceErrors.detail(e));
                if (attempt < MAX_DOCUMENT_VERSION_ATTEMPTS) {
                    backoffQuietly(attempt);
                }
            }
        }

        log.warn("文档版本号冲突重试耗尽: documentId={}, attempts={}", id, MAX_DOCUMENT_VERSION_ATTEMPTS);
        throw new BusinessException(ErrorCode.CONFLICT, "文档版本冲突，请重试");
    }

    /**
     * 单次尝试（独立事务）：行级锁 → 应用变更 → 递增版本号 → saveAndFlush → 写快照
     *
     * <p>{@code (document_id, version)} 唯一键冲突会在 {@code saveAndFlush} 时抛出，由外层有界重试处理。
     */
    private Document applyUpdateWithSnapshotOnce(UUID id, Document patch, UUID operatorId, int attempt) {
        // 行级锁：同一文档的版本号分配串行化
        Document existingDocument = documentRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));

        boolean contentChanged = patch.getContent() != null
                && !Objects.equals(existingDocument.getContent(), patch.getContent());

        if (patch.getTitle() != null) {
            existingDocument.setTitle(patch.getTitle());
        }
        if (patch.getContent() != null) {
            existingDocument.setContent(patch.getContent());
            existingDocument.setWordCount(countContentLength(patch.getContent()));
        }
        if (patch.getSummary() != null) {
            existingDocument.setSummary(patch.getSummary());
        }
        if (patch.getCategory() != null) {
            existingDocument.setCategory(patch.getCategory());
        }

        int currentVersion = existingDocument.getVersion() == null ? 1 : existingDocument.getVersion();
        if (contentChanged) {
            existingDocument.setVersion(currentVersion + 1);
        }

        log.debug("尝试保存文档: id={}, willSnapshot={}, targetVersion={}, attempt={}",
                id, contentChanged, existingDocument.getVersion(), attempt);

        Document saved = documentRepository.saveAndFlush(Objects.requireNonNull(existingDocument));

        if (contentChanged) {
            UUID createdBy = operatorId != null ? operatorId : saved.getAuthorId();
            documentVersionRepository.saveAndFlush(com.vulnaudit.entity.DocumentVersion.builder()
                    .documentId(id)
                    .version(saved.getVersion())
                    .title(saved.getTitle())
                    .summary(saved.getSummary())
                    .content(saved.getContent())
                    .changelog("编辑保存")
                    .createdBy(createdBy)
                    .build());
        }

        return saved;
    }

    private org.springframework.transaction.support.TransactionTemplate transactionTemplate() {
        return new org.springframework.transaction.support.TransactionTemplate(transactionManager);
    }

    private void backoffQuietly(int attempt) {
        long sleepMs = attempt * 15L + java.util.concurrent.ThreadLocalRandom.current().nextLong(20);
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    @Transactional
    public void deleteDocument(@Nullable UUID id) {
        log.info("删除文档: id={}", id);
        if (id == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        documentRepository.deleteById(id);
    }

    @Override
    public boolean existsByTitle(@Nullable String title) {
        return title != null && documentRepository.existsByTitle(title);
    }

    @Override
    public List<Document> search(@Nullable String keyword) {
        return keyword != null ? documentRepository.search(keyword) : List.of();
    }

    @Override
    @Transactional
    public void grantAccess(@Nullable UUID documentId, @Nullable UUID userId, @Nullable String accessType) {
        log.info("授予文档访问权限: documentId={}, userId={}, accessType={}", documentId, userId, accessType);

        if (documentId == null || userId == null) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        }

        DocumentAccess access = DocumentAccess.builder()
                .documentId(documentId)
                .userId(userId)
                .accessType(accessType)
                .build();
        documentAccessRepository.save(Objects.requireNonNull(access));
    }

    @Override
    @Transactional
    public void revokeAccess(@Nullable UUID documentId, @Nullable UUID userId) {
        log.info("撤销文档访问权限: documentId={}, userId={}", documentId, userId);
        if (documentId != null && userId != null) {
            documentAccessRepository.deleteByDocumentIdAndUserId(documentId, userId);
        }
    }

    @Override
    public boolean hasAccess(@Nullable UUID documentId, @Nullable UUID userId, @Nullable String accessType) {
        if (documentId == null || userId == null) {
            return false;
        }
        return documentAccessRepository.existsByDocumentIdAndUserIdAndAccessType(documentId, userId, accessType);
    }

    @Override
    public List<Document> findByCreatedBy(@Nullable UUID createdBy) {
        return createdBy != null ? documentRepository.findByCreatedBy(createdBy) : List.of();
    }
}
