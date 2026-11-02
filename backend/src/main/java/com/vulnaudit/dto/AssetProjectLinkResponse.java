package com.vulnaudit.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 资产挂靠视图（t69）
 *
 * <p>含项目与客户摘要，供资产详情的 {@code projectLinks} 与
 * {@code GET /assets/{assetId}/project-links} 使用。
 */
public record AssetProjectLinkResponse(
        UUID linkId,
        UUID assetId,
        UUID projectId,
        String projectCode,
        String projectName,
        UUID customerId,
        String customerName,
        UUID phaseId,
        String phaseName,
        String linkType,
        String remark,
        UUID linkedBy,
        LocalDateTime linkedAt) {
}
