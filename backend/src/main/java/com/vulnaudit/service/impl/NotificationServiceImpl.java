package com.vulnaudit.service.impl;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.Notification;
import com.vulnaudit.entity.NotificationRecipient;
import com.vulnaudit.entity.NotificationSetting;
import com.vulnaudit.entity.SystemAnnouncement;
import com.vulnaudit.entity.AssetSubscription;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.NotificationRecipientRepository;
import com.vulnaudit.repository.NotificationRepository;
import com.vulnaudit.repository.NotificationSettingRepository;
import com.vulnaudit.repository.SystemAnnouncementRepository;
import com.vulnaudit.repository.AssetSubscriptionRepository;
import com.vulnaudit.service.NotificationService;
import com.vulnaudit.service.notify.NotificationDispatchService;
import com.vulnaudit.service.notify.NotificationTypes;
import com.vulnaudit.util.Paging;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * 通知服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationRecipientRepository notificationRecipientRepository;
    private final NotificationSettingRepository notificationSettingRepository;
    private final SystemAnnouncementRepository systemAnnouncementRepository;
    private final AssetSubscriptionRepository assetSubscriptionRepository;
    private final NotificationDispatchService notificationDispatchService;

    @Override
    @Transactional
    @Nonnull
    public Notification createNotification(@Nonnull Notification notification) {
        // 统一校验类型长度，避免超长值触发数据库约束异常（500）
        notification.setType(NotificationTypes.requireValid(notification.getType()));
        log.info("创建通知: type={}, title={}", notification.getType(), notification.getTitle());
        return notificationRepository.save(notification);
    }

    @Override
    public Optional<Notification> findById(@Nullable UUID id) {
        return id != null ? notificationRepository.findById(id) : Optional.empty();
    }

    @Override
    @Nonnull
    public PageResponse<Notification> findByRecipientId(@Nullable UUID userId, int page, int size, @Nullable Boolean isRead) {
        if (userId == null) {
            return PageResponse.<Notification>builder()
                    .content(List.of())
                    .totalElements(0L)
                    .totalPages(0)
                    .currentPage(page)
                    .pageSize(size)
                    .build();
        }

        Pageable pageable = PageRequest.of(Paging.page(page), Paging.size(size), Sort.by("createdAt").descending());
        
        Page<NotificationRecipient> recipientPage;
        if (isRead != null) {
            recipientPage = notificationRecipientRepository.findByUserIdAndIsRead(userId, isRead, pageable);
        } else {
            recipientPage = notificationRecipientRepository.findByUserId(userId, pageable);
        }
        
        List<Notification> notifications = new ArrayList<>();
        for (NotificationRecipient recipient : recipientPage.getContent()) {
            UUID notificationId = recipient.getNotificationId();
            if (notificationId != null) {
                notificationRepository.findById(notificationId)
                        .ifPresent(notifications::add);
            }
        }
        
        return PageResponse.<Notification>builder()
                .content(notifications)
                .totalElements(recipientPage.getTotalElements())
                .totalPages(recipientPage.getTotalPages())
                .currentPage(recipientPage.getNumber())
                .pageSize(recipientPage.getSize())
                .build();
    }

    @Override
    @Transactional
    public void markAsRead(@Nullable UUID notificationId, @Nullable UUID userId) {
        log.info("标记通知已读: notificationId={}, userId={}", notificationId, userId);

        if (notificationId == null || userId == null) {
            return;
        }

        notificationRecipientRepository.findByNotificationIdAndUserId(notificationId, userId)
                .ifPresent(recipient -> {
                    recipient.setIsRead(true);
                    recipient.setReadAt(LocalDateTime.now());
                    notificationRecipientRepository.save(recipient);
                });
    }

    @Override
    @Transactional
    public void markAllAsRead(@Nullable UUID userId) {
        log.info("标记所有通知已读: userId={}", userId);

        if (userId == null) {
            return;
        }

        List<NotificationRecipient> recipients = notificationRecipientRepository.findByUserIdAndIsRead(userId, false);
        LocalDateTime now = LocalDateTime.now();
        for (NotificationRecipient recipient : recipients) {
            recipient.setIsRead(true);
            recipient.setReadAt(now);
        }
        notificationRecipientRepository.saveAll(Objects.requireNonNull(recipients));
    }

    @Override
    public long countUnread(@Nullable UUID userId) {
        if (userId == null) {
            return 0;
        }
        return notificationRecipientRepository.countByUserIdAndIsRead(userId, false);
    }

    @Override
    @Transactional
    public void sendNotification(@Nullable UUID userId, @Nullable String type, @Nullable String title, @Nullable String content) {
        log.info("发送通知: userId={}, type={}, title={}", userId, type, title);

        // 先校验类型（超长 → 400），再判断接收人，保证调用方立即得到明确错误
        String notificationType = NotificationTypes.requireValid(type);

        if (userId == null) {
            return;
        }

        Notification notification = Notification.builder()
                .type(notificationType)
                .title(title)
                .content(content)
                .build();
        Notification savedNotification = notificationRepository.save(Objects.requireNonNull(notification));

        NotificationRecipient recipient = NotificationRecipient.builder()
                .notificationId(Objects.requireNonNull(savedNotification.getId()))
                .userId(userId)
                .isRead(false)
                .build();
        NotificationRecipient savedRecipient = notificationRecipientRepository.save(Objects.requireNonNull(recipient));

        // 外部通道（邮件/Webhook）按用户通知设置异步能力受限，此处同步投递并回写发送状态；
        // 通道未配置时自动降级为仅站内，不抛异常。
        dispatchSafely(savedNotification, List.of(savedRecipient));
    }

    @Override
    @Transactional
    public void sendBulkNotification(@Nullable List<UUID> userIds, @Nullable String type, @Nullable String title, @Nullable String content) {
        log.info("批量发送通知: userCount={}, type={}, title={}", userIds != null ? userIds.size() : 0, type, title);

        String notificationType = NotificationTypes.requireValid(type);

        if (userIds == null || userIds.isEmpty()) {
            return;
        }

        Notification notification = Notification.builder()
                .type(notificationType)
                .title(title)
                .content(content)
                .build();
        Notification savedNotification = notificationRepository.save(Objects.requireNonNull(notification));
        
        UUID savedNotificationId = Objects.requireNonNull(savedNotification.getId());
        List<NotificationRecipient> recipients = new ArrayList<>();
        for (UUID userId : userIds) {
            recipients.add(NotificationRecipient.builder()
                    .notificationId(savedNotificationId)
                    .userId(userId)
                    .isRead(false)
                    .build());
        }
        notificationRecipientRepository.saveAll(Objects.requireNonNull(recipients));

        dispatchSafely(savedNotification, recipients);
    }

    /**
     * 执行外部通道分发，任何异常都只记录日志，不影响站内信主流程
     */
    private void dispatchSafely(Notification notification, List<NotificationRecipient> recipients) {
        try {
            notificationDispatchService.dispatch(notification, recipients);
        } catch (RuntimeException e) {
            log.warn("外部通知通道分发失败，已降级为仅站内: notificationId={}, error={}",
                    notification.getId(), e.getMessage(), e);
        }
    }

    /**
     * 删除通知
     */
    @Override
    @Transactional
    public void deleteNotification(UUID notificationId, UUID userId) {
        log.info("删除通知: notificationId={}, userId={}", notificationId, userId);
        
        notificationRecipientRepository.findByNotificationIdAndUserId(notificationId, userId)
                .ifPresent(notificationRecipientRepository::delete);
    }

    /**
     * 发送漏洞通知
     */
    @Override
    @Transactional
    public void sendVulnerabilityNotification(UUID vulnerabilityId, String type, List<UUID> recipientIds) {
        log.info("发送漏洞通知: vulnerabilityId={}, type={}", vulnerabilityId, type);
        
        String title = "漏洞通知";
        String content = "漏洞ID: " + vulnerabilityId + " 有新的动态";
        
        if (recipientIds != null && !recipientIds.isEmpty()) {
            sendBulkNotification(recipientIds, type, title, content);
        }
    }

    /**
     * 获取通知发送记录
     */
    @Override
    public List<?> getNotificationRecipients(@Nullable UUID notificationId) {
        log.debug("获取通知发送记录: notificationId={}", notificationId);
        return notificationId != null ? notificationRecipientRepository.findByNotificationId(notificationId) : List.of();
    }

    @Override
    public NotificationSetting getSetting(@Nullable UUID userId, @Nullable String type) {
        if (userId == null || type == null) {
            return null;
        }
        return notificationSettingRepository.findByUserIdAndType(userId, type).orElse(null);
    }

    @Override
    @Transactional
    public NotificationSetting updateSetting(@Nullable UUID userId, @Nullable String type, NotificationSetting setting) {
        log.info("更新通知设置: userId={}, type={}", userId, type);

        if (userId == null || type == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }

        NotificationSetting existingSetting = notificationSettingRepository
                .findByUserIdAndType(userId, type)
                .orElse(NotificationSetting.builder()
                        .userId(userId)
                        .type(type)
                        .build());

        if (setting.getEnabled() != null) {
            existingSetting.setEnabled(setting.getEnabled());
        }
        if (setting.getNotifyEmail() != null) {
            existingSetting.setNotifyEmail(setting.getNotifyEmail());
        }
        if (setting.getNotifySms() != null) {
            existingSetting.setNotifySms(setting.getNotifySms());
        }
        if (setting.getNotifyWebhook() != null) {
            existingSetting.setNotifyWebhook(setting.getNotifyWebhook());
        }

        return notificationSettingRepository.save(Objects.requireNonNull(existingSetting));
    }

    @Override
    public List<NotificationSetting> getSettings(@Nullable UUID userId) {
        return userId != null ? notificationSettingRepository.findByUserId(userId) : List.of();
    }

    @Override
    @Transactional
    public SystemAnnouncement createAnnouncement(SystemAnnouncement announcement) {
        log.info("创建系统公告: title={}", announcement.getTitle());
        // system_announcements.published_at 为 NOT NULL：未传时按「立即发布」补当前时间，
        // 否则创建公告会直接 500（此前只有显式传 publishedAt 的调用方能用）
        if (announcement.getPublishedAt() == null) {
            announcement.setPublishedAt(LocalDateTime.now());
        }
        if (announcement.getPriority() == null || announcement.getPriority().isBlank()) {
            announcement.setPriority("normal");
        }
        return systemAnnouncementRepository.save(announcement);
    }

    @Override
    public Optional<SystemAnnouncement> findAnnouncementById(@Nullable UUID id) {
        return id != null ? systemAnnouncementRepository.findById(id) : Optional.empty();
    }

    @Override
    public List<SystemAnnouncement> findActiveAnnouncements() {
        return systemAnnouncementRepository.findActive(LocalDateTime.now());
    }

    @Override
    public PageResponse<SystemAnnouncement> findAllAnnouncements(int page, int size) {
        Pageable pageable = PageRequest.of(Paging.page(page), Paging.size(size), Sort.by("publishedAt").descending());
        Page<SystemAnnouncement> announcementPage = systemAnnouncementRepository.findAll(pageable);

        return PageResponse.<SystemAnnouncement>builder()
                .content(announcementPage.getContent())
                .totalElements(announcementPage.getTotalElements())
                .totalPages(announcementPage.getTotalPages())
                .currentPage(announcementPage.getNumber())
                .pageSize(announcementPage.getSize())
                .build();
    }

    @Override
    @Transactional
    public SystemAnnouncement updateAnnouncement(@Nullable UUID id, SystemAnnouncement announcement) {
        log.info("更新系统公告: id={}", id);

        if (id == null) {
            throw new RuntimeException("公告不存在");
        }

        SystemAnnouncement existingAnnouncement = systemAnnouncementRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("公告不存在"));

        if (announcement.getTitle() != null) {
            existingAnnouncement.setTitle(announcement.getTitle());
        }
        if (announcement.getContent() != null) {
            existingAnnouncement.setContent(announcement.getContent());
        }
        if (announcement.getPriority() != null) {
            existingAnnouncement.setPriority(announcement.getPriority());
        }

        return systemAnnouncementRepository.save(Objects.requireNonNull(existingAnnouncement));
    }

    @Override
    @Transactional
    public void deleteAnnouncement(@Nullable UUID id) {
        log.info("删除系统公告: id={}", id);
        if (id != null) {
            systemAnnouncementRepository.deleteById(id);
        }
    }

    @Override
    @Transactional
    public void subscribeAsset(UUID userId, UUID assetId) {
        log.info("订阅资产: userId={}, assetId={}", userId, assetId);
        
        if (!assetSubscriptionRepository.existsByUserIdAndAssetId(userId, assetId)) {
            AssetSubscription subscription = AssetSubscription.builder()
                    .userId(userId)
                    .assetId(assetId)
                    .build();
            assetSubscriptionRepository.save(Objects.requireNonNull(subscription));
        }
    }

    @Override
    @Transactional
    public void unsubscribeAsset(UUID userId, UUID assetId) {
        log.info("取消订阅资产: userId={}, assetId={}", userId, assetId);
        assetSubscriptionRepository.deleteByUserIdAndAssetId(userId, assetId);
    }

    @Override
    public boolean isSubscribed(UUID userId, UUID assetId) {
        return assetSubscriptionRepository.existsByUserIdAndAssetId(userId, assetId);
    }

    @Override
    public List<UUID> getSubscribedAssetIds(UUID userId) {
        return assetSubscriptionRepository.findByUserId(userId).stream()
                .map(AssetSubscription::getAssetId)
                .toList();
    }

    @Override
    @Transactional
    public void notifySubscribers(UUID assetId, String type, String title, String content) {
        log.info("通知资产订阅者: assetId={}, type={}", assetId, type);
        
        List<UUID> subscriberIds = assetSubscriptionRepository.findByAssetId(assetId).stream()
                .map(AssetSubscription::getUserId)
                .toList();
        
        if (!subscriberIds.isEmpty()) {
            sendBulkNotification(subscriberIds, type, title, content);
        }
    }
}
