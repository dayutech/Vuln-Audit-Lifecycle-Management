package com.vulnaudit.service.impl;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.Document;
import com.vulnaudit.entity.DocumentAccess;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.DocumentAccessRepository;
import com.vulnaudit.repository.DocumentRepository;
import com.vulnaudit.service.DocumentService;
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
import java.util.Optional;
import java.util.UUID;

/**
 * 文档服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentAccessRepository documentAccessRepository;

    @Override
    @Transactional
    public Document createDocument(Document document) {
        log.info("创建文档: title={}", document.getTitle());
        return documentRepository.save(document);
    }

    @Override
    public Optional<Document> findById(UUID id) {
        return documentRepository.findById(id);
    }

    @Override
    public PageResponse<Document> findAll(int page, int size, String keyword, String category, UUID assetId) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        
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
    public List<Document> findByAssetId(UUID assetId) {
        return documentRepository.findByAssetId(assetId);
    }

    @Override
    public List<Document> findByCategory(String category) {
        return documentRepository.findByCategory(category);
    }

    @Override
    @Transactional
    public Document updateDocument(UUID id, Document document) {
        log.info("更新文档: id={}", id);
        
        Document existingDocument = documentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
        
        if (document.getTitle() != null) {
            existingDocument.setTitle(document.getTitle());
        }
        if (document.getContent() != null) {
            existingDocument.setContent(document.getContent());
        }
        if (document.getSummary() != null) {
            existingDocument.setSummary(document.getSummary());
        }
        if (document.getCategory() != null) {
            existingDocument.setCategory(document.getCategory());
        }
        
        return documentRepository.save(existingDocument);
    }

    @Override
    @Transactional
    public void deleteDocument(UUID id) {
        log.info("删除文档: id={}", id);
        documentRepository.deleteById(id);
    }

    @Override
    public boolean existsByTitle(String title) {
        return documentRepository.existsByTitle(title);
    }

    @Override
    public List<Document> search(String keyword) {
        return documentRepository.search(keyword);
    }

    @Override
    @Transactional
    public void grantAccess(UUID documentId, UUID userId, String accessType) {
        log.info("授予文档访问权限: documentId={}, userId={}, accessType={}", documentId, userId, accessType);
        
        DocumentAccess access = DocumentAccess.builder()
                .documentId(documentId)
                .userId(userId)
                .accessType(accessType)
                .build();
        documentAccessRepository.save(access);
    }

    @Override
    @Transactional
    public void revokeAccess(UUID documentId, UUID userId) {
        log.info("撤销文档访问权限: documentId={}, userId={}", documentId, userId);
        documentAccessRepository.deleteByDocumentIdAndUserId(documentId, userId);
    }

    @Override
    public boolean hasAccess(UUID documentId, UUID userId, String accessType) {
        return documentAccessRepository.existsByDocumentIdAndUserIdAndAccessType(documentId, userId, accessType);
    }

    @Override
    public List<Document> findByCreatedBy(UUID createdBy) {
        return documentRepository.findByCreatedBy(createdBy);
    }
}
