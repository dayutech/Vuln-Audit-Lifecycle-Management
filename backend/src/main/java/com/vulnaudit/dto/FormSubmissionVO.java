package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** 表单实例详情（t179） */
@Schema(description = "表单实例详情")
public record FormSubmissionVO(
        UUID id,
        UUID definitionId,
        String definitionCode,
        String definitionName,
        String title,
        String subjectType,
        UUID subjectId,
        String subjectName,
        String status,
        UUID createdBy,
        String createdByName,
        UUID assignedTo,
        String assignedToName,
        UUID confirmedBy,
        LocalDateTime assignedAt,
        LocalDateTime submittedAt,
        LocalDateTime confirmedAt,
        String note,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        /** 已填写的字段数 / 字段总数（进度 N/M） */
        int filledCount,
        int fieldCount,
        List<FormValueVO> fields) {
}
