package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * 「添加资产」选择器条目（t154）
 *
 * @param linkCount 该资产**当前**的挂靠总数（0 = 孤儿资产，便于前端提示）
 */
@Schema(description = "可选资产条目")
public record AvailableAssetItem(
        UUID id,
        String code,
        String name,
        String targetType,
        String status,
        long linkCount) {
}
