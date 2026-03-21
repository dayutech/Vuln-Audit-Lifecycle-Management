package com.vulnaudit.service;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.KnowledgeArticle;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 知识库服务接口
 */
public interface KnowledgeService {

    KnowledgeArticle createArticle(KnowledgeArticle article);

    Optional<KnowledgeArticle> findById(UUID id);

    PageResponse<KnowledgeArticle> findAll(int page, int size, String keyword, String category);

    List<KnowledgeArticle> findByAuthorId(UUID authorId);

    List<KnowledgeArticle> findByCategory(String category);

    List<KnowledgeArticle> findPopular(int limit);

    KnowledgeArticle updateArticle(UUID id, KnowledgeArticle article);

    void deleteArticle(UUID id);

    void incrementViewCount(UUID id);

    void addComment(UUID articleId, UUID userId, String content);

    void addTag(UUID articleId, String tag);

    void removeTag(UUID articleId, String tag);

    List<String> getTags(UUID articleId);

    List<KnowledgeArticle> search(String keyword);
}
