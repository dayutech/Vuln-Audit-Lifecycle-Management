package com.vulnaudit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** 表单字段（t178）：`sourceType` 非空 = 库字段（生成时快照），为空 = 手填 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "form_field",
        uniqueConstraints = @UniqueConstraint(name = "uk_form_field_key",
                columnNames = {"definition_id", "field_key"}),
        indexes = @Index(name = "idx_form_field_definition", columnList = "definition_id,sort_order"))
public class FormField {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "definition_id", nullable = false)
    private UUID definitionId;

    /** 字段键（同模板内唯一） */
    @Column(name = "field_key", length = 64, nullable = false)
    private String fieldKey;

    @Column(name = "label", length = 200, nullable = false)
    private String label;

    /** TEXT/TEXTAREA/NUMBER/DATE/SELECT/CHECKBOX/FILE */
    @Column(name = "field_type", length = 20, nullable = false)
    private String fieldType;

    @Column(name = "required", nullable = false)
    @Builder.Default
    private boolean required = false;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;

    /** 库字段来源（FormFieldSource 的 code），空 = 手填 */
    @Column(name = "source_type", length = 64)
    private String sourceType;

    /** SELECT 选项（JSON 数组字符串） */
    @Column(name = "options", length = 2000)
    private String options;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

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
