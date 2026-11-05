package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 拒绝注册申请请求（t102）
 *
 * @param note 拒绝原因（必填，1–500 字；会写入审计与被拒用户的 review_note）
 */
@Schema(description = "拒绝注册申请请求")
public record RejectRegistrationRequest(
        @Schema(description = "拒绝原因", example = "手机号与工号不匹配", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "validation.registration.reject_note.required")
        @Size(max = 500, message = "validation.registration.reject_note.too_long")
        String note) {
}
