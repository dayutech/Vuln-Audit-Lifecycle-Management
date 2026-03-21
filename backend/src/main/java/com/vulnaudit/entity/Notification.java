package com.vulnaudit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 通知实体类
 * 
 * 功能说明：
 * - 映射数据库 notifications 表
 * - 存储通知消息内容
 * - 与 NotificationRecipient 配合实现一对多关系
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "notifications")
public class Notification {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "type", length = 20, nullable = false)
    private String type;

    @Column(name = "priority", length = 10, nullable = false)
    @Builder.Default
    private String priority = "normal";

    @Column(name = "sender_id")
    private UUID senderId;

    @Column(name = "related_type", length = 50)
    private String relatedType;

    @Column(name = "related_id")
    private UUID relatedId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        log.debug("创建通知: title={}", title);
    }

}
