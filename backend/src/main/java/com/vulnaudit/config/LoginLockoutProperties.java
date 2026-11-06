package com.vulnaudit.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 登录暴力破解防护配置（t123）
 *
 * <pre>
 * app:
 *   security:
 *     login-lockout:
 *       enabled: true               # 总开关（false 时完全回归旧行为）
 *       ip-max-failures: 5          # 同一 IP 在窗口期内的失败次数阈值
 *       ip-window-minutes: 15       # IP 失败计数窗口
 *       ip-lock-minutes: 15         # IP 锁定时长
 *       account-max-failures: 5     # 账户级失败次数上限（用于「可疑账号」判定）
 *       distinct-ip-threshold: 3    # 窗口期内来自多少个不同 IP 且均失败 → 升级锁账户
 *       distinct-ip-window-minutes: 30  # 「不同 IP」统计窗口
 *       account-lock-minutes: 30    # 账户锁定时长
 *       unlock-username-cooldown-seconds: 300  # OTP 解锁：同一 username 发码间隔
 *       unlock-max-per-ip-per-hour: 10         # OTP 解锁：单 IP 每小时发码上限
 * </pre>
 *
 * <p><b>两级模型</b>：先锁 IP（不影响账户，换网络可登录）；若锁 IP 期间该账户又从别的 IP 用错密码尝试，
 * 或窗口期内来自 ≥N 个不同 IP 且都失败 → 判定为目标化暴力破解，升级为账户锁定（任何 IP 的正确密码也拒绝）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.security.login-lockout")
public class LoginLockoutProperties {

    /** 总开关：关闭时登录流程完全回归旧行为 */
    private boolean enabled = true;

    /** 同一 IP 在窗口期内允许的失败次数 */
    private int ipMaxFailures = 5;

    /** IP 失败计数窗口（分钟） */
    private int ipWindowMinutes = 15;

    /** IP 锁定时长（分钟） */
    private int ipLockMinutes = 15;

    /** 账户级失败次数上限 */
    private int accountMaxFailures = 5;

    /** 窗口期内触发「升级锁账户」的不同 IP 数 */
    private int distinctIpThreshold = 3;

    /** 不同 IP 统计窗口（分钟） */
    private int distinctIpWindowMinutes = 30;

    /** 账户锁定时长（分钟） */
    private int accountLockMinutes = 30;

    /** OTP 解锁：同一 username 的发码冷却（秒） */
    private long unlockUsernameCooldownSeconds = 300;

    /** OTP 解锁：单 IP 每小时发码上限 */
    private int unlockMaxPerIpPerHour = 10;
}
