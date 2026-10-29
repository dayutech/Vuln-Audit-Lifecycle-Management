package com.vulnaudit.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

/**
 * 邮件发送器配置
 *
 * <p>仅当 {@code app.notify.mail.enabled=true} 时注册 {@link JavaMailSender}；
 * 未启用时容器中不存在该 Bean，邮件通道自动降级为仅站内通知。
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "app.notify.mail", name = "enabled", havingValue = "true")
public class MailConfig {

    /**
     * 基于 {@link NotificationProperties.Mail} 构建 SMTP 发送器
     *
     * <p>若容器中已存在其他 {@link JavaMailSender}（例如自行配置了 spring.mail.*），则不再重复注册。
     */
    @Bean
    @ConditionalOnMissingBean(JavaMailSender.class)
    public JavaMailSender notificationJavaMailSender(NotificationProperties properties) {
        NotificationProperties.Mail mail = properties.getMail();
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(mail.getHost());
        sender.setPort(mail.getPort());
        sender.setDefaultEncoding("UTF-8");

        boolean auth = mail.getUsername() != null && !mail.getUsername().isBlank();
        if (auth) {
            sender.setUsername(mail.getUsername());
            sender.setPassword(mail.getPassword());
        }

        Properties javaMailProperties = sender.getJavaMailProperties();
        javaMailProperties.put("mail.transport.protocol", "smtp");
        javaMailProperties.put("mail.smtp.auth", String.valueOf(auth));
        javaMailProperties.put("mail.smtp.starttls.enable", String.valueOf(mail.isStarttls()));
        javaMailProperties.put("mail.smtp.ssl.enable", String.valueOf(mail.isSsl()));
        javaMailProperties.put("mail.smtp.connectiontimeout", String.valueOf(mail.getTimeoutMs()));
        javaMailProperties.put("mail.smtp.timeout", String.valueOf(mail.getTimeoutMs()));
        javaMailProperties.put("mail.smtp.writetimeout", String.valueOf(mail.getTimeoutMs()));
        javaMailProperties.put("mail.debug", "false");

        log.info("邮件通道已启用: host={}, port={}, ssl={}, starttls={}, auth={}",
                mail.getHost(), mail.getPort(), mail.isSsl(), mail.isStarttls(), auth);
        if (mail.getHost() == null || mail.getHost().isBlank()) {
            log.warn("app.notify.mail.enabled=true 但 app.notify.mail.host 未配置，邮件通道将不可用，降级为仅站内通知");
        }
        if (mail.getFrom() == null || mail.getFrom().isBlank()) {
            log.warn("app.notify.mail.enabled=true 但 app.notify.mail.from 未配置，邮件通道将不可用，降级为仅站内通知");
        }
        return sender;
    }
}
