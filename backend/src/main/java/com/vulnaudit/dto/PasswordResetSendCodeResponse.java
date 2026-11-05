package com.vulnaudit.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 忘记密码 - 发送验证码响应（t103）
 *
 * <p><b>防账号枚举</b>：无论 target 是否存在，HTTP 状态码与结构都完全一致；
 * {@code devCode} 只在开发/测试模式（{@code app.security.verification.dev-mode=true}）下出现，
 * 且仅当验证码真的发出（target 命中已绑定且已验证的账号）时才非空。
 *
 * @param channel          渠道
 * @param target           接收方（原样回显调用方传入值，便于前端对齐表单）
 * @param purpose          固定 {@code RESET_PASSWORD}
 * @param expiresInSeconds 有效期（秒）
 * @param devCode          开发/测试模式下的明文验证码；生产为 null
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "忘记密码-发送验证码响应")
public record PasswordResetSendCodeResponse(
        String channel,
        String target,
        String purpose,
        long expiresInSeconds,
        String devCode) {
}
