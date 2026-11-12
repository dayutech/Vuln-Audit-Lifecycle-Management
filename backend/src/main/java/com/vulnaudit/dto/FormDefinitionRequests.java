package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/** 表单模板 / 字段的请求体（t178） */
public final class FormDefinitionRequests {

    private FormDefinitionRequests() {
    }

    /** 创建模板 */
    @Schema(description = "创建表单模板")
    public record CreateDefinition(
            @NotBlank @Size(max = 50) String code,
            @NotBlank @Size(max = 200) String name,
            @Size(max = 1000) String description,
            Boolean enabled) {
    }

    /** 更新模板（**code 不可改**：带了不同值 → 403） */
    @Schema(description = "更新表单模板")
    public record UpdateDefinition(
            @Size(max = 50) String code,
            @Size(max = 200) String name,
            @Size(max = 1000) String description,
            Boolean enabled) {
    }

    /** 创建/更新字段 */
    @Schema(description = "表单字段")
    public record FieldUpsert(
            @Size(max = 64) String fieldKey,
            @Size(max = 200) String label,
            @Size(max = 20) String fieldType,
            Boolean required,
            Integer sortOrder,
            @Size(max = 64) String sourceType,
            @Size(max = 2000) String options) {
    }

    /** 字段重排序（按给定顺序重排 sort_order） */
    @Schema(description = "字段重排序")
    public record Reorder(List<java.util.UUID> fieldIds) {
    }
}
