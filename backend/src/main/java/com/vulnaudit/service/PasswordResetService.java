package com.vulnaudit.service;

import com.vulnaudit.dto.PasswordResetSendCodeResponse;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * 忘记密码服务（t103）
 *
 * <p>复用 t102 的 {@link VerificationCodeService}（purpose={@code RESET_PASSWORD}）与验证码表，
 * 不另建表。
 *
 * <p>安全要点：
 * <ol>
 *   <li><b>防账号枚举</b>：发码接口对「不存在 / 未验证」的 target 同样返回成功，内部不发码；</li>
 *   <li><b>只用已验证联系方式</b>：手机号需 {@code phone_verified=true}、邮箱需 {@code email_verified=true}；</li>
 *   <li><b>验证码</b>：哈希比对、过期、尝试上限、一次性消费（复用验证码服务的实现）；</li>
 *   <li><b>改密后全端下线</b>：写 {@code users.token_invalid_before}，JWT 过滤器比较 {@code iat}；</li>
 *   <li><b>审计</b>：{@code RESET_PASSWORD}（resource_type=user、resource_id=用户、details 只含渠道与脱敏 target）。</li>
 * </ol>
 */
public interface PasswordResetService {

    /**
     * 发送重置验证码
     *
     * @param username 用户名（t117：与 target 必须是同一账号）
     * @param channel  渠道 sms / email
     * @param target   手机号或邮箱
     * @param clientIp 调用方 IP（限流）
     * @return 统一结构响应（不存在/未验证的 target 也会成功返回，但不发码）
     */
    @Nonnull
    PasswordResetSendCodeResponse sendCode(@Nullable String username, @Nullable String channel,
                                                       @Nullable String target, @Nullable String clientIp);

    /**
     * 校验验证码并重置密码
     *
     * @param target      手机号或邮箱
     * @param code        验证码
     * @param newPassword 新密码（强度由 {@code PasswordPolicy} 校验）
     * @param clientIp    调用方 IP（连续失败限流）
     * @throws com.vulnaudit.exception.BusinessException 400（验证码/强度/与旧密码相同）、429（失败过多）
     */
    void confirm(@Nullable String username, @Nullable String target, @Nullable String code,
                 @Nullable String newPassword, @Nullable String clientIp);
}
