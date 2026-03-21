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
 * 用户实体类
 * 
 * 功能说明：
 * - 映射数据库 users 表
 * - 存储用户基本信息和认证信息
 * - 支持用户状态管理（活跃/暂停/停用）
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "users")
public class User {

    /**
     * 用户主键ID
     * 使用UUID自动生成，确保全局唯一性
     */
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * 用户名
     * 用于系统登录，全局唯一
     * 最大长度50字符
     */
    @Column(name = "username", length = 50, unique = true, nullable = false)
    private String username;

    /**
     * 邮箱地址
     * 用于接收系统通知，全局唯一
     * 最大长度255字符
     */
    @Column(name = "email", length = 255, unique = true, nullable = false)
    private String email;

    /**
     * 手机号
     * 用于短信通知，可选字段
     * 最大长度20字符
     */
    @Column(name = "phone", length = 20)
    private String phone;

    /**
     * 密码哈希值
     * 使用BCrypt算法加密存储
     * 最大长度255字符
     */
    @Column(name = "password_hash", length = 255, nullable = false)
    private String passwordHash;

    /**
     * 真实姓名
     * 用于显示和业务沟通
     * 最大长度100字符
     */
    @Column(name = "full_name", length = 100, nullable = false)
    private String fullName;

    /**
     * 头像URL
     * 用户头像图片地址，可选字段
     * 最大长度500字符
     */
    @Column(name = "avatar", length = 500)
    private String avatar;

    /**
     * 职位
     * 用户在组织中的职位，可选字段
     * 最大长度100字符
     */
    @Column(name = "position", length = 100)
    private String position;

    /**
     * 用户状态
     * 可选值：active(活跃)/suspended(暂停)/deactivated(停用)
     * 默认值为 active
     */
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "active";

    /**
     * 创建时间
     * 记录用户账号创建的时间
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 更新时间
     * 记录用户信息最后更新的时间
     */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 最后登录时间
     * 记录用户最后一次成功登录的时间，可选字段
     */
    @Column(name = "last_login")
    private LocalDateTime lastLogin;

    /**
     * 实体持久化前的回调方法
     * 自动设置创建时间和更新时间
     */
    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        log.debug("创建用户实体: username={}, email={}", username, email);
    }

    /**
     * 实体更新前的回调方法
     * 自动更新更新时间
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        log.debug("更新用户实体: id={}, username={}", id, username);
    }

    /**
     * 检查用户是否处于活跃状态
     * 
     * @return 如果用户状态为active返回true，否则返回false
     */
    public boolean isActive() {
        return "active".equals(this.status);
    }

    /**
     * 检查用户是否被暂停
     * 
     * @return 如果用户状态为suspended返回true，否则返回false
     */
    public boolean isSuspended() {
        return "suspended".equals(this.status);
    }

    /**
     * 检查用户是否被停用
     * 
     * @return 如果用户状态为deactivated返回true，否则返回false
     */
    public boolean isDeactivated() {
        return "deactivated".equals(this.status);
    }

}
