package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * 表单字段视图（t178）
 *
 * @param sourceType  库字段来源（`CUSTOMER.NAME` 等），空 = 手填
 * @param sourceLabel 来源中文名（`sourceType` 为空时为 null）
 */
@Schema(description = "表单字段")
public record FormFieldVO(
        UUID id,
        String fieldKey,
        String label,
        String fieldType,
        boolean required,
        int sortOrder,
        String sourceType,
        String sourceLabel,
        String options) {
}
