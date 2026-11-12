package com.vulnaudit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** 表单实例（t178）：状态 DRAFT/ASSIGNED/SUBMITTED/CONFIRMED/RETURNED */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "form_submission",
        indexes = {
                @Index(name = "idx_form_submission_status_assignee", columnList = "status,assigned_to"),
                @Index(name = "idx_form_submission_creator", columnList = "created_by"),
                @Index(name = "idx_form_submission_definition", columnList = "definition_id")
        })
public class FormSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "definition_id", nullable = false)
    private UUID definitionId;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "status", length = 20, nullable = false)
    private String status;

    @Column(name = "created_by")
    private UUID createdBy;

    /** 表单级指派（第一期唯一实现的指派维度） */
    @Column(name = "assigned_to")
    private UUID assignedTo;

    /** 确认人（可选） */
    @Column(name = "confirmed_by")
    private UUID confirmedBy;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "note", length = 2000)
    private String note;

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
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
