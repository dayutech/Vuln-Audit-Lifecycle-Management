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
 * 用户权限关联实体类
 * 
 * 功能说明：
 * - 映射数据库 user_permissions 表
 * - 建立用户与权限的直接关联（绕过角色）
 * - 支持临时权限和过期时间
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "user_permissions")
public class UserPermission {

    /**
     * 关联主键ID
     * 使用UUID自动生成
     */
    @Id
    @GeneratedValue(generator = "UUID")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * 用户ID
     * 外键，关联 users 表
     */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * 权限ID
     * 外键，关联 permissions 表
     */
    @Column(name = "permission_id", nullable = false)
    private UUID permissionId;

    /**
     * 授权者ID
     * 外键，关联 users 表
     * 记录是谁授予了这个权限
     */
    @Column(name = "granted_by", nullable = false)
    private UUID grantedBy;

    /**
     * 授权时间
     */
    @Column(name = "granted_at", nullable = false, updatable = false)
    private LocalDateTime grantedAt;

    /**
     * 过期时间
     * 可选字段，用于临时权限
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    /**
     * 实体持久化前的回调方法
     */
    @PrePersist
    protected void onCreate() {
        this.grantedAt = LocalDateTime.now();
        log.debug("创建用户权限关联: userId={}, permissionId={}, grantedBy={}", userId, permissionId, grantedBy);
    }

    /**
     * 检查权限是否已过期
     * 
     * @return 如果权限已过期返回true，否则返回false
     */
    public boolean isExpired() {
        if (expiresAt == null) {
            return false;
        }
        return LocalDateTime.now().isAfter(expiresAt);
    }

}
