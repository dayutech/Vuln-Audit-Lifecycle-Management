package com.vulnaudit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 通知接收者实体类
 * 
 * 功能说明：
 * - 映射数据库 notification_recipients 表
 * - 存储通知与用户的关联关系
 * - 记录每个用户的阅读状态
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "notification_recipients")
public class NotificationRecipient {

    @Id
    @GeneratedValue(generator = "UUID")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "notification_id", nullable = false)
    private UUID notificationId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * 接收人类型：leader/assignee/specific_user
     */
    @Column(name = "recipient_type", length = 20)
    @Builder.Default
    private String recipientType = "specific_user";

    /**
     * 实际投递渠道：in_app / email / webhook（多通道以逗号分隔）
     */
    @Column(name = "channel", length = 50)
    @Builder.Default
    private String channel = "in_app";

    /**
     * 发送状态：pending / sent / failed / partial
     */
    @Column(name = "send_status", length = 20)
    @Builder.Default
    private String sendStatus = "pending";

    /**
     * 外部通道发送完成时间
     */
    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    /**
     * 发送失败原因
     */
    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (recipientType == null) {
            recipientType = "specific_user";
        }
        if (channel == null) {
            channel = "in_app";
        }
        if (sendStatus == null) {
            sendStatus = "pending";
        }
        this.createdAt = LocalDateTime.now();
        log.debug("创建通知接收者: notificationId={}, userId={}", notificationId, userId);
    }

}
