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
 * 本地账号 ↔ 第三方身份绑定（t104）
 *
 * <p>唯一性：`(provider_code, external_id)` 全局唯一（一个第三方身份只能绑一个本地账号），
 * `(user_id, provider_code)` 也唯一（一个本地账号同一 provider 只能绑一次 → 重复绑定返回 409）。
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "sso_bindings",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_sso_bindings_provider_external",
                        columnNames = {"provider_code", "external_id"}),
                @UniqueConstraint(name = "uk_sso_bindings_user_provider",
                        columnNames = {"user_id", "provider_code"})
        })
public class SsoBinding {

    @Id
    @GeneratedValue(generator = "UUID")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** 提供商代码（github / microsoft / google / wechat） */
    @Column(name = "provider_code", length = 32, nullable = false)
    private String providerCode;

    /** 第三方的稳定用户ID（如 GitHub 的 id、微信的 openid） */
    @Column(name = "external_id", length = 255, nullable = false)
    private String externalId;

    /** 第三方展示名（便于管理端识别） */
    @Column(name = "display_name", length = 128)
    private String displayName;

    @Column(name = "bound_at", nullable = false)
    private LocalDateTime boundAt;

    @PrePersist
    protected void onCreate() {
        if (boundAt == null) {
            boundAt = LocalDateTime.now();
        }
    }
}
