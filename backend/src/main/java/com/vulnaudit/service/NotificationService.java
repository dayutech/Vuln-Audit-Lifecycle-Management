package com.vulnaudit.service;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.Notification;
import com.vulnaudit.entity.NotificationSetting;
import com.vulnaudit.entity.SystemAnnouncement;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 通知服务接口
 */
public interface NotificationService {

    // 通知管理
    Notification createNotification(Notification notification);

    Optional<Notification> findById(UUID id);

    PageResponse<Notification> findByRecipientId(UUID userId, int page, int size, Boolean isRead);

    void markAsRead(UUID notificationId, UUID userId);

    void markAllAsRead(UUID userId);

    long countUnread(UUID userId);

    void sendNotification(UUID userId, String type, String title, String content);

    void sendBulkNotification(List<UUID> userIds, String type, String title, String content);

    /**
     * 删除通知
     * 
     * @param notificationId 通知ID
     * @param userId 用户ID
     */
    void deleteNotification(UUID notificationId, UUID userId);

    /**
     * 发送漏洞通知
     * 
     * @param vulnerabilityId 漏洞ID
     * @param type 通知类型
     * @param recipientIds 接收者ID列表（可选）
     */
    void sendVulnerabilityNotification(UUID vulnerabilityId, String type, List<UUID> recipientIds);

    /**
     * 获取通知发送记录
     * 
     * @param notificationId 通知ID
     * @return 接收者列表
     */
    List<?> getNotificationRecipients(UUID notificationId);

    // 通知设置
    NotificationSetting getSetting(UUID userId, String type);

    NotificationSetting updateSetting(UUID userId, String type, NotificationSetting setting);

    List<NotificationSetting> getSettings(UUID userId);

    // 系统公告
    SystemAnnouncement createAnnouncement(SystemAnnouncement announcement);

    Optional<SystemAnnouncement> findAnnouncementById(UUID id);

    List<SystemAnnouncement> findActiveAnnouncements();

    PageResponse<SystemAnnouncement> findAllAnnouncements(int page, int size);

    SystemAnnouncement updateAnnouncement(UUID id, SystemAnnouncement announcement);

    void deleteAnnouncement(UUID id);

    // 资产订阅
    void subscribeAsset(UUID userId, UUID assetId);

    void unsubscribeAsset(UUID userId, UUID assetId);

    boolean isSubscribed(UUID userId, UUID assetId);

    List<UUID> getSubscribedAssetIds(UUID userId);

    void notifySubscribers(UUID assetId, String type, String title, String content);
}
