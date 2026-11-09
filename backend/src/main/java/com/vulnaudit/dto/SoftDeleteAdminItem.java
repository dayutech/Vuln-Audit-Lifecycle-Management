package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 管理端「含已删」列表项（t140）
 *
 * @param id        主键
 * @param code      业务编码（客户编码 / 项目编码 / 资产编号）
 * @param name      名称
 * @param deletedAt 软删时间；null = 未删
 * @param deletedBy 删除人（user id）；null = 未删或历史数据
 */
@Schema(description = "软删管理列表项")
public record SoftDeleteAdminItem(
        UUID id,
        String code,
        String name,
        LocalDateTime deletedAt,
        UUID deletedBy) {
}
