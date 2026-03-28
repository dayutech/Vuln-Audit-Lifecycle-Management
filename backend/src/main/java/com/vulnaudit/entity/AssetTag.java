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
 * 资产标签实体类
 * 
 * 功能说明：
 * - 映射数据库 asset_tags 表
 * - 存储资产标签信息
 * - 支持资产分类和检索
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "asset_tags")
public class AssetTag {

    /**
     * 标签主键ID
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
     * 标签名称
     * 最大长度50字符
     */
    @Column(name = "tag", length = 50, nullable = false)
    private String tag;

    /**
     * 创建人ID
     * 外键，关联 users 表
     */
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

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
        log.debug("创建资产标签: assetId={}, tag={}", assetId, tag);
    }

}
