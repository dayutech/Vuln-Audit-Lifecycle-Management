package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * 项目侧批量添加资产请求（t154）
 *
 * @param assetIds 资产 id 列表，1–100
 * @param phaseId  期次 id（可选；项目未启用分期时不能传）
 * @param linkType TARGET / SHARED / REFERENCE（缺省 TARGET）
 */
@Schema(description = "项目批量添加资产请求")
public record ProjectBulkAssetsRequest(
        java.util.List<UUID> assetIds,
        UUID phaseId,
        String linkType) {
}
