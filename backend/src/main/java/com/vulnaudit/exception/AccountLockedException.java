package com.vulnaudit.exception;

import java.util.List;

/**
 * 账户锁定异常（t123）→ 全局异常处理器映射为 <b>423 Locked</b>
 *
 * <p>响应体除了统一错误结构外，还带上解锁方式与脱敏后的联系方式，便于前端直接引导用户走 OTP 解锁。
 * <b>不包含</b>密码、验证码或完整手机号/邮箱。
 */
public class AccountLockedException extends RuntimeException {

    private final long retryAfterSeconds;
    private final List<String> unlockMethods;
    private final List<String> maskedTargets;
    private final String reason;

    public AccountLockedException(String message, long retryAfterSeconds,
                                 List<String> unlockMethods, List<String> maskedTargets, String reason) {
        super(message);
        this.retryAfterSeconds = Math.max(retryAfterSeconds, 0);
        this.unlockMethods = unlockMethods == null ? List.of() : List.copyOf(unlockMethods);
        this.maskedTargets = maskedTargets == null ? List.of() : List.copyOf(maskedTargets);
        this.reason = reason;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }

    public List<String> getUnlockMethods() {
        return unlockMethods;
    }

    public List<String> getMaskedTargets() {
        return maskedTargets;
    }

    public String getReason() {
        return reason;
    }
}
