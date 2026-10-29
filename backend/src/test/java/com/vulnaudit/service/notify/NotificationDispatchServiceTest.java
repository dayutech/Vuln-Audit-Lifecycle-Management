package com.vulnaudit.service.notify;

import com.vulnaudit.entity.Notification;
import com.vulnaudit.entity.NotificationRecipient;
import com.vulnaudit.entity.NotificationSetting;
import com.vulnaudit.entity.User;
import com.vulnaudit.repository.NotificationRecipientRepository;
import com.vulnaudit.repository.NotificationSettingRepository;
import com.vulnaudit.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 通知分发服务单元测试
 *
 * <p>覆盖：
 * <ul>
 *     <li>未配置任何外部通道时降级为仅站内（行为与改造前一致，不抛异常）；</li>
 *     <li>用户设置开启后邮件 / Webhook 发送器被真实调用；</li>
 *     <li>发送失败 / 发送异常 / 部分成功时的状态回写。</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("通知分发服务测试")
class NotificationDispatchServiceTest {

    @Mock
    private NotificationSettingRepository notificationSettingRepository;

    @Mock
    private NotificationRecipientRepository notificationRecipientRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationChannelSender emailSender;

    @Mock
    private NotificationChannelSender webhookSender;

    private NotificationDispatchService dispatchService;

    private UUID userId;
    private UUID notificationId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        notificationId = UUID.randomUUID();
        dispatchService = new NotificationDispatchService(
                notificationSettingRepository,
                notificationRecipientRepository,
                userRepository,
                List.of(emailSender, webhookSender));

        when(emailSender.channel()).thenReturn(NotificationChannel.EMAIL);
        when(webhookSender.channel()).thenReturn(NotificationChannel.WEBHOOK);
        when(notificationRecipientRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private Notification notification(String type) {
        return Notification.builder()
                .id(notificationId)
                .type(type)
                .title("任务已分配")
                .content("您有一个新的审计任务")
                .priority("normal")
                .build();
    }

    private NotificationRecipient recipient() {
        return NotificationRecipient.builder()
                .id(UUID.randomUUID())
                .notificationId(notificationId)
                .userId(userId)
                .build();
    }

    private NotificationSetting setting(boolean enabled, boolean email, boolean webhook) {
        return NotificationSetting.builder()
                .id(UUID.randomUUID())
                .userId(userId)
                .type("task_assigned")
                .enabled(enabled)
                .notifyEmail(email)
                .notifySms(false)
                .notifyWebhook(webhook)
                .build();
    }

    private User user(String email) {
        return User.builder()
                .id(userId)
                .username("engineer1")
                .email(email)
                .fullName("工程师")
                .passwordHash("hash")
                .status("active")
                .build();
    }

    @Test
    @DisplayName("未配置任何通道 - 降级为仅站内且不报错")
    void dispatch_noChannelEnabled_fallsBackToInAppOnly() {
        when(emailSender.isEnabled()).thenReturn(false);
        when(webhookSender.isEnabled()).thenReturn(false);
        when(notificationSettingRepository.findByUserIdAndType(eq(userId), any()))
                .thenReturn(Optional.of(setting(true, true, true)));

        NotificationRecipient recipient = recipient();
        dispatchService.dispatch(notification("task_assigned"), List.of(recipient));

        assertEquals(NotificationChannel.IN_APP.getCode(), recipient.getChannel());
        assertEquals(NotificationDispatchService.STATUS_SENT, recipient.getSendStatus());
        assertNotNull(recipient.getSentAt());
        assertNull(recipient.getErrorMessage());
        verify(emailSender, never()).send(any());
        verify(webhookSender, never()).send(any());
        verify(notificationRecipientRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("用户开启邮件通知 - 邮件发送器被调用且状态回写为 sent")
    void dispatch_emailEnabledBySetting_callsEmailSender() {
        when(emailSender.isEnabled()).thenReturn(true);
        when(webhookSender.isEnabled()).thenReturn(false);
        when(notificationSettingRepository.findByUserIdAndType(userId, "task_assigned"))
                .thenReturn(Optional.of(setting(true, true, false)));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user("engineer@test.com")));
        when(emailSender.send(any())).thenReturn(ChannelSendResult.success(NotificationChannel.EMAIL));

        NotificationRecipient recipient = recipient();
        dispatchService.dispatch(notification("task_assigned"), List.of(recipient));

        ArgumentCaptor<NotificationMessage> captor = ArgumentCaptor.forClass(NotificationMessage.class);
        verify(emailSender).send(captor.capture());
        assertEquals("engineer@test.com", captor.getValue().recipientEmail());
        assertEquals(notificationId, captor.getValue().notificationId());
        assertEquals("任务已分配", captor.getValue().title());

        assertEquals(NotificationChannel.EMAIL.getCode(), recipient.getChannel());
        assertEquals(NotificationDispatchService.STATUS_SENT, recipient.getSendStatus());
        assertNotNull(recipient.getSentAt());
        assertNull(recipient.getErrorMessage());
        verify(webhookSender, never()).send(any());
    }

    @Test
    @DisplayName("用户未设置通知偏好 - 不调用外部通道")
    void dispatch_noSettingRecord_skipsExternalChannels() {
        when(emailSender.isEnabled()).thenReturn(true);
        when(webhookSender.isEnabled()).thenReturn(true);
        when(notificationSettingRepository.findByUserIdAndType(userId, "task_assigned"))
                .thenReturn(Optional.empty());

        NotificationRecipient recipient = recipient();
        dispatchService.dispatch(notification("task_assigned"), List.of(recipient));

        assertEquals(NotificationChannel.IN_APP.getCode(), recipient.getChannel());
        assertEquals(NotificationDispatchService.STATUS_SENT, recipient.getSendStatus());
        verify(emailSender, never()).send(any());
        verify(webhookSender, never()).send(any());
    }

    @Test
    @DisplayName("用户关闭该类型通知 - 不调用外部通道")
    void dispatch_settingDisabled_skipsExternalChannels() {
        when(emailSender.isEnabled()).thenReturn(true);
        when(webhookSender.isEnabled()).thenReturn(true);
        when(notificationSettingRepository.findByUserIdAndType(userId, "task_assigned"))
                .thenReturn(Optional.of(setting(false, true, true)));

        NotificationRecipient recipient = recipient();
        dispatchService.dispatch(notification("task_assigned"), List.of(recipient));

        assertEquals(NotificationChannel.IN_APP.getCode(), recipient.getChannel());
        verify(emailSender, never()).send(any());
        verify(webhookSender, never()).send(any());
    }

    @Test
    @DisplayName("邮件通道发送失败 - 状态回写 failed 并记录 error_message")
    void dispatch_emailFailure_writesFailedStatus() {
        when(emailSender.isEnabled()).thenReturn(true);
        when(webhookSender.isEnabled()).thenReturn(false);
        when(notificationSettingRepository.findByUserIdAndType(userId, "task_assigned"))
                .thenReturn(Optional.of(setting(true, true, false)));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user("engineer@test.com")));
        when(emailSender.send(any()))
                .thenReturn(ChannelSendResult.failure(NotificationChannel.EMAIL, "SMTP timeout"));

        NotificationRecipient recipient = recipient();
        dispatchService.dispatch(notification("task_assigned"), List.of(recipient));

        assertEquals(NotificationChannel.EMAIL.getCode(), recipient.getChannel());
        assertEquals(NotificationDispatchService.STATUS_FAILED, recipient.getSendStatus());
        assertNull(recipient.getSentAt());
        assertNotNull(recipient.getErrorMessage());
        assertTrue(recipient.getErrorMessage().contains("SMTP timeout"));
    }

    @Test
    @DisplayName("邮件成功但 Webhook 失败 - 状态回写 partial")
    void dispatch_partialSuccess_writesPartialStatus() {
        when(emailSender.isEnabled()).thenReturn(true);
        when(webhookSender.isEnabled()).thenReturn(true);
        when(notificationSettingRepository.findByUserIdAndType(userId, "task_assigned"))
                .thenReturn(Optional.of(setting(true, true, true)));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user("engineer@test.com")));
        when(emailSender.send(any())).thenReturn(ChannelSendResult.success(NotificationChannel.EMAIL));
        when(webhookSender.send(any()))
                .thenReturn(ChannelSendResult.failure(NotificationChannel.WEBHOOK, "HTTP 502"));

        NotificationRecipient recipient = recipient();
        dispatchService.dispatch(notification("task_assigned"), List.of(recipient));

        assertEquals("email,webhook", recipient.getChannel());
        assertEquals(NotificationDispatchService.STATUS_PARTIAL, recipient.getSendStatus());
        assertNotNull(recipient.getSentAt());
        assertTrue(recipient.getErrorMessage().contains("HTTP 502"));
    }

    @Test
    @DisplayName("接收人邮箱为空 - 跳过邮件通道并降级为仅站内")
    void dispatch_blankRecipientEmail_skipsEmailChannel() {
        when(emailSender.isEnabled()).thenReturn(true);
        when(webhookSender.isEnabled()).thenReturn(false);
        when(notificationSettingRepository.findByUserIdAndType(userId, "task_assigned"))
                .thenReturn(Optional.of(setting(true, true, false)));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(null)));

        NotificationRecipient recipient = recipient();
        dispatchService.dispatch(notification("task_assigned"), List.of(recipient));

        assertEquals(NotificationChannel.IN_APP.getCode(), recipient.getChannel());
        assertEquals(NotificationDispatchService.STATUS_SENT, recipient.getSendStatus());
        verify(emailSender, never()).send(any());
    }

    @Test
    @DisplayName("发送器抛异常 - 不影响主流程，状态回写 failed")
    void dispatch_senderThrows_doesNotPropagate() {
        when(emailSender.isEnabled()).thenReturn(true);
        when(webhookSender.isEnabled()).thenReturn(false);
        when(notificationSettingRepository.findByUserIdAndType(userId, "task_assigned"))
                .thenReturn(Optional.of(setting(true, true, false)));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user("engineer@test.com")));
        when(emailSender.send(any())).thenThrow(new IllegalStateException("boom"));

        NotificationRecipient recipient = recipient();
        dispatchService.dispatch(notification("task_assigned"), List.of(recipient));

        assertEquals(NotificationDispatchService.STATUS_FAILED, recipient.getSendStatus());
        assertTrue(recipient.getErrorMessage().contains("boom"));
    }

    @Test
    @DisplayName("接收人列表为空 - 直接返回不做任何写入")
    void dispatch_emptyRecipients_noop() {
        dispatchService.dispatch(notification("task_assigned"), List.of());

        verify(notificationRecipientRepository, never()).saveAll(anyList());
        verify(emailSender, never()).send(any());
        verify(webhookSender, never()).send(any());
    }

    @Test
    @DisplayName("Webhook 通道示例 - 仅开启 Webhook 时渠道为 webhook")
    void dispatch_webhookOnly_writesWebhookChannel() {
        when(emailSender.isEnabled()).thenReturn(false);
        when(webhookSender.isEnabled()).thenReturn(true);
        when(notificationSettingRepository.findByUserIdAndType(userId, "task_assigned"))
                .thenReturn(Optional.of(setting(true, false, true)));
        when(webhookSender.send(any())).thenReturn(ChannelSendResult.success(NotificationChannel.WEBHOOK, 2));

        NotificationRecipient recipient = recipient();
        dispatchService.dispatch(notification("task_assigned"), List.of(recipient));

        assertEquals(NotificationChannel.WEBHOOK.getCode(), recipient.getChannel());
        assertEquals(NotificationDispatchService.STATUS_SENT, recipient.getSendStatus());
        assertFalse(recipient.getChannel().contains("in_app"));
        verify(emailSender, never()).send(any());
    }
}
