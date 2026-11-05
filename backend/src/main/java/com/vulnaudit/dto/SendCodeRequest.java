package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 发送验证码请求（t102）
 *
 * @param channel 渠道：{@code sms} / {@code email}（大小写不敏感）
 * @param target  接收方：sms 传中国大陆手机号，email 传邮箱
 * @param purpose 用途：{@code REGISTER}（当前仅此用途落地）
 */
@Schema(description = "发送验证码请求")
public record SendCodeRequest(
        @Schema(description = "渠道：sms / email", example = "sms", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "validation.verification.channel.required")
        String channel,

        @Schema(description = "接收方：sms 为中国大陆手机号，email 为邮箱", example = "13800001111",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "validation.verification.target.required")
        @Size(max = 120, message = "validation.verification.target.too_long")
        String target,

        @Schema(description = "用途：REGISTER", example = "REGISTER", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "validation.verification.purpose.required")
        String purpose) {
}
