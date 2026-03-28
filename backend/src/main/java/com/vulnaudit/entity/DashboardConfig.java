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
 * 仪表盘配置实体类
 * 
 * 功能说明：
 * - 存储用户的仪表盘布局配置
 * - 支持自定义统计卡片
 */
@Slf4j
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "dashboard_configs")
public class DashboardConfig {

    @Id
    @GeneratedValue(generator = "UUID")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * 外键，关联users表
     */
    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    /**
     * 布局配置（JSON）
     */
    @Column(name = "layout", nullable = false, columnDefinition = "TEXT")
    private String layout;

    /**
     * 启用的卡片（JSON数组）
     */
    @Column(name = "cards", nullable = false, columnDefinition = "TEXT")
    private String cards;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        log.debug("创建仪表盘配置: userId={}", userId);
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
        log.debug("更新仪表盘配置: userId={}", userId);
    }
}
