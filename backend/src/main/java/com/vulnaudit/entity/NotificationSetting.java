package com.vulnaudit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.UUID;

/**
 * 通知设置实体类
 * 
 * 功能说明：
 * - 记录用户的通知偏好设置
 * - 支持邮件、短信、Webhook等多种通知渠道
 */
@Slf4j
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "notification_settings")
public class NotificationSetting {

    @Id
    @GeneratedValue(generator = "UUID")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * 外键，关联users表
     */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * 通知类型
     */
    @Column(name = "type", nullable = false, length = 50)
    private String type;

    /**
     * 是否启用
     */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /**
     * 邮件通知
     */
    @Column(name = "notify_email", nullable = false)
    private Boolean notifyEmail;

    /**
     * 短信通知
     */
    @Column(name = "notify_sms", nullable = false)
    private Boolean notifySms;

    /**
     * Webhook推送
     */
    @Column(name = "notify_webhook", nullable = false)
    private Boolean notifyWebhook;

    @PrePersist
    protected void onCreate() {
        if (enabled == null) {
            enabled = true;
        }
        if (notifyEmail == null) {
            notifyEmail = false;
        }
        if (notifySms == null) {
            notifySms = false;
        }
        if (notifyWebhook == null) {
            notifyWebhook = false;
        }
        log.debug("创建通知设置: userId={}, type={}", userId, type);
    }
}
