package com.vulnaudit.service;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.Document;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 文档服务接口
 */
public interface DocumentService {

    @Nonnull
    Document createDocument(@Nonnull Document document);

    Optional<Document> findById(@Nullable UUID id);

    @Nonnull
    PageResponse<Document> findAll(int page, int size, @Nullable String keyword, @Nullable String category, @Nullable UUID assetId);

    @Nonnull
    List<Document> findByAssetId(@Nullable UUID assetId);

    @Nonnull
    List<Document> findByCategory(@Nullable String category);

    @Nonnull
    Document updateDocument(@Nullable UUID id, @Nonnull Document document);

    void deleteDocument(@Nullable UUID id);

    boolean existsByTitle(@Nullable String title);

    @Nonnull
    List<Document> search(@Nullable String keyword);

    void grantAccess(@Nullable UUID documentId, @Nullable UUID userId, @Nullable String accessType);

    void revokeAccess(@Nullable UUID documentId, @Nullable UUID userId);

    boolean hasAccess(@Nullable UUID documentId, @Nullable UUID userId, @Nullable String accessType);

    @Nonnull
    List<Document> findByCreatedBy(@Nullable UUID createdBy);
}