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
@Table(name = "tool_versions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_tool_versions_tool_version_no", columnNames = {"tool_id", "version_no"})
})
public class ToolVersion {

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
     * 版本号（人类可读标签，如 v1 / 1.2.0）
     */
    @Column(name = "version", nullable = false, length = 50)
    private String version;

    /**
     * 版本序号（同工具下自增，1 起），配合 (tool_id, version_no) 唯一约束防并发重复
     */
    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    /**
     * 原始文件名（仅元数据版本为空）
     */
    @Column(name = "file_name", length = 255)
    private String fileName;

    /**
     * 文件存储路径（仅元数据版本为空串）
     */
    @Column(name = "file_path", nullable = false, length = 500)
    private String filePath;

    /**
     * 文件大小（字节）
     */
    @Column(name = "file_size")
    private Long fileSize;

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
     * 是否包含文件（仅元数据版本返回 false）
     *
     * <p>派生属性，不落库。
     */
    @jakarta.persistence.Transient
    public boolean isHasFile() {
        return filePath != null && !filePath.isBlank();
    }

    /**
     * 持久化前的生命周期回调
     * 自动设置创建时间
     */
    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (filePath == null) {
            filePath = "";
        }
        log.debug("创建工具版本记录: toolId={}, version={}, versionNo={}", toolId, version, versionNo);
    }
}
