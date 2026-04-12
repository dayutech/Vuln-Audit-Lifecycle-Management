package com.vulnaudit.entity;

import com.vulnaudit.dto.TaskUserInfo;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 任务实体类
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "tasks")
public class Task {

    @Id
    @GeneratedValue(generator = "UUID")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "title", length = 500, nullable = false)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "asset_id")
    private UUID assetId;

    @Column(name = "asset_version_id")
    private UUID assetVersionId;

    @Column(name = "vulnerability_id")
    private UUID vulnerabilityId;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "assigned_to", nullable = false)
    private UUID assignedTo;

    @Column(name = "assigned_by", nullable = false)
    private UUID assignedBy;

    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "pending";

    @Column(name = "due_date")
    private LocalDateTime dueDate;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "estimated_hours", precision = 10, scale = 2)
    private BigDecimal estimatedHours;

    @Column(name = "actual_hours", precision = 10, scale = 2, nullable = false)
    @Builder.Default
    private BigDecimal actualHours = BigDecimal.ZERO;

    @Column(name = "audit_conclusion", columnDefinition = "TEXT")
    private String auditConclusion;

    @Column(name = "review_signature", length = 255)
    private String reviewSignature;

    @Column(name = "approval_comment", columnDefinition = "TEXT")
    private String approvalComment;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "env_collect_doc_id")
    private UUID envCollectDocId;

    @Column(name = "env_setup_doc_id")
    private UUID envSetupDocId;

    @Column(name = "code_audit_doc_id")
    private UUID codeAuditDocId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Transient
    private TaskUserInfo assignee;

    @Transient
    private TaskUserInfo creator;

    @Transient
    private List<TaskUserInfo> collaborators;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        log.debug("创建任务: title={}", title);
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        log.debug("更新任务: id={}", id);
    }

}
