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
 * 资产—项目挂靠（t67）
 *
 * <p>一个资产可以同时属于多个项目（多对多），因此 {@link Asset} 上**不添加** projectId 单值外键：
 * 那样只能表达「一个资产属于一个项目」，与「同一资产为多个客户/项目服务」的真实业务冲突，
 * 并且会让历史数据回填变成有损覆盖。挂靠关系单独建表，并可选择落到具体期次。
 *
 * <h3>NULL 安全的唯一性（要求 5）</h3>
 * MariaDB 的唯一索引对 NULL 值不去重（多个 NULL 视为互不相同），因此若直接用
 * {@code UNIQUE(asset_id, project_id, phase_id)}，「该项目下无期次」的挂靠可以重复插入。
 * 本实现采用 <b>phase_key 计算列</b>方案（任务书允许的第二种）：
 * <ul>
 *   <li>{@code phase_id} 保留可空语义（无期次 = NULL，业务含义清晰）；</li>
 *   <li>{@code phase_key} 非空，写入时取 {@code phaseId}，为 null 时取哨兵 UUID
 *       {@link #PHASE_KEY_NONE}；由 {@link #syncPhaseKey()} 在 persist/update 前维护；</li>
 *   <li>唯一约束建在 {@code (asset_id, project_id, phase_key)} 上，于是
 *       「同一资产 + 同一项目 + 无期次」只能有一条，「+ 具体期次」也各自唯一。</li>
 * </ul>
 * 选择计算列而非 MariaDB 生成列（{@code AS (...) STORED}）的原因：生成列语法不是可移植 SQL，
 * H2（测试库）与 MariaDB 写法不同，而计算列在两边行为完全一致，且迁移里可显式回填。
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "asset_project_links",
        uniqueConstraints = @UniqueConstraint(name = "uk_asset_project_links_asset_project_phase",
                columnNames = {"asset_id", "project_id", "phase_key"}),
        indexes = {
                @Index(name = "idx_asset_project_links_asset", columnList = "asset_id"),
                @Index(name = "idx_asset_project_links_project", columnList = "project_id"),
                @Index(name = "idx_asset_project_links_phase", columnList = "phase_id"),
                @Index(name = "idx_asset_project_links_customer", columnList = "customer_id")
        })
public class AssetProjectLink {

    /** 「无期次」的哨兵值，用于 NULL 安全的唯一索引（全 0 UUID，不可能与真实期次冲突） */
    public static final UUID PHASE_KEY_NONE = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Id
    @GeneratedValue(generator = "UUID")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    /** 可空：为空表示挂靠在项目级（不落到具体期次） */
    @Column(name = "phase_id")
    private UUID phaseId;

    /**
     * 参与唯一索引的期次键：{@code phaseId} 或 {@link #PHASE_KEY_NONE}。
     * 应用侧由 {@link #syncPhaseKey()} 维护；迁移脚本里显式写入。
     */
    @Column(name = "phase_key", nullable = false)
    private UUID phaseKey;

    /** 冗余客户 id：便于按客户直接统计，避免每次 join projects */
    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    /** TARGET（测试目标）/ SHARED（共享）/ REFERENCE（参考） */
    @Column(name = "link_type", length = 20, nullable = false)
    @Builder.Default
    private String linkType = "TARGET";

    @Column(name = "remark", length = 500)
    private String remark;

    @Column(name = "linked_by")
    private UUID linkedBy;

    @Column(name = "linked_at", nullable = false, updatable = false)
    private LocalDateTime linkedAt;

    /** 保证 phase_key 与 phase_id 始终一致（含外部直接 set phaseId 的情况） */
    @PrePersist
    @PreUpdate
    protected void syncPhaseKey() {
        this.phaseKey = this.phaseId != null ? this.phaseId : PHASE_KEY_NONE;
        if (this.linkedAt == null) {
            this.linkedAt = LocalDateTime.now();
        }
        if (this.linkType == null) {
            this.linkType = "TARGET";
        }
        log.debug("资产挂靠: assetId={}, projectId={}, phaseId={}", assetId, projectId, phaseId);
    }
}
