package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 「我发起的」/「待我处理」列表项（t179/t180）
 *
 * @param note 最近一次退回的说明（RETURNED 时给前端展示「退回原因」）
 */
@Schema(description = "表单列表项")
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
        String note,
        LocalDateTime createdAt) {
}
