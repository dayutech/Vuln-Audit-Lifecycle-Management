package com.vulnaudit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 大文件上传审批请求（t160）
 *
 * <p>状态机：`PENDING` →（管理员批准）`APPROVED` /（管理员拒绝）`REJECTED` /（过期清理）`EXPIRED`。
 *
 * <p>**不持久化的严格性**：PENDING/REJECTED 期间文件只存在于临时区（{@link #storageKey}），
 * 业务表里没有对应记录，所以不会出现在任何列表/下载/统计中。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "upload_approval_requests",
        indexes = {
                @Index(name = "idx_upload_approval_status", columnList = "status"),
                @Index(name = "idx_upload_approval_requester", columnList = "requested_by"),
                @Index(name = "idx_upload_approval_purge", columnList = "status,purge_after")
        })
public class UploadApprovalRequest {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_APPROVED = "APPROVED";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_EXPIRED = "EXPIRED";

    public static final String TARGET_TOOL = "TOOL";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** TOOL / COMPONENT / KNOWLEDGE / DOCUMENT */
    @Column(name = "target_type", length = 20, nullable = false)
    private String targetType;

    /** 批准后写入的业务实体 id（新建时为空） */
    @Column(name = "target_id")
    private UUID targetId;

    @Column(name = "file_name", length = 300, nullable = false)
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    /** t165：用户填写的显示名（批准时优先用它做业务实体名） */
    @Column(name = "display_name", length = 200)
    private String displayName;

    /** t165：用户填写的描述（批准时写入业务实体） */
    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "content_type", length = 200)
    private String contentType;

    /** 临时区相对路径（不含磁盘根） */
    @Column(name = "storage_key", length = 1000)
    private String storageKey;

    @Column(name = "status", length = 20, nullable = false)
    private String status;

    @Column(name = "requested_by")
    private UUID requestedBy;

    @Column(name = "requested_at")
    private LocalDateTime requestedAt;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

    /** 过期清理水位（默认 +7 天） */
    @Column(name = "purge_after")
    private LocalDateTime purgeAfter;

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
        if (requestedAt == null) {
            requestedAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
