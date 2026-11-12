package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 表单模板视图（t178） */
@Schema(description = "表单模板")
public record FormDefinitionVO(
        UUID id,
        String code,
        String name,
        String description,
        boolean enabled,
        long fieldCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        /** 详情才有值（列表为 null） */
        List<FormFieldVO> fields) {
}
