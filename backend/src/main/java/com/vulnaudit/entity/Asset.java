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
 * 资产实体类
 * 
 * 功能说明：
 * - 映射数据库 assets 表
 * - 存储资产基本信息
 * - 支持资产状态管理
 * 
 * 注意：资产表不包含 vendor_notified、vendor_fixed、fixed_at 字段
 * 这些字段在漏洞表中管理
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "assets")
public class Asset {

    /**
     * 资产主键ID
     * 使用UUID自动生成，确保全局唯一性
     */
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * 资产名称
     * 最大长度200字符
     */
    @Column(name = "name", length = 200, nullable = false)
    private String name;

    /**
     * 资产编号
     * 唯一标识，最大长度50字符
     */
    @Column(name = "code", length = 50, unique = true, nullable = false)
    private String code;

    /**
     * 所属团队ID
     * 外键，关联 teams 表
     */
    @Column(name = "team_id", nullable = true)
    private UUID teamId;

    /**
     * 目标类型
     * 如：Web应用、系统、服务、IoT设备等
     * 最大长度100字符
     */
    @Column(name = "target_type", length = 100, nullable = false)
    private String targetType;

    /**
     * 目标来源
     * 可选值：客户委托、自主发现
     * 最大长度20字符
     */
    @Column(name = "target_source", length = 20, nullable = false)
    private String targetSource;

    /**
     * 资产描述
     * 详细说明资产的用途和特点
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * 优先级
     * 可选值：P0(紧急)、P1(高)、P2(中)、P3(低)
     * 默认值为 P2
     */
    @Column(name = "priority", length = 2, nullable = false)
    @Builder.Default
    private String priority = "P2";

    /**
     * 资产状态
     * 可选值：active(活跃)、under_test(测试中)、suspended(暂停)、archived(归档)
     * 默认值为 active
     */
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "active";

    /**
     * 资产负责人ID
     * 外键，关联 users 表
     */
    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

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
        log.debug("创建资产实体: code={}, name={}", code, name);
    }

    /**
     * 实体更新前的回调方法
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        log.debug("更新资产实体: id={}, code={}", id, code);
    }

    /**
     * 检查资产是否活跃
     */
    public boolean isActive() {
        return "active".equals(this.status);
    }

    /**
     * 检查资产是否在测试中
     */
    public boolean isUnderTest() {
        return "under_test".equals(this.status);
    }

    /**
     * 检查资产是否暂停
     */
    public boolean isSuspended() {
        return "suspended".equals(this.status);
    }

    /**
     * 检查资产是否归档
     */
    public boolean isArchived() {
        return "archived".equals(this.status);
    }

}
