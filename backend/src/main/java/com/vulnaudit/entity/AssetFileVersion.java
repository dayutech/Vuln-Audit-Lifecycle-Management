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
 * 资产文件版本实体类
 * 
 * 功能说明：
 * - 映射数据库 asset_file_versions 表
 * - 存储文件版本历史信息
 * - 支持文件版本回溯
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "asset_file_versions")
public class AssetFileVersion {

    /**
     * 版本主键ID
     */
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * 文件ID
     * 外键，关联 asset_files 表
     */
    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    /**
     * 版本号
     */
    @Column(name = "version", nullable = false)
    private Integer version;

    /**
     * 文件存储路径
     * 最大长度500字符
     */
    @Column(name = "file_path", length = 500, nullable = false)
    private String filePath;

    /**
     * 文件大小（字节）
     */
    @Column(name = "file_size")
    private Long fileSize;

    /**
     * SHA256校验值
     * 最大长度100字符
     */
    @Column(name = "checksum", length = 100)
    private String checksum;

    /**
     * 版本变更说明
     */
    @Column(name = "changelog", columnDefinition = "TEXT")
    private String changelog;

    /**
     * 上传人ID
     * 外键，关联 users 表
     */
    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 实体持久化前的回调方法
     */
    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        log.debug("创建文件版本: fileId={}, version={}", fileId, version);
    }

}
