package com.vulnaudit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 知识评论实体类
 * 
 * 功能说明：
 * - 记录知识文章的评论信息
 * - 支持知识讨论和交流
 */
@Slf4j
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "knowledge_comments")
public class KnowledgeComment {

    @Id
    @GeneratedValue(generator = "UUID")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * 外键，关联knowledge_articles表
     */
    @Column(name = "article_id", nullable = false)
    private UUID articleId;

    /**
     * 外键，关联users表
     */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * 评论内容
     */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        log.debug("创建知识评论: articleId={}, userId={}", articleId, userId);
    }
}
