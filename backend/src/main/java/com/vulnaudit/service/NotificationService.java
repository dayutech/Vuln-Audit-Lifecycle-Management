package com.vulnaudit.service;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.Notification;
import com.vulnaudit.entity.NotificationSetting;
import com.vulnaudit.entity.SystemAnnouncement;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 通知服务接口
 */
public interface NotificationService {

    // 通知管理
    @Nonnull
    Notification createNotification(@Nonnull Notification notification);

    Optional<Notification> findById(@Nullable UUID id);

    @Nonnull
    PageResponse<Notification> findByRecipientId(@Nullable UUID userId, int page, int size, @Nullable Boolean isRead);

    void markAsRead(@Nullable UUID notificationId, @Nullable UUID userId);

    void markAllAsRead(@Nullable UUID userId);

    long countUnread(@Nullable UUID userId);

    void sendNotification(@Nullable UUID userId, @Nullable String type, @Nullable String title, @Nullable String content);

    void sendBulkNotification(@Nullable List<UUID> userIds, @Nullable String type, @Nullable String title, @Nullable String content);

    /**
     * 删除通知
     *
     * @param notificationId 通知ID
     * @param userId 用户ID
     */
    void deleteNotification(@Nullable UUID notificationId, @Nullable UUID userId);

    /**
     * 发送漏洞通知
     *
     * @param vulnerabilityId 漏洞ID
     * @param type 通知类型
     * @param recipientIds 接收者ID列表（可选）
     */
    void sendVulnerabilityNotification(@Nullable UUID vulnerabilityId, @Nullable String type, @Nullable List<UUID> recipientIds);

    /**
     * 获取通知发送记录
     *
     * @param notificationId 通知ID
     * @return 接收者列表
     */
    @Nonnull
    List<?> getNotificationRecipients(@Nullable UUID notificationId);

    // 通知设置
    @Nonnull
    NotificationSetting getSetting(@Nullable UUID userId, @Nullable String type);

    @Nonnull
    NotificationSetting updateSetting(@Nullable UUID userId, @Nullable String type, @Nonnull NotificationSetting setting);

    @Nonnull
    List<NotificationSetting> getSettings(@Nullable UUID userId);

    // 系统公告
    @Nonnull
    SystemAnnouncement createAnnouncement(@Nonnull SystemAnnouncement announcement);

    Optional<SystemAnnouncement> findAnnouncementById(@Nullable UUID id);

    @Nonnull
    List<SystemAnnouncement> findActiveAnnouncements();

    @Nonnull
    PageResponse<SystemAnnouncement> findAllAnnouncements(int page, int size);

    @Nonnull
    SystemAnnouncement updateAnnouncement(@Nullable UUID id, @Nonnull SystemAnnouncement announcement);

    void deleteAnnouncement(@Nullable UUID id);

    // 资产订阅
    void subscribeAsset(@Nullable UUID userId, @Nullable UUID assetId);

    void unsubscribeAsset(@Nullable UUID userId, @Nullable UUID assetId);

    boolean isSubscribed(@Nullable UUID userId, @Nullable UUID assetId);

    @Nonnull
    List<UUID> getSubscribedAssetIds(@Nullable UUID userId);

    void notifySubscribers(@Nullable UUID assetId, @Nullable String type, @Nullable String title, @Nullable String content);
}
