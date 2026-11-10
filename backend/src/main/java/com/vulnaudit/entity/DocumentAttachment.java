package com.vulnaudit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 知识库 / 文档附件（t162）
 *
 * <p>取代前端「正文内 base64 附件块」：文件独立落盘（UUID 前缀），DB 只存元数据；
 * **没有按 storage_path 直接下载的端点**，下载必须经归属校验的接口。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "document_attachments",
        indexes = {
                @Index(name = "idx_document_attachments_owner", columnList = "owner_type,owner_id"),
                @Index(name = "idx_document_attachments_deleted", columnList = "deleted_at")
        })
public class DocumentAttachment {

    public static final String OWNER_KNOWLEDGE = "KNOWLEDGE";
    public static final String OWNER_DOCUMENT = "DOCUMENT";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** KNOWLEDGE | DOCUMENT */
    @Column(name = "owner_type", length = 20, nullable = false)
    private String ownerType;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    /** 净化后的原文件名（下载时回给客户端，支持中文） */
    @Column(name = "file_name", length = 300, nullable = false)
    private String fileName;

    /** 磁盘名（uuid + 原文件名） */
    @Column(name = "stored_name", length = 400, nullable = false)
    private String storedName;

    @Column(name = "content_type", length = 200)
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "storage_path", length = 1000, nullable = false)
    private String storagePath;

    @Column(name = "uploaded_by")
    private UUID uploadedBy;

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (uploadedAt == null) {
            uploadedAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
