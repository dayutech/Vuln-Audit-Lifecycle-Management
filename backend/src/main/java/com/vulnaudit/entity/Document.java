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
 * 文档实体类
 * 
 * 功能说明：
 * - 映射数据库 documents 表
 * - 存储文档基本信息
 * - 支持文档版本管理
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "documents")
public class Document {

    @Id
    @GeneratedValue(generator = "UUID")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "title", length = 500, nullable = false)
    private String title;

    @Column(name = "category", length = 50, nullable = false)
    private String category;

    @Column(name = "summary", length = 200)
    private String summary;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "asset_id")
    private UUID assetId;

    @Column(name = "task_id")
    private UUID taskId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "draft";

    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Column(name = "word_count")
    @Builder.Default
    private Integer wordCount = 0;

    @Column(name = "is_published", nullable = false)
    @Builder.Default
    private Boolean isPublished = false;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        log.debug("创建文档实体: title={}", title);
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        log.debug("更新文档实体: id={}", id);
    }

}
