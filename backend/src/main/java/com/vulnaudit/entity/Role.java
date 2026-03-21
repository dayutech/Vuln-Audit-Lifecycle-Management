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
 * 角色实体类
 * 
 * 功能说明：
 * - 映射数据库 roles 表
 * - 存储角色基本信息
 * - 支持系统内置角色和自定义角色
 * 
 * 内置角色：
 * - admin: 系统管理员
 * - project_leader: 项目负责人
 * - team_leader: 小组长
 * - vulnerability_engineer: 漏洞挖掘工程师（新成员默认角色）
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "roles")
public class Role {

    /**
     * 角色主键ID
     * 使用UUID自动生成，确保全局唯一性
     */
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * 角色名称
     * 用于显示，如"系统管理员"、"项目负责人"
     * 最大长度100字符
     */
    @Column(name = "name", length = 100, nullable = false)
    private String name;

    /**
     * 角色代码
     * 用于程序内部引用，如"admin"、"project_leader"
     * 最大长度50字符，全局唯一
     */
    @Column(name = "code", length = 50, unique = true, nullable = false)
    private String code;

    /**
     * 角色描述
     * 详细说明角色的职责和权限范围
     * 最大长度255字符
     */
    @Column(name = "description", length = 255)
    private String description;

    /**
     * 是否系统内置角色
     * 系统内置角色不可删除和修改核心属性
     */
    @Column(name = "is_system", nullable = false)
    @Builder.Default
    private Boolean isSystem = false;

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
        log.debug("创建角色实体: code={}, name={}", code, name);
    }

    /**
     * 实体更新前的回调方法
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        log.debug("更新角色实体: id={}, code={}", id, code);
    }

}
