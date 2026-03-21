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
 * 系统公告实体类
 * 
 * 功能说明：
 * - 发布系统公告通知
 * - 支持按角色定向推送
 * - 支持优先级和过期时间
 */
@Slf4j
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "system_announcements")
public class SystemAnnouncement {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * 公告标题
     */
    @Column(name = "title", nullable = false, length = 200)
    private String title;

    /**
     * 公告内容
     */
    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    /**
     * 目标角色（JSON数组）
     */
    @Column(name = "target_roles", columnDefinition = "jsonb")
    private String targetRoles;

    /**
     * 优先级（normal/important/urgent）
     */
    @Column(name = "priority", nullable = false, length = 20)
    private String priority;

    /**
     * 发布时间
     */
    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;

    /**
     * 过期时间（可选）
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (priority == null) {
            priority = "normal";
        }
        log.debug("创建系统公告: title={}, priority={}", title, priority);
    }
}
