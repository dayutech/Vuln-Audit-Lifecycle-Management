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
 * 资产历史实体类
 * 
 * 功能说明：
 * - 映射数据库 asset_history 表
 * - 记录资产字段变更历史
 * - 支持审计追溯
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "asset_history")
public class AssetHistory {

    /**
     * 历史记录主键ID
     */
    @Id
    @GeneratedValue(generator = "UUID")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * 资产ID
     * 外键，关联 assets 表
     */
    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    /**
     * 变更字段名
     * 最大长度50字符
     */
    @Column(name = "field_name", length = 50, nullable = false)
    private String fieldName;

    /**
     * 变更前值
     */
    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    /**
     * 变更后值
     */
    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    /**
     * 变更人ID
     * 外键，关联 users 表
     */
    @Column(name = "changed_by", nullable = false)
    private UUID changedBy;

    /**
     * 变更时间
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 实体持久化前的回调方法
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        log.debug("创建资产历史: assetId={}, fieldName={}", assetId, fieldName);
    }

}
