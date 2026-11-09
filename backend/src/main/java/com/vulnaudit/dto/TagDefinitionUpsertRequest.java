package com.vulnaudit.dto;

import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** 标签定义创建/更新请求（t141） */
@Schema(description = "标签定义创建/更新请求")
public record TagDefinitionUpsertRequest(
        @Schema(description = "标签名（创建必填；唯一）") @Size(max = 50) String name,
        @Schema(description = "颜色，可空") @Size(max = 20) String color,
        @Schema(description = "排序值，越小越靠前") Integer sortOrder) {

    public void requireCreateFields() {
        if (name == null || name.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "标签名不能为空");
        }
    }
}
