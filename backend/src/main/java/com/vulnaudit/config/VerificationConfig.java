package com.vulnaudit.config;

import com.vulnaudit.service.verification.LoggingVerificationCodeSender;
import com.vulnaudit.service.verification.VerificationCodeSender;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 验证码投递装配（t102）
 *
 * <p>默认装配「只写日志」的 {@link LoggingVerificationCodeSender}；
 * 生产接入真实短信/邮件网关时，只要提供一个 {@link VerificationCodeSender} Bean，
 * 本默认实现会自动让位（{@code @ConditionalOnMissingBean}），业务代码无需改动。
 */
@Configuration
public class VerificationConfig {

    @Bean
    @ConditionalOnMissingBean(VerificationCodeSender.class)
    public VerificationCodeSender verificationCodeSender() {
        return new LoggingVerificationCodeSender();
    }
}
