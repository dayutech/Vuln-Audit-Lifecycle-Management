package com.vulnaudit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 项目期次（t67）
 *
 * <p>一个项目可拆多期（如「一期」「二期」），期次内再做资产挂靠。
 * 唯一约束 {@code (project_id, phase_no)} 保证同项目内期次号不重复。
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "project_phases",
        uniqueConstraints = @UniqueConstraint(name = "uk_project_phases_project_no",
                columnNames = {"project_id", "phase_no"}),
        indexes = {
                @Index(name = "idx_project_phases_project", columnList = "project_id"),
                @Index(name = "idx_project_phases_status", columnList = "status")
        })
public class ProjectPhase {

    @Id
    @GeneratedValue(generator = "UUID")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    /** 期次序号（项目内从 1 开始连续） */
    @Column(name = "phase_no", nullable = false)
    private Integer phaseNo;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    /** PLANNED / ACTIVE / DONE */
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "PLANNED";

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = "PLANNED";
        }
        log.debug("创建项目期次: projectId={}, phaseNo={}", projectId, phaseNo);
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
