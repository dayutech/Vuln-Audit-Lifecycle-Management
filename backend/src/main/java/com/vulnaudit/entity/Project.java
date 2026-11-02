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
 * 项目实体（t67）
 *
 * <p>一个客户可有多个项目；项目可分「期次」（见 {@link ProjectPhase}）。
 * 项目的状态机：DRAFT → ACTIVE → PAUSED / CLOSED。
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "projects",
        uniqueConstraints = @UniqueConstraint(name = "uk_projects_code", columnNames = "code"),
        indexes = {
                @Index(name = "idx_projects_customer", columnList = "customer_id"),
                @Index(name = "idx_projects_manager", columnList = "manager_id"),
                @Index(name = "idx_projects_status", columnList = "status")
        })
public class Project {

    @Id
    @GeneratedValue(generator = "UUID")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "code", length = 64, nullable = false, unique = true)
    private String code;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    /** PENTEST / CODE_AUDIT / EMERGENCY / COMPLIANCE / TRAINING / OTHER */
    @Column(name = "type", length = 32, nullable = false)
    @Builder.Default
    private String type = "PENTEST";

    /** DRAFT / ACTIVE / PAUSED / CLOSED */
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "DRAFT";

    /** 项目经理（user id） */
    @Column(name = "manager_id", nullable = false)
    private UUID managerId;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    /** 是否按期次管理（true 时挂靠可落到具体期次） */
    @Column(name = "is_phased", nullable = false)
    @Builder.Default
    private Boolean isPhased = false;

    @Column(name = "contract_no", length = 100)
    private String contractNo;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

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
        if (this.status == null) {
            this.status = "DRAFT";
        }
        if (this.type == null) {
            this.type = "PENTEST";
        }
        if (this.isPhased == null) {
            this.isPhased = false;
        }
        log.debug("创建项目实体: code={}, customerId={}", code, customerId);
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
