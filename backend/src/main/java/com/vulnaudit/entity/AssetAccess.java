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
 * 资产访问申请实体类
 * 
 * 功能说明：
 * - 映射数据库 asset_access 表
 * - 存储资产访问申请信息
 * - 使用独立字段管理访问权限（不使用JSON）
 * 
 * 注意：
 * - 使用 can_view_documents、can_view_environments、can_view_files、allowed_file_types 字段
 * - 不使用 access_scope JSON 字段
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "asset_access")
public class AssetAccess {

    /**
     * 访问申请主键ID
     */
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * 资产ID
     * 外键，关联 assets 表
     */
    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    /**
     * 申请人ID
     * 外键，关联 users 表
     */
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /**
     * 申请状态
     * 可选值：pending(待审核)/approved(已批准)/rejected(已拒绝)
     * 默认值为 pending
     */
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "pending";

    /**
     * 是否可查看文档
     */
    @Column(name = "can_view_documents", nullable = false)
    @Builder.Default
    private Boolean canViewDocuments = false;

    /**
     * 是否可查看环境配置
     */
    @Column(name = "can_view_environments", nullable = false)
    @Builder.Default
    private Boolean canViewEnvironments = false;

    /**
     * 是否可查看文件
     */
    @Column(name = "can_view_files", nullable = false)
    @Builder.Default
    private Boolean canViewFiles = false;

    /**
     * 允许访问的文件类型
     * 多个类型用逗号分隔，如 "iso,vmdk,zip"
     * 最大长度500字符
     */
    @Column(name = "allowed_file_types", length = 500)
    private String allowedFileTypes;

    /**
     * 申请说明
     */
    @Column(name = "request_note", columnDefinition = "TEXT")
    private String requestNote;

    /**
     * 审批人ID
     * 外键，关联 users 表
     */
    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    /**
     * 审批时间
     */
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    /**
     * 审批备注
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
        log.debug("创建资产访问申请: assetId={}, userId={}", assetId, userId);
    }

    /**
     * 实体更新前的回调方法
     */
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
        log.debug("更新资产访问申请: id={}, status={}", id, status);
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

}
