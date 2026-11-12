package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * 表单字段值视图（t179）——三层取值原样暴露
 *
 * @param sourceValue   生成时从业务库取的快照（**不可修改**；业务后续变化不影响它）
 * @param filledValue   被指派人填写的值
 * @param overrideValue 修改覆盖（只影响导出）
 */
@Schema(description = "表单字段与取值")
public record FormValueVO(
        UUID fieldId,
        String fieldKey,
        String label,
        String fieldType,
        boolean required,
        int sortOrder,
        String sourceType,
        String sourceLabel,
        String options,
        /** 是否实例级字段（发起人在本表单里临时加的） */
        boolean instanceField,
        String sourceValue,
        String filledValue,
        String overrideValue) {
}
