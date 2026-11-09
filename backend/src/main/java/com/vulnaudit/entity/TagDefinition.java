package com.vulnaudit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 标签定义（t141）：资产标签的「目录」，替代原来的资产分组
 *
 * <p>与 {@link AssetTag}（资产 ↔ 标签名的关联行）配合：定义管名称/颜色/排序/内置保护，
 * 关联表管「哪个资产打了哪个标签」。自动标签（客户名/项目名）也会落一条定义（builtin=false）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tag_definitions",
        uniqueConstraints = @UniqueConstraint(name = "uk_tag_definitions_name", columnNames = "name"),
        indexes = {
                @Index(name = "idx_tag_definitions_deleted", columnList = "deleted_at")
        })
public class TagDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** 标签名（唯一，含软删行；≤50） */
    @Column(name = "name", length = 50, nullable = false)
    private String name;

    /** 展示颜色（可空，如 #1677ff） */
    @Column(name = "color", length = 20)
    private String color;

    /** 内置标签（名称不可改、不可删） */
    @Column(name = "builtin", nullable = false)
    @Builder.Default
    private boolean builtin = false;

    /** 软删时间；null = 未删除 */
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

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
