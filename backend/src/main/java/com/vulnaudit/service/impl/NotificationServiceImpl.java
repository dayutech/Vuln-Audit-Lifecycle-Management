package com.vulnaudit.service.impl;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.Notification;
import com.vulnaudit.entity.NotificationRecipient;
import com.vulnaudit.entity.NotificationSetting;
import com.vulnaudit.entity.SystemAnnouncement;
import com.vulnaudit.entity.AssetSubscription;
import com.vulnaudit.repository.NotificationRecipientRepository;
import com.vulnaudit.repository.NotificationRepository;
import com.vulnaudit.repository.NotificationSettingRepository;
import com.vulnaudit.repository.SystemAnnouncementRepository;
import com.vulnaudit.repository.AssetSubscriptionRepository;
import com.vulnaudit.service.NotificationService;
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
import java.util.Optional;
import java.util.UUID;

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

    @Override
    @Transactional
    public Notification createNotification(Notification notification) {
        log.info("创建通知: type={}, title={}", notification.getType(), notification.getTitle());
        return notificationRepository.save(notification);
    }

    @Override
    public Optional<Notification> findById(UUID id) {
        return notificationRepository.findById(id);
    }

    @Override
    public PageResponse<Notification> findByRecipientId(UUID userId, int page, int size, Boolean isRead) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        
        Page<NotificationRecipient> recipientPage;
        if (isRead != null) {
            recipientPage = notificationRecipientRepository.findByUserIdAndIsRead(userId, isRead, pageable);
        } else {
            recipientPage = notificationRecipientRepository.findByUserId(userId, pageable);
        }
        
        List<Notification> notifications = new ArrayList<>();
        for (NotificationRecipient recipient : recipientPage.getContent()) {
            notificationRepository.findById(recipient.getNotificationId())
                    .ifPresent(notifications::add);
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
    public void markAsRead(UUID notificationId, UUID userId) {
        log.info("标记通知已读: notificationId={}, userId={}", notificationId, userId);
        
        notificationRecipientRepository.findByNotificationIdAndUserId(notificationId, userId)
                .ifPresent(recipient -> {
                    recipient.setIsRead(true);
                    recipient.setReadAt(LocalDateTime.now());
                    notificationRecipientRepository.save(recipient);
                });
    }

    @Override
    @Transactional
    public void markAllAsRead(UUID userId) {
        log.info("标记所有通知已读: userId={}", userId);
        
        List<NotificationRecipient> recipients = notificationRecipientRepository.findByUserIdAndIsRead(userId, false);
        LocalDateTime now = LocalDateTime.now();
        for (NotificationRecipient recipient : recipients) {
            recipient.setIsRead(true);
            recipient.setReadAt(now);
        }
        notificationRecipientRepository.saveAll(recipients);
    }

    @Override
    public long countUnread(UUID userId) {
        return notificationRecipientRepository.countByUserIdAndIsRead(userId, false);
    }

    @Override
    @Transactional
    public void sendNotification(UUID userId, String type, String title, String content) {
        log.info("发送通知: userId={}, type={}, title={}", userId, type, title);
        
        Notification notification = Notification.builder()
                .type(type)
                .title(title)
                .content(content)
                .build();
        Notification savedNotification = notificationRepository.save(notification);
        
        NotificationRecipient recipient = NotificationRecipient.builder()
                .notificationId(savedNotification.getId())
                .userId(userId)
                .isRead(false)
                .build();
        notificationRecipientRepository.save(recipient);
    }

    @Override
    @Transactional
    public void sendBulkNotification(List<UUID> userIds, String type, String title, String content) {
        log.info("批量发送通知: userCount={}, type={}, title={}", userIds.size(), type, title);
        
        Notification notification = Notification.builder()
                .type(type)
                .title(title)
                .content(content)
                .build();
        Notification savedNotification = notificationRepository.save(notification);
        
        List<NotificationRecipient> recipients = new ArrayList<>();
        for (UUID userId : userIds) {
            recipients.add(NotificationRecipient.builder()
                    .notificationId(savedNotification.getId())
                    .userId(userId)
                    .isRead(false)
                    .build());
        }
        notificationRecipientRepository.saveAll(recipients);
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
    public List<?> getNotificationRecipients(UUID notificationId) {
        log.debug("获取通知发送记录: notificationId={}", notificationId);
        return notificationRecipientRepository.findByNotificationId(notificationId);
    }

    @Override
    public NotificationSetting getSetting(UUID userId, String type) {
        return notificationSettingRepository.findByUserIdAndType(userId, type).orElse(null);
    }

    @Override
    @Transactional
    public NotificationSetting updateSetting(UUID userId, String type, NotificationSetting setting) {
        log.info("更新通知设置: userId={}, type={}", userId, type);
        
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
        
        return notificationSettingRepository.save(existingSetting);
    }

    @Override
    public List<NotificationSetting> getSettings(UUID userId) {
        return notificationSettingRepository.findByUserId(userId);
    }

    @Override
    @Transactional
    public SystemAnnouncement createAnnouncement(SystemAnnouncement announcement) {
        log.info("创建系统公告: title={}", announcement.getTitle());
        return systemAnnouncementRepository.save(announcement);
    }

    @Override
    public Optional<SystemAnnouncement> findAnnouncementById(UUID id) {
        return systemAnnouncementRepository.findById(id);
    }

    @Override
    public List<SystemAnnouncement> findActiveAnnouncements() {
        return systemAnnouncementRepository.findActive(LocalDateTime.now());
    }

    @Override
    public PageResponse<SystemAnnouncement> findAllAnnouncements(int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("publishedAt").descending());
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
    public SystemAnnouncement updateAnnouncement(UUID id, SystemAnnouncement announcement) {
        log.info("更新系统公告: id={}", id);
        
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
        
        return systemAnnouncementRepository.save(existingAnnouncement);
    }

    @Override
    @Transactional
    public void deleteAnnouncement(UUID id) {
        log.info("删除系统公告: id={}", id);
        systemAnnouncementRepository.deleteById(id);
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
            assetSubscriptionRepository.save(subscription);
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
