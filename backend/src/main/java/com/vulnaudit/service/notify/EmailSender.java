package com.vulnaudit.service.notify;

import com.vulnaudit.config.NotificationProperties;
import jakarta.annotation.PostConstruct;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 邮件通道发送器
 *
 * <p>功能说明：
 * <ul>
 *     <li>仅在 {@code app.notify.mail.enabled=true} 且 host/from/JavaMailSender 均就绪时启用；</li>
 *     <li>配置缺失时记录 WARN 并 {@link #isEnabled()} 返回 false，上层降级为仅站内通知；</li>
 *     <li>发送异常全部内部捕获并转换为失败结果，不影响通知主流程。</li>
 * </ul>
 */
@Slf4j
@Component
public class EmailSender implements NotificationChannelSender {

    private final NotificationProperties properties;
    private final ObjectProvider<JavaMailSender> mailSenderProvider;

    public EmailSender(NotificationProperties properties, ObjectProvider<JavaMailSender> mailSenderProvider) {
        this.properties = properties;
        this.mailSenderProvider = mailSenderProvider;
    }

    /**
     * 启动时输出通道状态，便于运维确认是否已正确配置
     */
    @PostConstruct
    public void reportConfiguration() {
        NotificationProperties.Mail mail = properties.getMail();
        if (mail == null || !mail.isEnabled()) {
            log.info("邮件通道未启用（app.notify.mail.enabled=false），通知降级为仅站内");
            return;
        }
        if (!isEnabled()) {
            log.warn("邮件通道已开启但配置不完整（host/from 缺失或 JavaMailSender 未注册），降级为仅站内通知");
            return;
        }
        log.info("邮件通道已就绪: host={}, from={}", mail.getHost(), mail.getFrom());
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public boolean isEnabled() {
        NotificationProperties.Mail mail = properties.getMail();
        if (mail == null || !mail.isEnabled()) {
            return false;
        }
        if (isBlank(mail.getHost()) || isBlank(mail.getFrom())) {
            return false;
        }
        return resolveMailSender() != null;
    }

    /**
     * 解析容器中的 JavaMailSender
     *
     * <p>未注册或多实例冲突时返回 null，由调用方降级为仅站内通知。
     */
    private JavaMailSender resolveMailSender() {
        try {
            return mailSenderProvider.getIfAvailable();
        } catch (RuntimeException e) {
            log.warn("JavaMailSender 解析失败（可能存在多个邮件发送器实例），邮件通道降级为仅站内: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public ChannelSendResult send(NotificationMessage message) {
        if (message == null) {
            return ChannelSendResult.failure(NotificationChannel.EMAIL, "通知报文为空");
        }
        if (!isEnabled()) {
            return ChannelSendResult.skipped(NotificationChannel.EMAIL, "邮件通道未启用或配置不完整");
        }
        if (isBlank(message.recipientEmail())) {
            return ChannelSendResult.skipped(NotificationChannel.EMAIL, "接收人邮箱为空");
        }

        JavaMailSender mailSender = resolveMailSender();
        if (mailSender == null) {
            return ChannelSendResult.skipped(NotificationChannel.EMAIL, "JavaMailSender 未就绪");
        }
        try {
            NotificationProperties.Mail mail = properties.getMail();
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(mail.getFrom(), defaultFromName(mail), StandardCharsets.UTF_8.name()));
            helper.setTo(message.recipientEmail());
            helper.setSubject(subjectOf(message));
            helper.setText(bodyOf(message), false);

            mailSender.send(mimeMessage);
            log.info("邮件通知发送成功: notificationId={}, to={}", message.notificationId(), message.recipientEmail());
            return ChannelSendResult.success(NotificationChannel.EMAIL);
        } catch (Exception e) {
            String error = e.getClass().getSimpleName() + ": " + e.getMessage();
            log.warn("邮件通知发送失败，降级为仅站内: notificationId={}, to={}, error={}",
                    message.notificationId(), message.recipientEmail(), error);
            return ChannelSendResult.failure(NotificationChannel.EMAIL, error);
        }
    }

    private String subjectOf(NotificationMessage message) {
        String prefix = isBlank(message.type()) ? "[通知]" : "[" + message.type() + "]";
        String title = isBlank(message.title()) ? "系统通知" : message.title();
        return prefix + " " + title;
    }

    private String bodyOf(NotificationMessage message) {
        StringBuilder builder = new StringBuilder();
        builder.append("您好").append(isBlank(message.username()) ? "" : " " + message.username()).append("：\n\n");
        builder.append(isBlank(message.title()) ? "" : message.title() + "\n");
        builder.append(isBlank(message.content()) ? "" : message.content() + "\n");
        if (message.notificationId() != null) {
            builder.append("\n通知ID: ").append(message.notificationId());
        }
        builder.append("\n\n—— 漏洞审计生命周期管理平台（系统自动发送，请勿回复）");
        return builder.toString();
    }

    private String defaultFromName(NotificationProperties.Mail mail) {
        return isBlank(mail.getFromName()) ? "漏洞审计平台" : mail.getFromName();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
