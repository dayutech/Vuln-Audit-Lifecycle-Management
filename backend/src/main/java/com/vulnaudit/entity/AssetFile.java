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
 * 资产文件实体类
 * 
 * 功能说明：
 * - 映射数据库 asset_files 表
 * - 存储资产相关文件信息
 * - 支持文件版本管理
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "asset_files")
public class AssetFile {

    /**
     * 文件主键ID
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

    @Column(name = "asset_version_id")
    private UUID assetVersionId;

    /**
     * 文件名
     * 最大长度500字符
     */
    @Column(name = "name", length = 500, nullable = false)
    private String name;

    /**
     * 文件分类
     * 可选值：系统镜像/磁盘文件/安装包/提取程序/配置文件/其他
     * 最大长度20字符
     */
    @Column(name = "category", length = 20, nullable = false)
    private String category;

    /**
     * 文件描述
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * 文件大小（字节）
     */
    @Column(name = "file_size")
    private Long fileSize;

    /**
     * 文件存储路径
     * 最大长度500字符
     */
    @Column(name = "file_path", length = 500, nullable = false)
    private String filePath;

    /**
     * SHA256校验值
     * 最大长度100字符
     */
    @Column(name = "checksum", length = 100)
    private String checksum;

    /**
     * 当前版本号
     * 默认值为1
     */
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;

    /**
     * 文件状态
     * 可选值：active(活跃)/archived(归档)
     * 默认值为 active
     */
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "active";

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
     * 更新时间
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 实体持久化前的回调方法
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        log.debug("创建资产文件: assetId={}, name={}", assetId, name);
    }

    /**
     * 实体更新前的回调方法
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        log.debug("更新资产文件: id={}, name={}", id, name);
    }

    /**
     * 检查文件是否活跃
     */
    public boolean isActive() {
        return "active".equals(this.status);
    }

    /**
     * 检查文件是否归档
     */
    public boolean isArchived() {
        return "archived".equals(this.status);
    }

}
