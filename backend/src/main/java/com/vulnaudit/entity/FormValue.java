package com.vulnaudit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 表单字段值（t178）——三层语义，本系统的核心设计
 *
 * <ul>
 *   <li>{@code sourceValue}：生成实例时从业务库取的**快照**，生成后不可变；</li>
 *   <li>{@code filledValue}：填表人手工填写的值；</li>
 *   <li>{@code overrideValue}：修改覆盖，**只影响导出**，不回写业务库。</li>
 * </ul>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "form_value",
        uniqueConstraints = @UniqueConstraint(name = "uk_form_value_submission_field",
                columnNames = {"submission_id", "field_id"}),
        indexes = @Index(name = "idx_form_value_field", columnList = "field_id"))
public class FormValue {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "submission_id", nullable = false)
    private UUID submissionId;

    @Column(name = "field_id", nullable = false)
    private UUID fieldId;

    /** 库快照（生成后不可变） */
    @Column(name = "source_value", columnDefinition = "TEXT")
    private String sourceValue;

    /** 人工填写 */
    @Column(name = "filled_value", columnDefinition = "TEXT")
    private String filledValue;

    /** 修改覆盖（只影响导出） */
    @Column(name = "override_value", columnDefinition = "TEXT")
    private String overrideValue;

    /** 字段级指派预留位（第一期不使用） */
    @Column(name = "assigned_to")
    private UUID assignedTo;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void touch() {
        updatedAt = LocalDateTime.now();
    }
}
