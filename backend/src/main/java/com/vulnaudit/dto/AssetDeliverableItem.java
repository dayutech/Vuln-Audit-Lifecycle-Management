package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/** 资产交付物列表项（t148）：与 {@link AssetFileItem} 同口径（分页 + 上传人展示名 + remark） */
@Schema(description = "资产交付物列表项")
public record AssetDeliverableItem(
        UUID id,
        UUID assetId,
        UUID assetVersionId,
        String name,
        String deliverableType,
        String description,
        String remark,
        Long fileSize,
        String filePath,
        String checksum,
        String status,
        LocalDateTime createdAt,
        UUID uploadedBy,
        String uploaderName) {
}
