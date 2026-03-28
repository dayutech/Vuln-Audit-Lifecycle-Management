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
 * 团队实体类
 * 
 * 功能说明：
 * - 映射数据库 teams 表
 * - 存储团队基本信息
 * - 支持团队层级结构（通过 parent_id）
 * - 记录团队负责人和创建者
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "teams")
public class Team {

    /**
     * 团队主键ID
     * 使用UUID自动生成，确保全局唯一性
     */
    @Id
    @GeneratedValue(generator = "UUID")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * 团队名称
     * 最大长度100字符
     */
    @Column(name = "name", length = 100, nullable = false)
    private String name;

    /**
     * 上级团队ID
     * 外键，关联 teams 表
     * 用于构建团队层级结构，顶级团队为 null
     */
    @Column(name = "parent_id")
    private UUID parentId;

    /**
     * 团队描述
     * 详细说明团队的职责和范围
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * 团队负责人ID
     * 外键，关联 users 表
     */
    @Column(name = "leader_id", nullable = false)
    private UUID leaderId;

    /**
     * 创建者ID
     * 外键，关联 users 表
     * 记录是谁创建了这个团队
     */
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

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
        log.debug("创建团队实体: name={}, leaderId={}", name, leaderId);
    }

    /**
     * 实体更新前的回调方法
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        log.debug("更新团队实体: id={}, name={}", id, name);
    }

    /**
     * 检查是否为顶级团队
     * 
     * @return 如果是顶级团队返回true，否则返回false
     */
    public boolean isTopLevel() {
        return this.parentId == null;
    }

}
