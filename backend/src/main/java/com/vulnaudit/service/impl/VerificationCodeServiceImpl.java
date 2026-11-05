package com.vulnaudit.service.impl;

import com.vulnaudit.config.VerificationProperties;
import com.vulnaudit.dto.SendCodeRequest;
import com.vulnaudit.dto.SendCodeResponse;
import com.vulnaudit.entity.VerificationCode;
import com.vulnaudit.entity.enums.VerificationChannel;
import com.vulnaudit.entity.enums.VerificationPurpose;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.exception.TooManyRequestsException;
import com.vulnaudit.repository.VerificationCodeRepository;
import com.vulnaudit.service.VerificationCodeService;
import com.vulnaudit.service.verification.VerificationCodeSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 验证码服务实现（t102）
 *
 * <h3>限流（三层）</h3>
 * <ol>
 *   <li><b>target 冷却</b>：同一 target+purpose 距上次发送不足 {@code resend-interval-seconds} → 429（带 Retry-After）；</li>
 *   <li><b>target 小时上限</b>：同 target+purpose 过去 1 小时发送次数 ≥ {@code max-per-hour} → 429；</li>
 *   <li><b>IP 小时上限</b>：同一客户端 IP 过去 1 小时发送次数 ≥ {@code max-per-ip-per-hour} → 429（进程内滑动窗口）。</li>
 * </ol>
 *
 * <p>前两层落库（多实例共享），第三层是进程内近似防御（挡住单机批量刷；多实例部署时按实例数放大，
 * 实际防刷需在网关层再叠加，已在 README 记录）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationCodeServiceImpl implements VerificationCodeService {

    private static final Pattern CN_MOBILE = Pattern.compile(CN_MOBILE_REGEX);
    private static final Pattern EMAIL = Pattern.compile(EMAIL_REGEX);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final VerificationCodeRepository verificationCodeRepository;
    private final VerificationCodeSender verificationCodeSender;
    private final VerificationProperties properties;
    private final PasswordEncoder passwordEncoder;

    /** IP → 最近一小时发码时间戳（进程内） */
    private final Map<String, Deque<LocalDateTime>> ipWindows = new ConcurrentHashMap<>();

    @Override
    @Transactional
    public SendCodeResponse sendCode(SendCodeRequest request, String clientIp) {
        VerificationChannel channel = VerificationChannel.fromCode(request.channel());
        if (channel == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "不支持的验证码渠道: " + request.channel() + "（支持: sms, email）");
        }
        VerificationPurpose purpose = VerificationPurpose.fromCode(request.purpose());
        if (purpose == null || !purpose.isEnabled()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "暂不支持该验证码用途: " + request.purpose() + "（当前仅支持 REGISTER）");
        }

        String target = normalize(channel, request.target());
        validateTarget(channel, target);

        enforceIpLimit(clientIp);
        enforceTargetLimits(target, purpose);

        String code = randomCode(properties.getCodeLength());
        LocalDateTime now = LocalDateTime.now();
        VerificationCode entity = VerificationCode.builder()
                .target(target)
                .purpose(purpose)
                .codeHash(passwordEncoder.encode(code))
                // 只存哈希：明文仅在此刻存在，交给 Sender 投递
                .expiresAt(now.plusSeconds(properties.getCodeTtlSeconds()))
                .attempts(0)
                .createdAt(now)
                .build();
        verificationCodeRepository.save(entity);

        if (channel == VerificationChannel.SMS) {
            verificationCodeSender.sendSms(target, code);
        } else {
            verificationCodeSender.sendEmail(target, code);
        }
        log.info("验证码已发送: channel={}, purpose={}, target={}, expiresIn={}s",
                channel, purpose, mask(target, channel), properties.getCodeTtlSeconds());

        return new SendCodeResponse(
                channel.name().toLowerCase(Locale.ROOT),
                target,
                purpose.name(),
                properties.getCodeTtlSeconds(),
                properties.isDevMode() ? code : null);
    }

    @Override
    @Transactional
    public void verifyAndConsume(String targetRaw, VerificationChannel channel,
                                 VerificationPurpose purpose, String code) {
        String target = normalize(channel, targetRaw);
        VerificationCode entity = verificationCodeRepository
                .findFirstByTargetAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(target, purpose)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "请先获取验证码"));

        if (entity.isExpired()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "验证码已过期，请重新获取");
        }
        if (entity.getAttempts() >= properties.getMaxAttempts()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "验证码错误次数过多，请重新获取");
        }
        if (!StringUtils.hasText(code) || !passwordEncoder.matches(code.trim(), entity.getCodeHash())) {
            entity.setAttempts(entity.getAttempts() + 1);
            verificationCodeRepository.save(entity);
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    entity.getAttempts() >= properties.getMaxAttempts()
                            ? "验证码错误次数过多，请重新获取"
                            : "验证码错误");
        }

        entity.setConsumedAt(LocalDateTime.now());
        verificationCodeRepository.save(entity);
        log.info("验证码校验通过并消费: purpose={}, target={}", purpose, mask(target, channel));
    }

    @Override
    public String normalize(VerificationChannel channel, String targetRaw) {
        if (channel == null || targetRaw == null) {
            return "";
        }
        String trimmed = targetRaw.trim();
        return channel == VerificationChannel.EMAIL ? trimmed.toLowerCase(Locale.ROOT) : trimmed;
    }

    // ------------------------------------------------------------------ 内部

    private void validateTarget(VerificationChannel channel, String target) {
        if (!StringUtils.hasText(target)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "接收方不能为空");
        }
        if (channel == VerificationChannel.SMS && !CN_MOBILE.matcher(target).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "手机号格式不正确（需为中国大陆 11 位手机号，如 13800001111）: " + target);
        }
        if (channel == VerificationChannel.EMAIL && !EMAIL.matcher(target).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "邮箱格式不正确: " + target);
        }
    }

    /** 同 target+purpose：冷却期 + 小时上限 */
    private void enforceTargetLimits(String target, VerificationPurpose purpose) {
        LocalDateTime now = LocalDateTime.now();
        verificationCodeRepository.findFirstByTargetAndPurposeOrderByCreatedAtDesc(target, purpose)
                .ifPresent(last -> {
                    long gap = Duration.between(last.getCreatedAt(), now).getSeconds();
                    if (gap < properties.getResendIntervalSeconds()) {
                        long retryAfter = properties.getResendIntervalSeconds() - gap;
                        throw new TooManyRequestsException(
                                "验证码发送过于频繁，请 " + retryAfter + " 秒后再试", retryAfter);
                    }
                });

        long inLastHour = verificationCodeRepository.countByTargetAndPurposeAndCreatedAtAfter(
                target, purpose, now.minusHours(1));
        if (inLastHour >= properties.getMaxPerHour()) {
            throw new TooManyRequestsException(
                    "该接收方 1 小时内验证码发送次数已达上限（" + properties.getMaxPerHour() + " 次），请稍后再试",
                    Math.max(properties.getResendIntervalSeconds(), 60));
        }
    }

    /** 单 IP 小时上限（进程内滑动窗口） */
    private void enforceIpLimit(String clientIp) {
        if (!StringUtils.hasText(clientIp)) {
            return;
        }
        LocalDateTime threshold = LocalDateTime.now().minusHours(1);
        Deque<LocalDateTime> window = ipWindows.computeIfAbsent(clientIp, key -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && window.peekFirst().isBefore(threshold)) {
                window.pollFirst();
            }
            if (window.size() >= properties.getMaxPerIpPerHour()) {
                throw new TooManyRequestsException("该 IP 验证码发送过于频繁，请稍后再试", 3600);
            }
            window.addLast(LocalDateTime.now());
        }
    }

    private String randomCode(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
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

    @Override
    public void resetThrottleState() {
        ipWindows.clear();
    }
}
