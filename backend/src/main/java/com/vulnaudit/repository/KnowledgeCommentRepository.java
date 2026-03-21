package com.vulnaudit.repository;

import com.vulnaudit.entity.KnowledgeComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * 知识评论Repository接口
 */
@Repository
public interface KnowledgeCommentRepository extends JpaRepository<KnowledgeComment, UUID> {

    /**
     * 按文章ID查询评论列表
     */
    List<KnowledgeComment> findByArticleIdOrderByCreatedAtDesc(UUID articleId);

    /**
     * 按用户ID查询评论列表
     */
    List<KnowledgeComment> findByUserIdOrderByCreatedAtDesc(UUID userId);

    /**
     * 统计文章评论数量
     */
    long countByArticleId(UUID articleId);
}
