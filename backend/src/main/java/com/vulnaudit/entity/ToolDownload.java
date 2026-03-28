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
 * 工具下载记录实体类
 * 
 * 功能说明：
 * - 记录工具下载历史
 * - 支持下载统计功能
 * - 追踪用户下载行为
 */
@Slf4j
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tool_downloads")
public class ToolDownload {

    /**
     * 主键ID，自动生成UUID
     */
    @Id
    @GeneratedValue(generator = "UUID")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * 外键，关联tools表
     */
    @Column(name = "tool_id", nullable = false)
    private UUID toolId;

    /**
     * 外键，关联users表，下载者
     */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * 下载时间
     */
    @Column(name = "downloaded_at", nullable = false, updatable = false)
    private LocalDateTime downloadedAt;

    /**
     * 持久化前的生命周期回调
     * 自动设置下载时间
     */
    @PrePersist
    protected void onCreate() {
        if (downloadedAt == null) {
            downloadedAt = LocalDateTime.now();
        }
        log.debug("记录工具下载: toolId={}, userId={}", toolId, userId);
    }
}
