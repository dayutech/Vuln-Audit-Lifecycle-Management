package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 项目批量移除资产结果（t154） */
@Schema(description = "项目批量移除资产结果")
public record ProjectBulkRemoveResult(
        int removed) {
}
