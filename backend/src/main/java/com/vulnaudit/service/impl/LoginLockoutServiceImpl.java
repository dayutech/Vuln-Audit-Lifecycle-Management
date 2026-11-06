package com.vulnaudit.service.impl;

import com.vulnaudit.config.LoginLockoutProperties;
import com.vulnaudit.entity.User;
import com.vulnaudit.security.LoginLockoutTracker;
import com.vulnaudit.service.AuditLogService;
import com.vulnaudit.service.LoginLockoutService;
import com.vulnaudit.service.verification.VerificationCodeSender;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 登录暴力破解防护实现（t123）
 *
 * <p>计数器在 {@link LoginLockoutTracker}（进程内，带 TTL 清理）；本类负责升级判定后的**副作用**：
 * 审计 {@code ACCOUNT_LOCKED} / {@code ACCOUNT_UNLOCKED} 与安全告警投递。
 *
 * <p>告警发送失败**不得**影响锁定生效：发送异常被吞掉并打 WARN。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LoginLockoutServiceImpl implements LoginLockoutService {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LoginLockoutProperties properties;
    private final LoginLockoutTracker tracker;
    private final AuditLogService auditLogService;
    private final VerificationCodeSender verificationCodeSender;

    @Override
    public boolean isEnabled() {
        return properties.isEnabled();
    }

    @Override
    public long ipLockRemainingSeconds(String clientIp) {
        if (!properties.isEnabled()) {
            return 0;
        }
        return tracker.ipLockRemainingSeconds(clientIp);
    }

    @Override
    public long accountLockRemainingSeconds(UUID userId) {
        if (!properties.isEnabled()) {
            return 0;
        }
        return tracker.accountLockRemainingSeconds(userId);
    }

    @Override
    public boolean onLoginFailure(User user, String username, String clientIp) {
        if (!properties.isEnabled()) {
            return false;
        }
        // 第一层：IP 计数（用户名不存在时也只做这一层，避免账号枚举）
        boolean ipJustLocked = tracker.recordIpFailure(clientIp);
        if (ipJustLocked) {
            log.warn("IP 登录失败次数达到阈值，已锁定该 IP: ip={}, minutes={}",
                    clientIp, properties.getIpLockMinutes());
        }
        if (user == null) {
            return false;
        }

        UUID userId = user.getId();
        // 已被锁定的账户无需重复升级/重复告警
        if (tracker.isAccountLocked(userId)) {
            return true;
        }

        String escalationReason = tracker.recordAccountFailure(userId, clientIp);
        if (escalationReason == null) {
            return false;
        }

        tracker.lockAccount(userId, escalationReason);
        List<String> ips = tracker.recentIps(userId);
        String details = "登录暴力破解触发账户锁定: username=" + user.getUsername()
                + ", reason=" + escalationReason
                + ", ips=" + String.join(",", ips)
                + ", lockMinutes=" + properties.getAccountLockMinutes();
        auditLogService.log(userId, "ACCOUNT_LOCKED", "user", userId, details);
        sendSecurityAlert(user, ips);
        return true;
    }

    @Override
    public void onLoginSuccess(User user) {
        if (user == null) {
            return;
        }
        tracker.clearAccount(user.getId());
    }

    @Override
    public void unlockByAdmin(UUID userId, UUID operatorId) {
        if (userId == null) {
            return;
        }
        tracker.clearAccount(userId);
        auditLogService.log(operatorId, "ACCOUNT_UNLOCKED", "user", userId,
                "管理员解锁账户: userId=" + userId + ", by=admin");
        log.info("账户已由管理员解锁: userId={}, operatorId={}", userId, operatorId);
    }

    @Override
    public void unlockByOtp(UUID userId) {
        if (userId == null) {
            return;
        }
        tracker.clearAccount(userId);
        auditLogService.log(userId, "ACCOUNT_UNLOCKED", "user", userId,
                "账户已通过 OTP 解锁: userId=" + userId + ", by=otp");
        log.info("账户已通过 OTP 解锁: userId={}", userId);
    }

    @Override
    public List<String> unlockMethodsOf(User user) {
        List<String> methods = new ArrayList<>();
        if (user == null) {
            return methods;
        }
        if (user.isPhoneVerified() && StringUtils.hasText(user.getPhone())) {
            methods.add("sms");
        }
        if (user.isEmailVerified() && StringUtils.hasText(user.getEmail())) {
            methods.add("email");
        }
        return methods;
    }

    @Override
    public List<String> maskedTargetsOf(User user) {
        List<String> targets = new ArrayList<>();
        if (user == null) {
            return targets;
        }
        if (user.isPhoneVerified() && StringUtils.hasText(user.getPhone())) {
            targets.add(maskPhone(user.getPhone()));
        }
        if (user.isEmailVerified() && StringUtils.hasText(user.getEmail())) {
            targets.add(maskEmail(user.getEmail()));
        }
        return targets;
    }

    @Override
    public void reset() {
        tracker.reset();
    }

    // ------------------------------------------------------------------ 内部

    /**
     * 发送安全告警（手机 + 邮箱各一份）
     *
     * <p>内容只含时间、来源 IP、解锁方式；<b>不含</b>密码与验证码。发送失败仅 WARN，不影响锁定。
     */
    private void sendSecurityAlert(User user, List<String> ips) {
        String time = LocalDateTime.now().format(TIME_FORMAT);
        String ipText = ips.isEmpty() ? "未知" : String.join(", ", ips);
        String methods = String.join(" / ", unlockMethodsOf(user));
        String message = "【安全告警】您的账户在 " + time + " 因多次登录失败被临时锁定。"
                + "近期登录来源 IP：" + ipText + "。"
                + "若非本人操作，请立即通过 " + (methods.isEmpty() ? "管理员" : methods)
                + " 验证解封并修改密码。本消息不含密码或验证码。";

        if (user.isPhoneVerified() && StringUtils.hasText(user.getPhone())) {
            try {
                verificationCodeSender.sendSecurityAlert(user.getPhone(), "sms", message);
            } catch (RuntimeException ex) {
                log.warn("安全告警短信发送失败（不影响锁定）: userId={}, error={}", user.getId(), ex.getMessage());
            }
        }
        if (user.isEmailVerified() && StringUtils.hasText(user.getEmail())) {
            try {
                verificationCodeSender.sendSecurityAlert(user.getEmail(), "email", message);
            } catch (RuntimeException ex) {
                log.warn("安全告警邮件发送失败（不影响锁定）: userId={}, error={}", user.getId(), ex.getMessage());
            }
        }
    }

    private String maskPhone(String phone) {
        if (phone.length() >= 7) {
            return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
        }
        return "****";
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "****";
        }
        return email.charAt(0) + "***" + email.substring(at).toLowerCase(Locale.ROOT);
    }
}
