package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/** 项目批量移除资产请求（t154）：assetIds 1–100，移除该项目下这些资产的**全部**挂靠（含各期次） */
@Schema(description = "项目批量移除资产请求")
public record ProjectBulkRemoveRequest(
        java.util.List<UUID> assetIds) {
}
