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
 * 团队成员关联实体类
 * 
 * 功能说明：
 * - 映射数据库 team_members 表
 * - 建立用户与团队的多对多关系
 * - 记录成员在团队中的角色和状态
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "team_members")
public class TeamMember {

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
     * 团队ID
     * 外键，关联 teams 表
     */
    @Column(name = "team_id", nullable = false)
    private UUID teamId;

    /**
     * 是否为团队负责人
     * 一个团队可以有多个负责人
     */
    @Column(name = "is_leader", nullable = false)
    @Builder.Default
    private Boolean isLeader = false;

    /**
     * 成员状态
     * 可选值：available(空闲)/busy(忙碌)/offline(离线)
     */
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "available";

    /**
     * 加入时间
     */
    @Column(name = "joined_at", nullable = false, updatable = false)
    private LocalDateTime joinedAt;

    /**
     * 实体持久化前的回调方法
     */
    @PrePersist
    protected void onCreate() {
        this.joinedAt = LocalDateTime.now();
        log.debug("创建团队成员关联: userId={}, teamId={}, isLeader={}", userId, teamId, isLeader);
    }

    /**
     * 检查成员是否空闲
     * 
     * @return 如果成员状态为available返回true，否则返回false
     */
    public boolean isAvailable() {
        return "available".equals(this.status);
    }

    /**
     * 检查成员是否忙碌
     * 
     * @return 如果成员状态为busy返回true，否则返回false
     */
    public boolean isBusy() {
        return "busy".equals(this.status);
    }

    /**
     * 检查成员是否离线
     * 
     * @return 如果成员状态为offline返回true，否则返回false
     */
    public boolean isOffline() {
        return "offline".equals(this.status);
    }

}
