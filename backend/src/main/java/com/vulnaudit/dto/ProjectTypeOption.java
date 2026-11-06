package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 项目类型选项（t130）：建项目时的下拉，登录即可访问 */
@Schema(description = "项目类型选项")
public record ProjectTypeOption(
        String code,
        String name,
        boolean builtin) {
}
