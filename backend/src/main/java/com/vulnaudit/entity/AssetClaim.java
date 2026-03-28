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
 * 资产认领实体类
 * 
 * 功能说明：
 * - 映射数据库 asset_claims 表
 * - 存储资产认领申请信息
 * - 支持认领审批流程
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "asset_claims")
public class AssetClaim {

    /**
     * 认领主键ID
     */
    @Id
    @GeneratedValue(generator = "UUID")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * 资产ID
     * 外键，关联 assets 表
     */
    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    /**
     * 认领人ID（漏洞挖掘工程师）
     * 外键，关联 users 表
     */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * 认领状态
     * 可选值：pending(待审核)/approved(已批准)/rejected(已拒绝)/completed(已完成)/abandoned(已放弃)
     * 默认值为 pending
     */
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "pending";

    /**
     * 认领时间
     * 审核通过后设置
     */
    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    /**
     * 完成时间
     * 认领完成后设置
     */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    /**
     * 认领备注
     */
    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    /**
     * 审核人ID
     * 外键，关联 users 表
     */
    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    /**
     * 审核时间
     */
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /**
     * 审核备注
     */
    @Column(name = "review_note", columnDefinition = "TEXT")
    private String reviewNote;

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
        log.debug("创建资产认领: assetId={}, userId={}", assetId, userId);
    }

    /**
     * 实体更新前的回调方法
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        log.debug("更新资产认领: id={}, status={}", id, status);
    }

    /**
     * 检查是否待审核
     */
    public boolean isPending() {
        return "pending".equals(this.status);
    }

    /**
     * 检查是否已批准
     */
    public boolean isApproved() {
        return "approved".equals(this.status);
    }

    /**
     * 检查是否已拒绝
     */
    public boolean isRejected() {
        return "rejected".equals(this.status);
    }

    /**
     * 检查是否已完成
     */
    public boolean isCompleted() {
        return "completed".equals(this.status);
    }

    /**
     * 检查是否已放弃
     */
    public boolean isAbandoned() {
        return "abandoned".equals(this.status);
    }

}
