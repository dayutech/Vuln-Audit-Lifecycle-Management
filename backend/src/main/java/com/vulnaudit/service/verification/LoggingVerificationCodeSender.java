package com.vulnaudit.service.verification;

import lombok.extern.slf4j.Slf4j;

/**
 * 默认验证码投递实现：只写 WARN 日志（t102）
 *
 * <p>用途：本地 / 测试 / 未接入短信网关的部署。生产接入真实网关时提供另一个
 * {@link VerificationCodeSender} Bean 即可自动覆盖本实现（见 {@code VerificationConfig}
 * 的 {@code @ConditionalOnMissingBean}）。
 *
 * <p><b>安全</b>：日志中只打印「脱敏后的 target」与验证码长度，<b>不打印明文验证码</b>，
 * 避免运维日志成为验证码旁路。
 */
@Slf4j
public class LoggingVerificationCodeSender implements VerificationCodeSender {

    @Override
    public void sendSms(String target, String code) {
        log.warn("[验证码][SMS] 未接入短信网关，仅记录: target={}, codeLength={}（明文不落日志）",
                maskPhone(target), code == null ? 0 : code.length());
    }

    @Override
    public void sendEmail(String target, String code) {
        log.warn("[验证码][EMAIL] 未接入邮件网关，仅记录: target={}, codeLength={}（明文不落日志）",
                maskEmail(target), code == null ? 0 : code.length());
    }

    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "****";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "****";
        }
        int at = email.indexOf('@');
        String name = email.substring(0, at);
        String domain = email.substring(at);
        if (name.length() <= 2) {
            return "**" + domain;
        }
        return name.charAt(0) + "***" + name.charAt(name.length() - 1) + domain;
    }
}
