package com.vulnaudit.controller;

import com.vulnaudit.entity.NotificationRecipient;
import com.vulnaudit.entity.NotificationSetting;
import com.vulnaudit.repository.NotificationRecipientRepository;
import com.vulnaudit.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 通知通道基线集成测试
 *
 * <p>验证验收标准中的第一条：未配置任何外部通道时，通知行为与改造前一致，
 * 站内信正常落库、不抛出异常，接收记录标记为 in_app / sent。
 */
@DisplayName("通知通道基线集成测试")
class NotificationChannelBaselineTest extends BaseTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private NotificationRecipientRepository notificationRecipientRepository;

    @Test
    @DisplayName("默认未配置外部通道 - 仅站内且不报错")
    void sendNotification_defaultConfig_onlyInApp() {
        notificationService.sendNotification(engineerUserId, "task_assigned", "任务已分配", "您有一个新的审计任务");

        List<NotificationRecipient> recipients = notificationRecipientRepository.findByUserId(engineerUserId);
        assertEquals(1, recipients.size());
        NotificationRecipient recipient = recipients.get(0);
        assertEquals("in_app", recipient.getChannel());
        assertEquals("sent", recipient.getSendStatus());
        assertNotNull(recipient.getSentAt());
    }

    @Test
    @DisplayName("用户开启邮件与 Webhook 但通道未部署 - 仍降级为仅站内且不报错")
    void sendNotification_settingEnabledButChannelMissing_fallsBackToInApp() {
        NotificationSetting setting = NotificationSetting.builder()
                .userId(engineerUserId)
                .type("task_assigned")
                .enabled(true)
                .notifyEmail(true)
                .notifySms(false)
                .notifyWebhook(true)
                .build();
        notificationService.updateSetting(engineerUserId, "task_assigned", Objects.requireNonNull(setting));

        notificationService.sendBulkNotification(List.of(engineerUserId, leaderUserId),
                "task_assigned", "任务已分配", "您有一个新的审计任务");

        List<NotificationRecipient> engineerRecipients = notificationRecipientRepository.findByUserId(engineerUserId);
        List<NotificationRecipient> leaderRecipients = notificationRecipientRepository.findByUserId(leaderUserId);
        assertEquals(1, engineerRecipients.size());
        assertEquals(1, leaderRecipients.size());
        assertTrue(engineerRecipients.stream().allMatch(r -> "in_app".equals(r.getChannel())));
        assertTrue(engineerRecipients.stream().allMatch(r -> "sent".equals(r.getSendStatus())));
        assertTrue(leaderRecipients.stream().allMatch(r -> "in_app".equals(r.getChannel())));
    }
}
