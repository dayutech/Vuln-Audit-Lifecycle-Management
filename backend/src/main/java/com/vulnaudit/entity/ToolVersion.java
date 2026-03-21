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
 * 工具版本实体类
 * 
 * 功能说明：
 * - 记录工具的不同版本信息
 * - 支持工具版本管理和历史追溯
 * - 存储版本变更说明和文件路径
 */
@Slf4j
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tool_versions")
public class ToolVersion {

    /**
     * 主键ID，自动生成UUID
     */
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * 外键，关联tools表
     */
    @Column(name = "tool_id", nullable = false)
    private UUID toolId;

    /**
     * 版本号
     */
    @Column(name = "version", nullable = false, length = 50)
    private String version;

    /**
     * 文件存储路径
     */
    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    /**
     * 变更说明
     */
    @Column(name = "changelog", length = 500)
    private String changelog;

    /**
     * 外键，关联users表，上传者
     */
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 持久化前的生命周期回调
     * 自动设置创建时间
     */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        log.debug("创建工具版本记录: toolId={}, version={}", toolId, version);
    }
}
