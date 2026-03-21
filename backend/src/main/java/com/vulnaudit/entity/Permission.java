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
 * 权限实体类
 * 
 * 功能说明：
 * - 映射数据库 permissions 表
 * - 存储API级别权限信息
 * - 支持细粒度的权限控制
 * 
 * 权限代码格式：资源:操作，如 asset:read、vulnerability:write
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "permissions")
public class Permission {

    /**
     * 权限主键ID
     * 使用UUID自动生成，确保全局唯一性
     */
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * 权限名称
     * 用于显示，如"资产查看"、"漏洞编辑"
     * 最大长度100字符
     */
    @Column(name = "name", length = 100, nullable = false)
    private String name;

    /**
     * 权限代码
     * 用于程序内部引用，格式：资源:操作
     * 如 asset:read、vulnerability:write
     * 最大长度50字符，全局唯一
     */
    @Column(name = "code", length = 50, unique = true, nullable = false)
    private String code;

    /**
     * API路径
     * 关联的API路径，如 /api/v1/assets
     * 最大长度200字符
     */
    @Column(name = "api_path", length = 200, nullable = false)
    private String apiPath;

    /**
     * HTTP方法
     * 支持的方法：GET、POST、PUT、DELETE、PATCH
     * 最大长度10字符
     */
    @Column(name = "http_method", length = 10, nullable = false)
    private String httpMethod;

    /**
     * 权限描述
     * 详细说明权限的作用
     * 最大长度255字符
     */
    @Column(name = "description", length = 255)
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
        log.debug("创建权限实体: code={}, name={}", code, name);
    }

}
