package com.vulnaudit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.GenericGenerator;

import java.util.UUID;

/**
 * 知识标签实体类
 * 
 * 功能说明：
 * - 为知识文章添加标签分类
 * - 支持多维度标签检索
 */
@Slf4j
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "knowledge_tags")
public class KnowledgeTag {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * 外键，关联knowledge_articles表
     */
    @Column(name = "article_id", nullable = false)
    private UUID articleId;

    /**
     * 标签名称
     */
    @Column(name = "tag", nullable = false, length = 50)
    private String tag;

    @PrePersist
    protected void onCreate() {
        log.debug("创建知识标签: articleId={}, tag={}", articleId, tag);
    }
}
