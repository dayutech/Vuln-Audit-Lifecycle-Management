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
 * 资产分组实体类
 * 
 * 功能说明：
 * - 映射数据库 asset_groups 表
 * - 存储资产分组信息
 * - 支持按客户、项目、自建等方式分组
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "asset_groups")
public class AssetGroup {

    /**
     * 分组主键ID
     */
    @Id
    @GeneratedValue(generator = "UUID")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * 分组名称
     * 最大长度100字符
     */
    @Column(name = "name", length = 100, nullable = false)
    private String name;

    /**
     * 分组类型
     * 可选值：客户、项目、自建
     * 最大长度20字符
     */
    @Column(name = "type", length = 20, nullable = false)
    private String type;

    /**
     * 分组描述
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

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
        log.debug("创建资产分组实体: name={}, type={}", name, type);
    }

}
