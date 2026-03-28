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
 * 审批范围实体类
 * 
 * 功能说明：
 * - 定义审批者的审批权限范围
 * - 支持按团队、资产分类、资产等维度授权
 */
@Slf4j
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "approval_scopes")
public class ApprovalScope {

    @Id
    @GeneratedValue(generator = "UUID")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * 审批者ID
     */
    @Column(name = "approver_id", nullable = false)
    private UUID approverId;

    /**
     * 范围类型（team/asset_category/asset）
     */
    @Column(name = "scope_type", nullable = false, length = 20)
    private String scopeType;

    /**
     * 范围ID（团队ID/资产分类ID/资产ID）
     */
    @Column(name = "scope_id", nullable = false)
    private UUID scopeId;

    /**
     * 授权者ID
     */
    @Column(name = "granted_by", nullable = false)
    private UUID grantedBy;

    /**
     * 授权时间
     */
    @Column(name = "granted_at", nullable = false)
    private LocalDateTime grantedAt;

    /**
     * 过期时间（可选）
     */
    @Column(name = "expires_at")
    private LocalDateTime expiresAt;

    @PrePersist
    protected void onCreate() {
        if (grantedAt == null) {
            grantedAt = LocalDateTime.now();
        }
        log.debug("创建审批范围: approverId={}, scopeType={}, scopeId={}", approverId, scopeType, scopeId);
    }

    /**
     * 检查审批范围是否有效
     */
    public boolean isValid() {
        if (expiresAt == null) {
            return true;
        }
        return LocalDateTime.now().isBefore(expiresAt);
    }
}
