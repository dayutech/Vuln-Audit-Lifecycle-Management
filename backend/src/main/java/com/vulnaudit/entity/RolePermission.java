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
 * 角色权限关联实体类
 * 
 * 功能说明：
 * - 映射数据库 role_permissions 表
 * - 建立角色与权限的多对多关系
 * - 支持角色拥有多个权限
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "role_permissions")
public class RolePermission {

    /**
     * 关联主键ID
     * 使用UUID自动生成
     */
    @Id
    @GeneratedValue(generator = "UUID")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * 角色ID
     * 外键，关联 roles 表
     */
    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    /**
     * 权限ID
     * 外键，关联 permissions 表
     */
    @Column(name = "permission_id", nullable = false)
    private UUID permissionId;

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
        log.debug("创建角色权限关联: roleId={}, permissionId={}", roleId, permissionId);
    }

}
