package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 资产文件列表项（t148）
 *
 * <p>相比直接返回实体，这里补了 **上传人展示名**（`uploaderName`，由 userRepository.findAllById 一次性批量解析，
 * 禁止 N+1）与 `remark`；分页/排序全部走数据库。
 */
@Schema(description = "资产文件列表项")
public record AssetFileItem(
        UUID id,
        UUID assetId,
        UUID assetVersionId,
        String name,
        String category,
        String description,
        String remark,
        Long fileSize,
        String filePath,
        String checksum,
        Integer version,
        String status,
        LocalDateTime createdAt,
        UUID uploadedBy,
        String uploaderName) {
}
