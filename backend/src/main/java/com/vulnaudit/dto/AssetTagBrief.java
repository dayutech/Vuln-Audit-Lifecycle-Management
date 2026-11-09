package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * 标签摘要（t145）：资产列表 / 项目资产 / 客户 summary 内联展示
 *
 * @param id      标签定义 id（定义缺失时为 null，例如历史脏数据）
 * @param name    标签名
 * @param color   颜色（可空）
 * @param builtin 是否内置
 */
@Schema(description = "标签摘要")
public record AssetTagBrief(
        UUID id,
        String name,
        String color,
        boolean builtin) {
}
