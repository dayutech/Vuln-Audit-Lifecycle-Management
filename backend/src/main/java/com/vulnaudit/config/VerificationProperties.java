package com.vulnaudit.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 验证码策略配置（t102）
 *
 * <pre>
 * app:
 *   security:
 *     verification:
 *       dev-mode: false            # 开发/测试模式：send-code 响应里返回 devCode（生产必须 false）
 *       code-length: 6             # 验证码位数
 *       code-ttl-seconds: 300      # 有效期 5 分钟
 *       resend-interval-seconds: 300  # 同一 target+purpose 的发码间隔（≥ 此值才允许再发，否则 429）
 *       max-per-hour: 5            # 单 target+purpose 每小时上限
 *       max-attempts: 5            # 单个验证码允许的最大校验失败次数
 *       max-per-ip-per-hour: 20    # 单 IP 每小时发码上限（防批量刷）
 * </pre>
 *
 * <p><b>生产要求</b>：{@code dev-mode} 必须为 false，否则验证码会随响应回传，等于没有验证码。
 * 启动时不做强校验（测试需要打开），但 {@code VerificationCodeServiceImpl} 会在 dev-mode 打开时打 WARN。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.security.verification")
public class VerificationProperties {

    /** 开发/测试模式：响应回传 devCode。生产必须关闭 */
    private boolean devMode = false;

    /** 验证码位数（6 位数字） */
    private int codeLength = 6;

    /** 有效期（秒），默认 300 = 5 分钟 */
    private long codeTtlSeconds = 300L;

    /** 同 target+purpose 的最小发送间隔（秒），默认 300 */
    private long resendIntervalSeconds = 300L;

    /** 单 target+purpose 每小时上限，默认 5 */
    private int maxPerHour = 5;

    /** 单个验证码允许的校验失败次数，默认 5 */
    private int maxAttempts = 5;

    /** 单 IP 每小时发码上限，默认 20 */
    private int maxPerIpPerHour = 20;

    /** 忘记密码 confirm 连续失败上限（同一 IP，默认 5 次）——超过后 429 */
    private int resetMaxFailedAttempts = 5;

    /** 忘记密码失败计数窗口（分钟，默认 15） */
    private int resetFailedWindowMinutes = 15;
}
