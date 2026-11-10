package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 审批历史计数（t156，供前端角标，避免全量拉取） */
@Schema(description = "审批历史计数")
public record ApprovalHistoryCount(
        long approved,
        long rejected) {
}
