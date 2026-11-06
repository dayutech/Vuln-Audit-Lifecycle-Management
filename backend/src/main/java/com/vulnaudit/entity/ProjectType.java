package com.vulnaudit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 项目类型（t130）
 *
 * <p>内置 5 项（{@code builtin=true}，code/name 不可改、不可删）：
 * {@code PENTEST} 渗透测试、{@code CODE_AUDIT} 代码审计、{@code EMERGENCY} 应急响应、
 * {@code COMPLIANCE} 合规测评、{@code TRAINING} 安全培训；管理员可新增/修改/删除自定义项。
 *
 * <p>删除是**软删**（{@code deleted_at}）：{@code projects.type_code} 仍在引用历史类型时，
 * 软删可保证历史项目仍能解析出名称，而不是出现悬空引用。
 * {@code code} 全局唯一（含软删行），软删后不可复用同一 code。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "project_types",
        uniqueConstraints = @UniqueConstraint(name = "uk_project_types_code", columnNames = "code"),
        indexes = {
                @Index(name = "idx_project_types_name", columnList = "name"),
                @Index(name = "idx_project_types_deleted", columnList = "deleted_at")
        })
public class ProjectType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "code", length = 32, nullable = false)
    private String code;

    @Column(name = "name", length = 50, nullable = false)
    private String name;

    @Column(name = "builtin", nullable = false)
    @Builder.Default
    private boolean builtin = false;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = true;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    /** 软删时间；null = 未删除 */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public boolean isDeleted() {
        return deletedAt != null;
    }

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
