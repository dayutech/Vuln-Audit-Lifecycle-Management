package com.vulnaudit.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 双因子与 remember-me 配置（t124）
 *
 * <pre>
 * app:
 *   security:
 *     two-factor:
 *       totp-secret-key: ${APP_TOTP_SECRET_KEY:}   # AES-GCM 主密钥；为空则 2FA 功能禁用（绝不落库明文）
 *       issuer: OwlAudit                            # otpauth 的 issuer
 *       challenge-ttl-seconds: 300                  # 登录挑战 token 有效期
 *       challenge-max-attempts: 5                   # 挑战期内允许的错误次数（超过即作废）
 *     remember-me:
 *       enabled: true
 *       ttl-days: 30
 *       cookie-name: owl_rm
 *       secure: true                                # 生产必须 true（HTTPS）
 * </pre>
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.security")
public class TwoFactorProperties {

    private TwoFactor twoFactor = new TwoFactor();
    private RememberMe rememberMe = new RememberMe();

    /** TOTP 主密钥（环境变量 APP_TOTP_SECRET_KEY） */
    public String getTotpSecretKey() {
        return twoFactor.getTotpSecretKey();
    }

    /** 兼容旧调用：主密钥直取 */
    public String totpSecretKey() {
        return twoFactor.getTotpSecretKey();
    }

    @Data
    public static class TwoFactor {
        private String totpSecretKey = "";
        private String issuer = "OwlAudit";
        private long challengeTtlSeconds = 300;
        private int challengeMaxAttempts = 5;
    }

    @Data
    public static class RememberMe {
        private boolean enabled = true;
        private int ttlDays = 30;
        private String cookieName = "owl_rm";
        private boolean secure = true;
    }
}
