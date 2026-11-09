package com.vulnaudit.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 项目下的资产条目（t69 反向查询 {@code GET /projects/{projectId}/assets}）
 *
 * <p>把「资产摘要」与「挂靠信息」放在一条记录里，前端无需再回来查挂靠表；
 * {@code phaseId/phaseName} 为空表示该项目级挂靠（不分期）。
 */
public record ProjectAssetItem(
        UUID assetId,
        String assetName,
        String assetCode,
        String assetStatus,
        String assetPriority,
        String targetType,
        UUID ownerId,
        UUID linkId,
        UUID phaseId,
        String phaseName,
        String linkType,
        String remark,
        LocalDateTime linkedAt,
        java.util.List<com.vulnaudit.dto.AssetTagBrief> tags) {
}
