package com.vulnaudit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 行业类型（t129，GB/T 4754 国民经济行业分类）
 *
 * <p>{@code builtin=true} 为内置项（不可删改）；{@code parentCode} 支持门类→大类层级。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "industry_types",
        uniqueConstraints = @UniqueConstraint(name = "uk_industry_types_code", columnNames = "code"))
public class IndustryType {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "code", length = 16, nullable = false)
    private String code;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "parent_code", length = 16)
    private String parentCode;

    @Column(name = "builtin", nullable = false)
    @Builder.Default
    private boolean builtin = false;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = true;

    /**
     * 软删时间（t130）：管理员删除**自定义**行业时只打标记，不物理删除。
     *
     * <p>原因：{@code customers.industry_code} 仍可能引用历史行业，物理删除会让客户详情
     * 解析不出行业名；软删后「检索 / 下拉」不再返回，但历史引用仍可解释。
     */
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
