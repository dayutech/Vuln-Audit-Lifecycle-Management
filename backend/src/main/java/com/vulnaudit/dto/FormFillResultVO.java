package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 填写/覆盖的结果（t180） */
@Schema(description = "填写或覆盖结果")
public record FormFillResultVO(
        UUID submissionId,
        String status,
        int updatedFields,
        /** 必填缺失字段的 label（仅在提交校验失败时给，成功为空） */
        List<String> missingRequired,
        /** 本次落下/清除后各字段的生效值（fieldKey → effectiveValue），便于前端即时回显 */
        Map<String, String> effectiveValues) {
}
