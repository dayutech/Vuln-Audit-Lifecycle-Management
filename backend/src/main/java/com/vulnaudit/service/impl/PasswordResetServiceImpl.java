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
 * 忘记密码实现（t103 / t117 双因子）
 *
 * <p>复用 t102 的验证码基础设施（{@link VerificationCodeService} + {@code verification_codes} 表），不新增表。
 *
 * <h3>t117：用户名 + 手机/邮箱「双因子一致」</h3>
 * 只看手机号/邮箱就能重置密码是不安全的（手机号是可枚举的弱标识）。现在要求：
 * <b>username 存在</b> + <b>该用户的 phone/email 与 target 完全一致</b> + <b>该渠道已验证</b> + <b>账号可用</b>，
 * 四者同时成立才发码；confirm 阶段再校验一次 username↔target，防止「拿 A 的验证码改 B 的密码」。
 *
 * <p>防枚举：任一项不匹配一律 <b>返回 200 但不发码</b>，响应体与成功路径完全同构（devCode 为空），
 * 只在服务端写 WARN（不含敏感值）。
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

    /** username → 最近「双因子不匹配」时间戳（进程内滑动窗口，t117） */
    private final Map<String, Deque<LocalDateTime>> usernameFailures = new ConcurrentHashMap<>();

    /** username → 冷却截止时间（连续不匹配达到上限后 15 分钟，t117） */
    private final Map<String, LocalDateTime> usernameCooldownUntil = new ConcurrentHashMap<>();

    // ------------------------------------------------------------------ 发码

    @Override
    @Transactional
    public PasswordResetSendCodeResponse sendCode(String usernameRaw, String channelRaw, String targetRaw, String clientIp) {
        VerificationChannel channel = VerificationChannel.fromCode(channelRaw);
        if (channel == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "不支持的验证码渠道: " + channelRaw + "（支持: sms, email）");
        }
        String target = verificationCodeService.normalize(channel, targetRaw);
        if (!StringUtils.hasText(target)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "接收方不能为空");
        }
        String username = normalizeUsername(usernameRaw);
        if (!StringUtils.hasText(username)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户名不能为空");
        }

        // username 级冷却：连续「双因子不匹配」达到上限后，该用户名 15 分钟内一律不发码（仍返回 200）
        if (isUsernameCoolingDown(username)) {
            log.warn("忘记密码发码：该用户名处于冷却期，按成功返回但不发送: username={}", username);
            return silentSuccess(channel, target);
        }

        User user = resolveUser(username, channel, target);
        if (user == null) {
            // 防账号枚举：对外仍是 200，内部不发码；只在服务端留 WARN（不含手机号/邮箱明文）
            recordUsernameFailure(username);
            log.warn("忘记密码发码：用户名与接收方未匹配（不存在/不匹配/未验证/停用），按成功返回但不发送: "
                    + "username={}, channel={}, target={}", username, channel, mask(target, channel));
            return silentSuccess(channel, target);
        }

        SendCodeResponse sent = verificationCodeService.sendCode(
                new SendCodeRequest(channel.name().toLowerCase(Locale.ROOT), target,
                        VerificationPurpose.RESET_PASSWORD.name()), clientIp);

        clearUsernameFailures(username);
        log.info("忘记密码发码成功: userId={}, username={}, channel={}, target={}",
                user.getId(), user.getUsername(), channel, mask(target, channel));
        return new PasswordResetSendCodeResponse(sent.channel(), sent.target(), sent.purpose(),
                sent.expiresInSeconds(), sent.devCode());
    }

    // ------------------------------------------------------------------ 重置

    @Override
    @Transactional
    public void confirm(String usernameRaw, String targetRaw, String code, String newPassword, String clientIp) {
        enforceFailureLimit(clientIp);

        String username = normalizeUsername(usernameRaw);
        if (!StringUtils.hasText(username)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户名不能为空");
        }

        // 目标归一化：先按手机号，再按邮箱（模板格式不同，不会互相误判）
        String target = null;
        VerificationChannel channel = null;
        if (StringUtils.hasText(targetRaw)) {
            String phone = verificationCodeService.normalize(VerificationChannel.SMS, targetRaw);
            if (StringUtils.hasText(phone) && phone.matches(VerificationCodeService.CN_MOBILE_REGEX)) {
                target = phone;
                channel = VerificationChannel.SMS;
            } else {
                String email = verificationCodeService.normalize(VerificationChannel.EMAIL, targetRaw);
                if (StringUtils.hasText(email) && email.matches(VerificationCodeService.EMAIL_REGEX)) {
                    target = email;
                    channel = VerificationChannel.EMAIL;
                }
            }
        }

        // 再次校验 username ↔ target 属于同一账号（防止先拿 A 的验证码再改 B 的密码）
        User user = (channel == null) ? null : resolveUser(username, channel, target);
        if (user == null) {
            recordFailure(clientIp);
            log.warn("忘记密码重置：用户名与接收方未匹配，拒绝: username={}", username);
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

        clearUsernameFailures(username);
        auditLogService.log(user.getId(), "RESET_PASSWORD", "user", user.getId(),
                "忘记密码重置: username=" + user.getUsername()
                        + ", channel=" + channel.name().toLowerCase(Locale.ROOT)
                        + ", target=" + mask(target, channel)
                        + ", tokensInvalidBefore=" + invalidBefore);

        log.info("忘记密码重置成功: userId={}, username={}, channel={}, target={}",
                user.getId(), user.getUsername(), channel, mask(target, channel));
    }

    // ------------------------------------------------------------------ 内部

    /**
     * 双因子一致校验：username 存在 + 该用户的对应联系方式与 target 一致 + 该渠道已验证 + 账号可用
     *
     * @return 匹配到的用户；任一条件不成立返回 null（调用方按「静默成功」处理）
     */
    private User resolveUser(String username, VerificationChannel channel, String target) {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(target)) {
            return null;
        }
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null || !user.isActive()) {
            return null;
        }
        if (channel == VerificationChannel.SMS) {
            return user.isPhoneVerified() && target.equals(user.getPhone()) ? user : null;
        }
        return user.isEmailVerified() && user.getEmail() != null && target.equalsIgnoreCase(user.getEmail())
                ? user
                : null;
    }

    /** 与成功响应同构（devCode 为空），不泄露失败原因 */
    private PasswordResetSendCodeResponse silentSuccess(VerificationChannel channel, String target) {
        return new PasswordResetSendCodeResponse(
                channel.name().toLowerCase(Locale.ROOT), target,
                VerificationPurpose.RESET_PASSWORD.name(), properties.getCodeTtlSeconds(), null);
    }

    private String normalizeUsername(String raw) {
        return raw == null ? "" : raw.trim();
    }

    private boolean isUsernameCoolingDown(String username) {
        LocalDateTime until = usernameCooldownUntil.get(username);
        if (until == null) {
            return false;
        }
        if (until.isBefore(LocalDateTime.now())) {
            usernameCooldownUntil.remove(username);
            return false;
        }
        return true;
    }

    /** 记录一次「双因子不匹配」；达到上限 → 该 username 冷却 15 分钟 */
    private void recordUsernameFailure(String username) {
        if (!StringUtils.hasText(username)) {
            return;
        }
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(properties.getResetFailedWindowMinutes());
        Deque<LocalDateTime> window = usernameFailures.computeIfAbsent(username, key -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && window.peekFirst().isBefore(threshold)) {
                window.pollFirst();
            }
            window.addLast(LocalDateTime.now());
            if (window.size() >= properties.getResetMaxFailedAttempts()) {
                usernameCooldownUntil.put(username,
                        LocalDateTime.now().plusMinutes(properties.getResetFailedWindowMinutes()));
                window.clear();
                log.warn("忘记密码：该用户名连续不匹配达到上限，进入冷却: username={}, minutes={}",
                        username, properties.getResetFailedWindowMinutes());
            }
        }
    }

    private void clearUsernameFailures(String username) {
        if (!StringUtils.hasText(username)) {
            return;
        }
        usernameFailures.remove(username);
        usernameCooldownUntil.remove(username);
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
