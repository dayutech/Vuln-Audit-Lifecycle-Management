package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * 标签下拉项（t141，登录即可）
 *
 * @param id         标签定义 id
 * @param name       标签名
 * @param color      颜色
 * @param builtin    是否内置
 * @param assetCount 打了该标签的资产数（按调用者可见范围收敛）
 */
@Schema(description = "标签下拉项")
public record TagOptionVO(
        UUID id,
        String name,
        String color,
        boolean builtin,
        long assetCount) {
}
