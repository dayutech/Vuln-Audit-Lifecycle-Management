package com.vulnaudit.service;

import com.vulnaudit.entity.User;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 登录暴力破解防护（t123）：IP 优先锁定 → 跨 IP 升级锁账户 → 告警 → OTP 解锁
 *
 * <p>分层语义：
 * <ol>
 *   <li><b>IP 层</b>：同一 IP 在窗口期内失败达阈值 → 该 IP 的登录一律 429（带 Retry-After），
 *       但<b>账户不受影响</b>（换网络 + 正确密码仍可登录）；</li>
 *   <li><b>账户层（升级）</b>：处于「可疑」状态的账户被锁定 —— a) 某个 IP 已被锁期间该账户又从别的 IP 用错密码尝试；
 *       或 b) 窗口期内来自 ≥N 个不同 IP 且均失败。锁定期间<b>任何 IP 的正确密码也拒绝</b>（423），
 *       并写 {@code ACCOUNT_LOCKED} 审计 + 向已验证联系方式发安全告警。</li>
 * </ol>
 */
public interface LoginLockoutService {

    /** IP 是否处于锁定期（返回到期剩余秒数，0 表示未锁） */
    long ipLockRemainingSeconds(@Nullable String clientIp);

    /** 账户是否被锁定（返回剩余秒数，0 表示未锁） */
    long accountLockRemainingSeconds(@Nullable UUID userId);

    /**
     * 记录一次登录失败（IP 计数 + 账户计数 + 升级判定）
     *
     * @param user       命中的用户（用户名不存在时为 null：只做 IP 计数，避免账号枚举）
     * @param username   提交的用户名
     * @param clientIp   来源 IP
     * @return 若本次失败触发了账户锁定，返回 {@code true}（调用方据此返回 423）
     */
    boolean onLoginFailure(@Nullable User user, @Nullable String username, @Nullable String clientIp);

    /** 登录成功：清零该账户的失败计数与来源 IP 记录 */
    void onLoginSuccess(@Nullable User user);

    /** 管理员或系统解锁（写 {@code ACCOUNT_UNLOCKED} 审计） */
    void unlockByAdmin(@Nullable UUID userId, @Nullable UUID operatorId);

    /** OTP 解锁成功后的清理（写审计由调用方按需补充，这里只做状态清理 + 审计） */
    void unlockByOtp(@Nullable UUID userId);

    /** 该用户可用的解锁方式（sms / email，按已验证渠道给出） */
    List<String> unlockMethodsOf(@Nullable User user);

    /** 脱敏后的联系方式（与 {@link #unlockMethodsOf} 顺序对应） */
    List<String> maskedTargetsOf(@Nullable User user);

    /** 是否启用（关闭时整套行为回归旧逻辑） */
    boolean isEnabled();

    /** 仅供测试/运维：清空全部计数与锁定状态 */
    void reset();
}
