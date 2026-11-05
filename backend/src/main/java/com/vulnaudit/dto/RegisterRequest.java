package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 自助注册请求（t102）
 *
 * <p>规则：手机号<b>必须</b>通过 {@code code} 校验；填了 {@code email} 则<b>必须</b>通过 {@code emailCode} 校验。
 *
 * <p>注意：注册成功**不产生可登录账号**，只落一条 {@code users.status=pending_approval} 的占位用户
 * 与一条 {@code registration_requests} 记录，等管理员审批后才会转为 active。
 *
 * @param username  用户名（3–50，字母/数字/下划线）
 * @param password  密码（6–64）
 * @param phone     手机号（中国大陆 11 位）
 * @param code      手机验证码（6 位数字）
 * @param email     邮箱（可选；填了则必须提供 emailCode）
 * @param emailCode 邮箱验证码（填了 email 时必填）
 */
@Schema(description = "自助注册请求")
public record RegisterRequest(
        @Schema(description = "用户名", example = "zhangsan", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "validation.user.username.required")
        @Size(min = 3, max = 50, message = "validation.user.username.size")
        String username,

        @Schema(description = "密码", example = "Passw0rd!", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "validation.user.password.required")
        @Size(min = 6, max = 64, message = "validation.user.password.size")
        String password,

        @Schema(description = "手机号（中国大陆 11 位）", example = "13800001111",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "validation.user.phone.required")
        String phone,

        @Schema(description = "手机验证码（6 位数字）", example = "123456", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "validation.verification.code.required")
        String code,

        @Schema(description = "邮箱（可选）", example = "zhangsan@example.com")
        String email,

        @Schema(description = "邮箱验证码（填了 email 时必填）", example = "654321")
        String emailCode) {
}
