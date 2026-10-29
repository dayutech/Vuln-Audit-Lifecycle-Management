package com.vulnaudit.service.notify;

import com.vulnaudit.entity.Notification;
import com.vulnaudit.entity.NotificationRecipient;
import com.vulnaudit.entity.NotificationSetting;
import com.vulnaudit.entity.User;
import com.vulnaudit.repository.NotificationRecipientRepository;
import com.vulnaudit.repository.NotificationSettingRepository;
import com.vulnaudit.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 通知分发服务
 *
 * <p>职责：
 * <ul>
 *     <li>根据 {@link NotificationSetting} 中用户对通知类型（type）的偏好，决定是否走邮件 / Webhook；</li>
 *     <li>调用已启用的 {@link NotificationChannelSender}，把结果回写到
 *         {@code notification_recipients} 的 channel / send_status / sent_at / error_message；</li>
 *     <li>任何外部通道异常都只影响该接收人的发送状态，绝不中断站内信主流程。</li>
 * </ul>
 *
 * <p>默认（未配置任何通道）时行为与改造前一致：仅写入站内信，接收记录标记为 in_app / sent。
 */
@Slf4j
@Service
public class NotificationDispatchService {

    /**
     * 发送成功
     */
    public static final String STATUS_SENT = "sent";

    /**
     * 发送失败
     */
    public static final String STATUS_FAILED = "failed";

    /**
     * 部分通道成功
     */
    public static final String STATUS_PARTIAL = "partial";

    private static final int ERROR_MESSAGE_MAX_LENGTH = 500;

    private final NotificationSettingRepository notificationSettingRepository;
    private final NotificationRecipientRepository notificationRecipientRepository;
    private final UserRepository userRepository;
    private final List<NotificationChannelSender> channelSenders;

    public NotificationDispatchService(NotificationSettingRepository notificationSettingRepository,
                                       NotificationRecipientRepository notificationRecipientRepository,
                                       UserRepository userRepository,
                                       List<NotificationChannelSender> channelSenders) {
        this.notificationSettingRepository = notificationSettingRepository;
        this.notificationRecipientRepository = notificationRecipientRepository;
        this.userRepository = userRepository;
        this.channelSenders = channelSenders == null ? List.of() : List.copyOf(channelSenders);
    }

    /**
     * 按通知ID查询接收人并分发
     */
    public void dispatch(Notification notification) {
        if (notification == null || notification.getId() == null) {
            return;
        }
        dispatch(notification, notificationRecipientRepository.findByNotificationId(notification.getId()));
    }

    /**
     * 对给定的接收人列表执行分发与状态回写
     *
     * @param notification 通知实体
     * @param recipients   接收人记录（会被就地更新并保存）
     */
    public void dispatch(Notification notification, List<NotificationRecipient> recipients) {
        if (notification == null || recipients == null || recipients.isEmpty()) {
            return;
        }

        String type = normalizeType(notification.getType());
        List<NotificationRecipient> updated = new ArrayList<>(recipients.size());
        for (NotificationRecipient recipient : recipients) {
            if (recipient == null) {
                continue;
            }
            try {
                applyDispatch(notification, type, recipient);
            } catch (RuntimeException e) {
                log.warn("通知分发异常，降级为仅站内: notificationId={}, userId={}, error={}",
                        notification.getId(), recipient.getUserId(), e.getMessage(), e);
                markInAppOnly(recipient);
            }
            updated.add(recipient);
        }

        try {
            notificationRecipientRepository.saveAll(updated);
        } catch (RuntimeException e) {
            // 状态回写失败不能影响站内信本身
            log.warn("通知发送状态回写失败: notificationId={}, error={}", notification.getId(), e.getMessage());
        }
    }

    /**
     * 单个接收人的分发逻辑
     */
    private void applyDispatch(Notification notification, String type, NotificationRecipient recipient) {
        NotificationSetting setting = loadSetting(recipient.getUserId(), type);
        User user = recipient.getUserId() == null ? null
                : userRepository.findById(recipient.getUserId()).orElse(null);

        List<NotificationChannelSender> activeSenders = new ArrayList<>();
        for (NotificationChannelSender sender : channelSenders) {
            if (sender == null || !sender.isEnabled()) {
                continue;
            }
            if (!acceptedBySetting(sender.channel(), setting)) {
                continue;
            }
            if (sender.channel() == NotificationChannel.EMAIL
                    && (user == null || isBlank(user.getEmail()))) {
                log.debug("接收人邮箱为空，跳过邮件通道: userId={}", recipient.getUserId());
                continue;
            }
            activeSenders.add(sender);
        }

        if (activeSenders.isEmpty()) {
            markInAppOnly(recipient);
            return;
        }

        NotificationMessage message = buildMessage(notification, type, recipient, user);

        Set<String> channels = new LinkedHashSet<>();
        List<String> errors = new ArrayList<>();
        int successCount = 0;
        for (NotificationChannelSender sender : activeSenders) {
            ChannelSendResult result;
            try {
                result = sender.send(message);
            } catch (RuntimeException e) {
                log.warn("通道发送异常: channel={}, notificationId={}, error={}",
                        sender.channel(), notification.getId(), e.getMessage());
                result = ChannelSendResult.failure(sender.channel(), e.getMessage());
            }
            if (result == null) {
                continue;
            }
            if (result.skipped()) {
                // 通道自检未通过（未配置），不计入本次发送渠道
                continue;
            }
            channels.add(result.channel() == null ? sender.channel().getCode() : result.channel().getCode());
            if (result.success()) {
                successCount++;
            } else if (result.error() != null && !result.error().isBlank()) {
                errors.add(result.channel().getCode() + ": " + result.error());
            }
        }

        if (channels.isEmpty()) {
            // 所有通道都跳过（配置缺失），按仅站内处理
            markInAppOnly(recipient);
            return;
        }

        recipient.setChannel(String.join(",", channels));
        if (errors.isEmpty()) {
            recipient.setSendStatus(STATUS_SENT);
            recipient.setSentAt(LocalDateTime.now());
            recipient.setErrorMessage(null);
        } else if (successCount > 0) {
            recipient.setSendStatus(STATUS_PARTIAL);
            recipient.setSentAt(LocalDateTime.now());
            recipient.setErrorMessage(truncate(String.join("; ", errors)));
        } else {
            recipient.setSendStatus(STATUS_FAILED);
            recipient.setSentAt(null);
            recipient.setErrorMessage(truncate(String.join("; ", errors)));
        }

        if (STATUS_FAILED.equals(recipient.getSendStatus())) {
            log.warn("通知外部通道全部失败: notificationId={}, userId={}, error={}",
                    notification.getId(), recipient.getUserId(), recipient.getErrorMessage());
        } else {
            log.info("通知分发完成: notificationId={}, userId={}, channel={}, status={}",
                    notification.getId(), recipient.getUserId(), recipient.getChannel(), recipient.getSendStatus());
        }
    }

    /**
     * 仅站内：不改变外部通道字段，仅标记站内投递成功
     */
    private void markInAppOnly(NotificationRecipient recipient) {
        recipient.setChannel(NotificationChannel.IN_APP.getCode());
        recipient.setSendStatus(STATUS_SENT);
        recipient.setSentAt(LocalDateTime.now());
        recipient.setErrorMessage(null);
    }

    private NotificationSetting loadSetting(UUID userId, String type) {
        if (userId == null || type == null) {
            return null;
        }
        return notificationSettingRepository.findByUserIdAndType(userId, type).orElse(null);
    }

    /**
     * 判断用户设置是否允许该通道
     *
     * <p>规则：
     * <ul>
     *     <li>无设置记录 / 设置为 null：不允许外部通道（与改造前一致，仅站内）；</li>
     *     <li>设置 enabled=false：该类型通知不推送外部通道；</li>
     *     <li>notify_email / notify_webhook 为 true 时才允许对应通道。</li>
     * </ul>
     */
    private boolean acceptedBySetting(NotificationChannel channel, NotificationSetting setting) {
        if (setting == null) {
            return false;
        }
        if (Boolean.FALSE.equals(setting.getEnabled())) {
            return false;
        }
        return switch (channel) {
            case EMAIL -> Boolean.TRUE.equals(setting.getNotifyEmail());
            case WEBHOOK -> Boolean.TRUE.equals(setting.getNotifyWebhook());
            // 短信通道尚未接入网关，仅站内；站内信由主流程负责
            case SMS, IN_APP -> false;
        };
    }

    private NotificationMessage buildMessage(Notification notification, String type,
                                             NotificationRecipient recipient, User user) {
        return NotificationMessage.builder()
                .notificationId(notification.getId())
                .userId(recipient.getUserId())
                .username(user == null ? null : user.getFullName())
                .recipientEmail(user == null ? null : user.getEmail())
                .recipientPhone(user == null ? null : user.getPhone())
                .type(type)
                .title(notification.getTitle())
                .content(notification.getContent())
                .priority(notification.getPriority())
                .relatedType(notification.getRelatedType())
                .relatedId(notification.getRelatedId())
                .build();
    }

    private static String normalizeType(String type) {
        return isBlank(type) ? "system" : type;
    }

    private static String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= ERROR_MESSAGE_MAX_LENGTH ? value : value.substring(0, ERROR_MESSAGE_MAX_LENGTH);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
