package com.vulnaudit.service.impl;

import com.vulnaudit.config.LoginLockoutProperties;
import com.vulnaudit.config.VerificationProperties;
import com.vulnaudit.dto.SendCodeRequest;
import com.vulnaudit.dto.SendCodeResponse;
import com.vulnaudit.dto.UnlockSendCodeResponse;
import com.vulnaudit.entity.User;
import com.vulnaudit.entity.enums.VerificationChannel;
import com.vulnaudit.entity.enums.VerificationPurpose;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.exception.TooManyRequestsException;
import com.vulnaudit.repository.UserRepository;
import com.vulnaudit.service.AccountUnlockService;
import com.vulnaudit.service.LoginLockoutService;
import com.vulnaudit.service.VerificationCodeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * OTP 解锁实现（t123）
 *
 * <p>复用 t102 的验证码设施（purpose={@link VerificationPurpose#UNLOCK}，5 分钟有效、一次性、5 次失败作废）。
 * 限流：同一 username 冷却（默认 300s）、单 IP 每小时上限（默认 10 次）。
 *
 * <p>防枚举：用户名不存在 / 无已验证联系方式时同样返回成功结构，但**不发码**（无已验证联系方式时给出
 * {@code hint=NO_VERIFIED_CONTACT} 让前端提示联系管理员）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccountUnlockServiceImpl implements AccountUnlockService {

    private final UserRepository userRepository;
    private final VerificationCodeService verificationCodeService;
    private final VerificationProperties verificationProperties;
    private final LoginLockoutProperties lockoutProperties;
    private final LoginLockoutService loginLockoutService;

    /** username → 最近一次发码时间（毫秒） */
    private final Map<String, Long> lastSendByUsername = new ConcurrentHashMap<>();

    /** ip → 发码时间窗口 */
    private final Map<String, Deque<Long>> ipWindows = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public UnlockSendCodeResponse sendCode(String username, String clientIp) {
        String normalizedUsername = normalize(username);
        if (!StringUtils.hasText(normalizedUsername)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户名不能为空");
        }
        enforceThrottle(normalizedUsername, clientIp);

        User user = userRepository.findByUsername(normalizedUsername).orElse(null);
        if (user == null) {
            log.info("解锁发码：用户名不存在，按成功返回但不发送");
            return silentSuccess(null);
        }

        VerificationChannel channel = preferredChannel(user);
        if (channel == null) {
            log.info("解锁发码：该用户无已验证联系方式，需管理员解锁: userId={}", user.getId());
            return silentSuccess("NO_VERIFIED_CONTACT");
        }
        String target = channel == VerificationChannel.SMS ? user.getPhone() : user.getEmail();

        SendCodeResponse sent = verificationCodeService.sendCode(
                new SendCodeRequest(channel.name().toLowerCase(Locale.ROOT), target,
                        VerificationPurpose.UNLOCK.name()), clientIp);
        lastSendByUsername.put(normalizedUsername, System.currentTimeMillis());
        log.info("解锁验证码已发送: userId={}, channel={}", user.getId(), channel);
        return new UnlockSendCodeResponse(sent.channel(), mask(target, channel),
                sent.expiresInSeconds(), sent.devCode(), null);
    }

    @Override
    @Transactional
    public void confirm(String username, String target, String code) {
        String normalizedUsername = normalize(username);
        User user = userRepository.findByUsername(normalizedUsername).orElse(null);
        if (user == null) {
            // 与「验证码错误」同形，避免账号枚举
            throw new BusinessException(ErrorCode.BAD_REQUEST, "验证码无效或已过期，请重新获取");
        }

        VerificationChannel channel = channelOf(user, target);
        if (channel == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "验证码无效或已过期，请重新获取");
        }

        verificationCodeService.verifyAndConsume(target, channel, VerificationPurpose.UNLOCK, code);
        loginLockoutService.unlockByOtp(user.getId());
        lastSendByUsername.remove(normalizedUsername);
        log.info("账户已通过 OTP 解锁: userId={}, channel={}", user.getId(), channel);
    }

    @Override
    public void resetThrottleState() {
        lastSendByUsername.clear();
        ipWindows.clear();
    }

    // ------------------------------------------------------------------ 内部


    private VerificationChannel preferredChannel(User user) {
        if (user.isPhoneVerified() && StringUtils.hasText(user.getPhone())) {
            return VerificationChannel.SMS;
        }
        if (user.isEmailVerified() && StringUtils.hasText(user.getEmail())) {
            return VerificationChannel.EMAIL;
        }
        return null;
    }

    /** 提交的 target 必须与该用户已验证的手机/邮箱一致 */
    private VerificationChannel channelOf(User user, String target) {
        if (!StringUtils.hasText(target)) {
            return null;
        }
        String normalized = target.trim();
        if (user.isPhoneVerified() && normalized.equals(user.getPhone())) {
            return VerificationChannel.SMS;
        }
        if (user.isEmailVerified() && user.getEmail() != null && normalized.equalsIgnoreCase(user.getEmail())) {
            return VerificationChannel.EMAIL;
        }
        return null;
    }

    private void enforceThrottle(String username, String clientIp) {
        long now = System.currentTimeMillis();
        Long last = lastSendByUsername.get(username);
        if (last != null && now - last < lockoutProperties.getUnlockUsernameCooldownSeconds() * 1000) {
            long retryAfter = (lockoutProperties.getUnlockUsernameCooldownSeconds() * 1000 - (now - last)) / 1000;
            throw new TooManyRequestsException("解锁验证码发送过于频繁，请 " + Math.max(retryAfter, 1) + " 秒后再试",
                    Math.max(retryAfter, 1));
        }
        if (StringUtils.hasText(clientIp)) {
            Deque<Long> window = ipWindows.computeIfAbsent(clientIp, k -> new ArrayDeque<>());
            synchronized (window) {
                while (!window.isEmpty() && now - window.peekFirst() > 3_600_000L) {
                    window.pollFirst();
                }
                if (window.size() >= lockoutProperties.getUnlockMaxPerIpPerHour()) {
                    throw new TooManyRequestsException("解锁请求过于频繁，请稍后再试", 600);
                }
                window.addLast(now);
            }
        }
    }

    private UnlockSendCodeResponse silentSuccess(String hint) {
        return new UnlockSendCodeResponse(null, null, verificationProperties.getCodeTtlSeconds(), null, hint);
    }

    private String mask(String target, VerificationChannel channel) {
        if (target == null) {
            return null;
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

    private String normalize(String raw) {
        return raw == null ? "" : raw.trim();
    }
}
