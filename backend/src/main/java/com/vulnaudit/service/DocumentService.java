package com.vulnaudit.service;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.Document;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 文档服务接口
 */
public interface DocumentService {

    Document createDocument(Document document);

    Optional<Document> findById(UUID id);

    PageResponse<Document> findAll(int page, int size, String keyword, String category, UUID assetId);

    List<Document> findByAssetId(UUID assetId);

    List<Document> findByCategory(String category);

    Document updateDocument(UUID id, Document document);

    void deleteDocument(UUID id);

    boolean existsByTitle(String title);

    List<Document> search(String keyword);

    void grantAccess(UUID documentId, UUID userId, String accessType);

    void revokeAccess(UUID documentId, UUID userId);

    boolean hasAccess(UUID documentId, UUID userId, String accessType);

    List<Document> findByCreatedBy(UUID createdBy);
}
