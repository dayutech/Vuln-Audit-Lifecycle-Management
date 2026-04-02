package com.vulnaudit.service;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.KnowledgeArticle;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 知识库服务接口
 */
public interface KnowledgeService {

    @Nonnull
    KnowledgeArticle createArticle(@Nonnull KnowledgeArticle article);

    Optional<KnowledgeArticle> findById(@Nullable UUID id);

    @Nonnull
    PageResponse<KnowledgeArticle> findAll(int page, int size, @Nullable String keyword, @Nullable String category);

    @Nonnull
    List<KnowledgeArticle> findByAuthorId(@Nullable UUID authorId);

    @Nonnull
    List<KnowledgeArticle> findByCategory(@Nullable String category);

    @Nonnull
    List<KnowledgeArticle> findPopular(int limit);

    @Nonnull
    KnowledgeArticle updateArticle(@Nullable UUID id, @Nonnull KnowledgeArticle article);

    void deleteArticle(@Nullable UUID id);

    void incrementViewCount(@Nullable UUID id);

    void addComment(@Nullable UUID articleId, @Nullable UUID userId, @Nullable String content);

    void addTag(@Nullable UUID articleId, @Nullable String tag);

    void removeTag(@Nullable UUID articleId, @Nullable String tag);

    @Nonnull
    List<String> getTags(@Nullable UUID articleId);

    @Nonnull
    List<KnowledgeArticle> search(@Nullable String keyword);
}