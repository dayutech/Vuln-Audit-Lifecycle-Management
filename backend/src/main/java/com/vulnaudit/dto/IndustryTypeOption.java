package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 行业选项（t129）：用于前端边输入边检索 */
@Schema(description = "行业选项")
public record IndustryTypeOption(
        String code,
        String name,
        String parentCode,
        boolean builtin) {
}
