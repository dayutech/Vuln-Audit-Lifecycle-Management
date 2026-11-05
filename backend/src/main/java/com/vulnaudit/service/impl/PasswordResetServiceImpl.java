package com.vulnaudit.service.impl;

import com.vulnaudit.config.VerificationProperties;
import com.vulnaudit.dto.PasswordResetSendCodeResponse;
import com.vulnaudit.dto.SendCodeRequest;
import com.vulnaudit.dto.SendCodeResponse;
import com.vulnaudit.entity.User;
import com.vulnaudit.entity.enums.VerificationChannel;
import com.vulnaudit.entity.enums.VerificationPurpose;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.exception.TooManyRequestsException;
import com.vulnaudit.repository.UserRepository;
import com.vulnaudit.service.AuditLogService;
import com.vulnaudit.service.PasswordResetService;
import com.vulnaudit.service.VerificationCodeService;
import com.vulnaudit.util.PasswordPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 忘记密码实现（t103）
 *
 * <p>复用 t102 的验证码基础设施（{@link VerificationCodeService} + {@code verification_codes} 表），
 * 不新增表。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetServiceImpl implements PasswordResetService {

    /** 验证码无效时的统一文案：不区分「没有这个账号」与「验证码不对」 */
    private static final String INVALID_CODE_MESSAGE = "验证码无效或已过期，请重新获取";

    private final UserRepository userRepository;
    private final VerificationCodeService verificationCodeService;
    private final VerificationProperties properties;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    /** IP → 最近 confirm 失败时间戳（进程内滑动窗口） */
    private final Map<String, Deque<LocalDateTime>> failedWindows = new ConcurrentHashMap<>();

    // ------------------------------------------------------------------ 发码

    @Override
    @Transactional
    public PasswordResetSendCodeResponse sendCode(String channelRaw, String targetRaw, String clientIp) {
        VerificationChannel channel = VerificationChannel.fromCode(channelRaw);
        if (channel == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "不支持的验证码渠道: " + channelRaw + "（支持: sms, email）");
        }
        String target = verificationCodeService.normalize(channel, targetRaw);
        if (!StringUtils.hasText(target)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "接收方不能为空");
        }

        User user = resolveUser(channel, target);
        if (user == null) {
            // 防账号枚举：对外仍是 200，内部不发码
            log.info("忘记密码发码：target 不存在或未验证，按成功返回但不发送: channel={}, target={}",
                    channel, mask(target, channel));
            return new PasswordResetSendCodeResponse(
                    channel.name().toLowerCase(Locale.ROOT), target,
                    VerificationPurpose.RESET_PASSWORD.name(), properties.getCodeTtlSeconds(), null);
        }

        SendCodeResponse sent = verificationCodeService.sendCode(
                new SendCodeRequest(channel.name().toLowerCase(Locale.ROOT), target,
                        VerificationPurpose.RESET_PASSWORD.name()), clientIp);

        log.info("忘记密码发码成功: userId={}, channel={}, target={}",
                user.getId(), channel, mask(target, channel));
        return new PasswordResetSendCodeResponse(sent.channel(), sent.target(), sent.purpose(),
                sent.expiresInSeconds(), sent.devCode());
    }

    // ------------------------------------------------------------------ 重置

    @Override
    @Transactional
    public void confirm(String targetRaw, String code, String newPassword, String clientIp) {
        enforceFailureLimit(clientIp);

        // 先按手机号匹配，再按邮箱匹配（两者都归一化后比较）
        User user = null;
        String target = null;
        VerificationChannel channel = null;
        if (StringUtils.hasText(targetRaw)) {
            String phone = verificationCodeService.normalize(VerificationChannel.SMS, targetRaw);
            user = resolveUser(VerificationChannel.SMS, phone);
            if (user != null) {
                target = phone;
                channel = VerificationChannel.SMS;
            } else {
                String email = verificationCodeService.normalize(VerificationChannel.EMAIL, targetRaw);
                user = resolveUser(VerificationChannel.EMAIL, email);
                if (user != null) {
                    target = email;
                    channel = VerificationChannel.EMAIL;
                }
            }
        }
        if (user == null || channel == null) {
            recordFailure(clientIp);
            throw new BusinessException(ErrorCode.BAD_REQUEST, INVALID_CODE_MESSAGE);
        }

        // 强度校验放在「消费验证码」之前：弱密码不会白白消耗一次验证码
        PasswordPolicy.validate(newPassword);

        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "新密码不能与旧密码相同");
        }

        try {
            verificationCodeService.verifyAndConsume(target, channel, VerificationPurpose.RESET_PASSWORD, code);
        } catch (BusinessException e) {
            recordFailure(clientIp);
            throw e;
        }

        LocalDateTime invalidBefore = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        // 失效该用户所有「签发时间早于此时间戳」的 token（JwtAuthenticationFilter 校验 iat）
        user.setTokenInvalidBefore(invalidBefore);
        userRepository.save(user);

        auditLogService.log(user.getId(), "RESET_PASSWORD", "user", user.getId(),
                "忘记密码重置: channel=" + channel.name().toLowerCase(Locale.ROOT)
                        + ", target=" + mask(target, channel)
                        + ", tokensInvalidBefore=" + invalidBefore);

        log.info("忘记密码重置成功: userId={}, channel={}, target={}",
                user.getId(), channel, mask(target, channel));
    }

    // ------------------------------------------------------------------ 内部

    /**
     * 只接受「已绑定并验证 + 账号可用」的联系方式
     */
    private User resolveUser(VerificationChannel channel, String target) {
        if (!StringUtils.hasText(target)) {
            return null;
        }
        if (channel == VerificationChannel.SMS) {
            return userRepository.findByPhone(target)
                    .filter(user -> user.isPhoneVerified() && user.isActive())
                    .orElse(null);
        }
        return userRepository.findByEmailIgnoreCase(target)
                .filter(user -> user.isEmailVerified() && user.isActive())
                .orElse(null);
    }

    /** 连续失败限流：同一 IP 在窗口内失败次数达到上限 → 429 */
    private void enforceFailureLimit(String clientIp) {
        if (!StringUtils.hasText(clientIp)) {
            return;
        }
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(properties.getResetFailedWindowMinutes());
        Deque<LocalDateTime> window = failedWindows.computeIfAbsent(clientIp, key -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && window.peekFirst().isBefore(threshold)) {
                window.pollFirst();
            }
            if (window.size() >= properties.getResetMaxFailedAttempts()) {
                long retryAfter = Math.max(
                        Duration.between(window.peekFirst(),
                                LocalDateTime.now().plusMinutes(properties.getResetFailedWindowMinutes())).getSeconds(),
                        1);
                throw new TooManyRequestsException(
                        "重置密码失败次数过多，请 " + retryAfter + " 秒后再试", retryAfter);
            }
        }
    }

    private void recordFailure(String clientIp) {
        if (!StringUtils.hasText(clientIp)) {
            return;
        }
        failedWindows.computeIfAbsent(clientIp, key -> new ArrayDeque<>()).addLast(LocalDateTime.now());
    }

    private String mask(String target, VerificationChannel channel) {
        if (target == null) {
            return "****";
        }
        if (channel == VerificationChannel.SMS && target.length() >= 7) {
            return target.substring(0, 3) + "****" + target.substring(target.length() - 4);
        }
        int at = target.indexOf('@');
        if (at <= 1) {
            return "****";
        }
        return target.charAt(0) + "***" + target.substring(at);
    }
}
