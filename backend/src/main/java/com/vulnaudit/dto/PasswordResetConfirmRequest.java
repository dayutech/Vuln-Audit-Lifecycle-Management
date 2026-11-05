package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 忘记密码 - 确认重置请求（t103）
 *
 * <p>密码强度由 {@code PasswordPolicy} 统一校验（≥8 位 + 四类字符至少三类），
 * 因此这里只做「非空 + 长度上限」的结构校验。
 *
 * @param target      手机号或邮箱（与发码时一致）
 * @param code        验证码（6 位数字）
 * @param newPassword 新密码
 */
@Schema(description = "忘记密码-确认重置请求")
public record PasswordResetConfirmRequest(
        @Schema(description = "手机号或邮箱", example = "13800001111", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "validation.verification.target.required")
        @Size(max = 120, message = "validation.verification.target.too_long")
        String target,

        @Schema(description = "验证码", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "validation.verification.code.required")
        String code,

        @Schema(description = "新密码（≥8 位，含大小写/数字/符号至少三类）", example = "NewPass@2026",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "validation.user.password.required")
        @Size(max = 64, message = "validation.user.password.size")
        String newPassword) {
}
