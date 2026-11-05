package com.vulnaudit.service;

import com.vulnaudit.dto.SendCodeResponse;
import com.vulnaudit.dto.SendCodeRequest;
import com.vulnaudit.entity.enums.VerificationChannel;
import com.vulnaudit.entity.enums.VerificationPurpose;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * 验证码服务（t102）
 *
 * <p>职责：发码（含限流与投递）、校验并消费。
 *
 * <p>安全约定：
 * <ul>
 *   <li>明文验证码只在「发送」瞬间存在，库里只存 BCrypt 哈希；</li>
 *   <li>同一 target+purpose 在 {@code resend-interval-seconds} 内只允许发送一次（429），
 *       单 target 每小时上限 {@code max-per-hour}（429），单 IP 每小时上限 {@code max-per-ip-per-hour}（429）；</li>
 *   <li>校验成功即消费；失败累计 {@code attempts}，达到上限后作废；</li>
 *   <li>只有 {@code app.security.verification.dev-mode=true} 时响应才回传 {@code devCode}。</li>
 * </ul>
 */
public interface VerificationCodeService {

    /** 中国大陆手机号 */
    String CN_MOBILE_REGEX = "^1[3-9]\\d{9}$";

    /** 基础邮箱格式（不做 DNS 校验） */
    String EMAIL_REGEX = "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";

    /**
     * 发送验证码
     *
     * @param request  渠道 / 接收方 / 用途
     * @param clientIp 调用方 IP（限流用，可空）
     * @return 响应（dev-mode 下含 devCode）
     */
    @Nonnull
    SendCodeResponse sendCode(@Nonnull SendCodeRequest request, @Nullable String clientIp);

    /**
     * 校验并消费验证码；任何不通过都会抛 400（{@link com.vulnaudit.exception.BusinessException}）
     *
     * @param targetRaw 接收方（手机号或邮箱，内部会归一化）
     * @param channel   渠道（决定归一化方式：邮箱转小写）
     * @param purpose   用途
     * @param code      提交的明文验证码
     */
    void verifyAndConsume(@Nullable String targetRaw,
                          @Nonnull VerificationChannel channel,
                          @Nonnull VerificationPurpose purpose,
                          @Nullable String code);

    /**
     * 目标归一化：手机号去空格；邮箱去空格 + 小写
     */
    @Nonnull
    String normalize(@Nonnull VerificationChannel channel, @Nullable String targetRaw);

    /**
     * 清空进程内的限流状态（t121：测试基类在每条用例前调用，避免上一条用例的冷却/计数泄漏）。
     *
     * <p>只影响内存计数，不动库里的验证码记录。
     */
    void resetThrottleState();
}
