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
 * 统计缓存实体类
 * 
 * 功能说明：
 * - 缓存统计数据
 * - 提高仪表盘查询性能
 */
@Slf4j
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "stat_cache")
public class StatCache {

    @Id
    @GeneratedValue(generator = "UUID")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * 统计类型
     */
    @Column(name = "stat_type", nullable = false, length = 50)
    private String statType;

    /**
     * 统计维度（如日期、资产ID等）
     */
    @Column(name = "stat_key", nullable = false, length = 100)
    private String statKey;

    /**
     * 统计值（JSON）
     */
    @Column(name = "stat_value", nullable = false, columnDefinition = "TEXT")
    private String statValue;

    /**
     * 计算时间
     */
    @Column(name = "calculated_at", nullable = false)
    private LocalDateTime calculatedAt;

    @PrePersist
    protected void onCreate() {
        if (calculatedAt == null) {
            calculatedAt = LocalDateTime.now();
        }
        log.debug("创建统计缓存: statType={}, statKey={}", statType, statKey);
    }
}
