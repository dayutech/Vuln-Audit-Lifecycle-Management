package com.vulnaudit.entity;

import com.vulnaudit.entity.enums.RegistrationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 自助注册申请（t102）
 *
 * <p>职责：承载「已验证手机号/邮箱、但尚未被管理员审批」的注册意图。
 *
 * <h3>与 users 的关系</h3>
 * 注册时会同时落一条 {@code users.status=pending_approval} 的占位用户（**不可登录**，见
 * {@code UserServiceImpl#authenticate} 的 403 分支），本表记录审批工作流所需的原始信息
 * （密码哈希、手机号验证标记、手机/邮箱、拒绝原因、审批人）。
 * 这样用户名/手机号/邮箱的唯一性由数据库直接兜住，审批通过时只需把占位用户转成 active 并补齐验证标记。
 *
 * <h3>为什么单独存密码哈希</h3>
 * 占位用户本身也带 password_hash；本表冗余一份是为了让「拒绝后再次申请」与审计回溯
 * 不依赖占位用户的当前状态（占位用户可能被管理员手工改过）。
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "registration_requests",
        indexes = {
                @Index(name = "idx_registration_requests_status", columnList = "status, created_at"),
                @Index(name = "idx_registration_requests_username", columnList = "username"),
                @Index(name = "idx_registration_requests_phone", columnList = "phone")
        })
public class RegistrationRequest {

    @Id
    @GeneratedValue(generator = "UUID")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** 申请的用户名 */
    @Column(name = "username", length = 50, nullable = false)
    private String username;

    /** BCrypt 密码哈希 */
    @Column(name = "password_hash", length = 255, nullable = false)
    private String passwordHash;

    /** 手机号（本地注册必填；SSO 注册没有手机号，t104 起可空） */
    @Column(name = "phone", length = 20)
    private String phone;

    /** 来源：LOCAL（自助注册）/ SSO（第三方首次登录），t104 */
    @Column(name = "source", length = 16, nullable = false)
    @Builder.Default
    private String source = "LOCAL";

    /** SSO 提供商代码（source=SSO 时非空） */
    @Column(name = "provider_code", length = 32)
    private String providerCode;

    /** 第三方稳定用户ID（source=SSO 时非空） */
    @Column(name = "external_id", length = 255)
    private String externalId;

    /** 第三方昵称（审批列表展示用） */
    @Column(name = "display_name", length = 128)
    private String displayName;

    /** 邮箱（可选） */
    @Column(name = "email", length = 255)
    private String email;

    /** 手机号是否已通过验证码验证 */
    @Column(name = "phone_verified", nullable = false)
    @Builder.Default
    private boolean phoneVerified = false;

    /** 邮箱是否已通过验证码验证（填了邮箱则必须为 true） */
    @Column(name = "email_verified", nullable = false)
    @Builder.Default
    private boolean emailVerified = false;

    /** 审批状态 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private RegistrationStatus status = RegistrationStatus.PENDING;

    /** 审批人（管理员）用户ID */
    @Column(name = "reviewer_id")
    private UUID reviewerId;

    /** 审批备注（拒绝时必填） */
    @Column(name = "review_note", length = 500)
    private String reviewNote;

    /** 申请时间 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 审批时间 */
    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (status == null) {
            status = RegistrationStatus.PENDING;
        }
    }

    /** 是否待审批 */
    public boolean isPending() {
        return status == RegistrationStatus.PENDING;
    }
}
