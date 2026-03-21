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
 * 用户角色关联实体类
 * 
 * 功能说明：
 * - 映射数据库 user_roles 表
 * - 建立用户与角色的多对多关系
 * - 记录角色分配者和分配时间
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "user_roles")
public class UserRole {

    /**
     * 关联主键ID
     * 使用UUID自动生成
     */
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * 用户ID
     * 外键，关联 users 表
     */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * 角色ID
     * 外键，关联 roles 表
     */
    @Column(name = "role_id", nullable = false)
    private UUID roleId;

    /**
     * 分配者ID
     * 外键，关联 users 表
     * 记录是谁分配了这个角色
     */
    @Column(name = "assigned_by", nullable = false)
    private UUID assignedBy;

    /**
     * 分配时间
     */
    @Column(name = "assigned_at", nullable = false, updatable = false)
    private LocalDateTime assignedAt;

    /**
     * 实体持久化前的回调方法
     */
    @PrePersist
    protected void onCreate() {
        this.assignedAt = LocalDateTime.now();
        log.debug("创建用户角色关联: userId={}, roleId={}, assignedBy={}", userId, roleId, assignedBy);
    }

}
