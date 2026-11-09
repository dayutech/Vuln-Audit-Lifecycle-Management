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
 * 客户实体（t67）
 *
 * <p>漏洞审计业务的第一层归属：客户 → 项目 → 期次 → 资产挂靠。
 * 本批只落领域模型与迁移，不暴露 API。
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "customers",
        uniqueConstraints = @UniqueConstraint(name = "uk_customers_code", columnNames = "code"),
        indexes = {
                @Index(name = "idx_customers_status", columnList = "status"),
                @Index(name = "idx_customers_owner", columnList = "owner_id"),
                @Index(name = "idx_customers_deleted", columnList = "deleted_at")
        })
@org.hibernate.annotations.SQLRestriction("deleted_at IS NULL")
public class Customer {

    @Id
    @GeneratedValue(generator = "UUID")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * 软删时间（t140）：非空 = 已删除，所有默认查询都不返回；唯一键仍占用（code 不可复用）
     *
     * <p>用 {@link org.hibernate.annotations.SQLRestriction} 统一过滤，而不是逐个仓储方法加
     * {@code AndDeletedAtIsNull} —— 后者极易漏（列表/详情/summary/stats/下拉/导入查重都要加），
     * 而 SQLRestriction 对 JPQL、派生查询、懒加载一律生效；需要「含已删」的管理端列表与恢复入口
     * 走原生 SQL（原生查询不受 restriction 影响），见 {@code CustomerRepository}。
     */
    @Column(name = "deleted_at")
    private java.time.LocalDateTime deletedAt;

    /** 删除人（操作者 user id），仅作审计线索 */
    @Column(name = "deleted_by", length = 36)
    private java.util.UUID deletedBy;

    /** 客户编号（业务唯一，如 CUST-DEFAULT） */
    @Column(name = "code", length = 64, nullable = false, unique = true)
    private String code;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    /** 所属行业 */
    @Column(name = "industry", length = 100)
    private String industry;

    /** 行业编码（引用 industry_types.code，t129） */
    @Column(name = "industry_code", length = 16)
    private String industryCode;

    /** 客户等级：A / B / C */
    @Column(name = "level", length = 8)
    private String level;

    @Column(name = "contact_name", length = 100)
    private String contactName;

    @Column(name = "contact_phone", length = 50)
    private String contactPhone;

    @Column(name = "contact_email", length = 200)
    private String contactEmail;

    /** 客户经理（user id） */
    @Column(name = "owner_id")
    private UUID ownerId;

    /** active / inactive */
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "active";

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.status == null) {
            this.status = "active";
        }
        log.debug("创建客户实体: code={}, name={}", code, name);
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
