package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 忘记密码 - 发送验证码请求（t103）
 *
 * @param channel 渠道：{@code sms} / {@code email}
 * @param target  已绑定并验证的手机号，或已验证的邮箱
 */
@Schema(description = "忘记密码-发送验证码请求")
public record PasswordResetSendCodeRequest(
        @Schema(description = "渠道：sms / email", example = "sms", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "validation.verification.channel.required")
        String channel,

        @Schema(description = "手机号或邮箱", example = "13800001111", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "validation.verification.target.required")
        @Size(max = 120, message = "validation.verification.target.too_long")
        String target) {
}
