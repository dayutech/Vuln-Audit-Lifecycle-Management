package com.vulnaudit.service.notify;

import com.vulnaudit.config.NotificationProperties;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 邮件通道发送器单元测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("邮件通道发送器测试")
class EmailSenderTest {

    @Mock
    private ObjectProvider<JavaMailSender> mailSenderProvider;

    @Mock
    private JavaMailSender javaMailSender;

    private NotificationProperties properties;
    private EmailSender emailSender;

    @BeforeEach
    void setUp() {
        properties = new NotificationProperties();
        emailSender = new EmailSender(properties, mailSenderProvider);
    }

    private NotificationMessage message(String email) {
        return NotificationMessage.builder()
                .notificationId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .username("工程师")
                .recipientEmail(email)
                .type("task_assigned")
                .title("任务已分配")
                .content("请尽快处理")
                .priority("normal")
                .build();
    }

    @Test
    @DisplayName("默认配置未开启 - 通道不可用且发送被跳过，不抛异常")
    void defaultDisabled_isNotEnabledAndSkips() {
        assertFalse(emailSender.isEnabled());

        ChannelSendResult result = emailSender.send(message("engineer@test.com"));

        assertFalse(result.success());
        assertTrue(result.skipped());
        assertNotNull(result.error());
    }

    @Test
    @DisplayName("开启但 host 缺失 - 记录降级为不可用")
    void enabledWithoutHost_isNotEnabled() {
        properties.getMail().setEnabled(true);
        properties.getMail().setFrom("no-reply@test.com");

        assertFalse(emailSender.isEnabled());
        assertTrue(emailSender.send(message("engineer@test.com")).skipped());
    }

    @Test
    @DisplayName("开启但发件人缺失 - 记录降级为不可用")
    void enabledWithoutFrom_isNotEnabled() {
        properties.getMail().setEnabled(true);
        properties.getMail().setHost("smtp.test.com");

        assertFalse(emailSender.isEnabled());
    }

    @Test
    @DisplayName("开启且配置完整但无 JavaMailSender Bean - 降级为不可用")
    void enabledWithoutMailSenderBean_isNotEnabled() {
        properties.getMail().setEnabled(true);
        properties.getMail().setHost("smtp.test.com");
        properties.getMail().setFrom("no-reply@test.com");
        when(mailSenderProvider.getIfAvailable()).thenReturn(null);

        assertFalse(emailSender.isEnabled());
    }

    @Test
    @DisplayName("配置完整 - 邮件被真实投递且返回成功")
    void fullyConfigured_sendsMail() throws Exception {
        properties.getMail().setEnabled(true);
        properties.getMail().setHost("smtp.test.com");
        properties.getMail().setPort(465);
        properties.getMail().setFrom("no-reply@test.com");
        properties.getMail().setFromName("漏洞审计平台");
        when(mailSenderProvider.getIfAvailable()).thenReturn(javaMailSender);
        when(javaMailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        assertTrue(emailSender.isEnabled());
        ChannelSendResult result = emailSender.send(message("engineer@test.com"));

        assertTrue(result.success());
        assertFalse(result.skipped());
        verify(javaMailSender).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("SMTP 异常 - 转换为失败结果，不影响主流程")
    void sendFailure_returnsFailureResult() throws Exception {
        properties.getMail().setEnabled(true);
        properties.getMail().setHost("smtp.test.com");
        properties.getMail().setFrom("no-reply@test.com");
        when(mailSenderProvider.getIfAvailable()).thenReturn(javaMailSender);
        when(javaMailSender.createMimeMessage())
                .thenReturn(new MimeMessage(Session.getInstance(new Properties())));
        doThrow(new MailSendException("smtp down")).when(javaMailSender).send(any(MimeMessage.class));

        ChannelSendResult result = emailSender.send(message("engineer@test.com"));

        assertFalse(result.success());
        assertFalse(result.skipped());
        assertNotNull(result.error());
        assertTrue(result.error().contains("smtp down"));
    }

    @Test
    @DisplayName("接收人邮箱为空 - 跳过邮件通道")
    void blankRecipientEmail_skips() {
        properties.getMail().setEnabled(true);
        properties.getMail().setHost("smtp.test.com");
        properties.getMail().setFrom("no-reply@test.com");
        when(mailSenderProvider.getIfAvailable()).thenReturn(javaMailSender);

        ChannelSendResult result = emailSender.send(message(null));

        assertTrue(result.skipped());
    }
}
