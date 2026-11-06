package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 账户解锁验证码响应（t123）
 *
 * @param channel         实际投递渠道（sms / email；未发送时为 null）
 * @param target          脱敏后的接收方（未发送时为 null）
 * @param expiresInSeconds 有效期（秒）
 * @param devCode         仅开发模式回传
 * @param hint            内部提示码：{@code NO_VERIFIED_CONTACT} 表示无已验证联系方式，需联系管理员
 */
@Schema(description = "账户解锁验证码响应")
public record UnlockSendCodeResponse(
        String channel,
        String target,
        long expiresInSeconds,
        String devCode,
        String hint) {
}
