package com.vulnaudit.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 发送验证码响应（t102）
 *
 * <p><b>devCode 只允许在 dev-mode 下出现</b>（{@code app.security.verification.dev-mode=true}），
 * 生产环境必须为 {@code null}，此时 JSON 里不会出现该字段（{@link JsonInclude} NON_NULL）。
 *
 * @param channel      渠道（sms / email）
 * @param target       接收方（脱敏后回显，避免响应成为枚举手机号的工具）
 * @param purpose      用途
 * @param expiresInSeconds 有效期（秒）
 * @param devCode      开发/测试模式下的明文验证码；生产为 null
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "发送验证码响应")
public record SendCodeResponse(
        String channel,
        String target,
        String purpose,
        long expiresInSeconds,
        String devCode) {
}
