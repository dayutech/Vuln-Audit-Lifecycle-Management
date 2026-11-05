package com.vulnaudit.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * SSO 首次登录产生的「待审批绑定」（t104）
 *
 * <p>流程：陌生第三方身份回调 → 建 {@code registration_requests(source=SSO)} + 本表（PENDING）→ 返回 202 不发 token；
 * 管理员审批通过 → 建/激活本地用户并把本表记录转为 {@code sso_bindings}（本表置 ACTIVATED）。
 *
 * <p>为什么单独一张表：审批前不产生任何「可登录绑定」，避免陌生身份直接拿到账号；
 * 同时保留 external_id 供审批通过时落绑定。
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "sso_pending_bindings",
        uniqueConstraints = @UniqueConstraint(name = "uk_sso_pending_provider_external",
                columnNames = {"provider_code", "external_id"}),
        indexes = @Index(name = "idx_sso_pending_request", columnList = "registration_request_id"))
public class SsoPendingBinding {

    @Id
    @GeneratedValue(generator = "UUID")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** 关联的注册申请（审批通过时据此激活） */
    @Column(name = "registration_request_id")
    private UUID registrationRequestId;

    @Column(name = "provider_code", length = 32, nullable = false)
    private String providerCode;

    @Column(name = "external_id", length = 255, nullable = false)
    private String externalId;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "display_name", length = 128)
    private String displayName;

    /** PENDING / ACTIVATED / CANCELLED */
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "activated_at")
    private LocalDateTime activatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = "PENDING";
        }
    }

    public boolean isPending() {
        return "PENDING".equals(status);
    }
}
