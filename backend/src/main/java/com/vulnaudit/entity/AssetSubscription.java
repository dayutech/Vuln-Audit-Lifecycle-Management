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
 * 资产订阅实体类
 * 
 * 功能说明：
 * - 记录用户订阅的资产动态
 * - 支持资产变更通知
 */
@Slf4j
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "asset_subscriptions")
public class AssetSubscription {

    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * 外键，关联users表
     */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * 外键，关联assets表
     */
    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    /**
     * 订阅时间
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        log.debug("创建资产订阅: userId={}, assetId={}", userId, assetId);
    }
}
