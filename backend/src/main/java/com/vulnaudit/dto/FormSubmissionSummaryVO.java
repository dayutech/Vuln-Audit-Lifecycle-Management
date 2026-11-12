package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/** 「我发起的」列表项（t179） */
@Schema(description = "我发起的表单")
public record FormSubmissionSummaryVO(
        UUID id,
        String definitionName,
        String title,
        String subjectType,
        UUID subjectId,
        String subjectName,
        String status,
        UUID assignedTo,
        String assignedToName,
        int filledCount,
        int fieldCount,
        LocalDateTime createdAt) {
}
