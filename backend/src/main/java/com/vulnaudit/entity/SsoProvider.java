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
 * 第三方 SSO 提供商配置（t104）
 *
 * <p><b>凭据不写死在代码里</b>：`client_id` / `client_secret` / 三个 URL / scopes 都在本表，
 * 管理员可直接改库（或由运维填写）。缺 `client_id` 或 `client_secret` 视为「未配置」：
 * 即使 `enabled=true`，`authorize-url` 也会返回 503「该登录方式尚未完成配置」，登录页按钮也不展示。
 *
 * <p>默认 4 条（github / microsoft / google / wechat）由启动初始化器在表为空时插入，全部 `enabled=false`。
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "sso_providers",
        indexes = @Index(name = "uk_sso_providers_code", columnList = "code", unique = true))
public class SsoProvider {

    @Id
    @GeneratedValue(generator = "UUID")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** 提供商代码：github / microsoft / google / wechat */
    @Column(name = "code", length = 32, nullable = false, unique = true)
    private String code;

    /** 展示名称 */
    @Column(name = "name", length = 64, nullable = false)
    private String name;

    /** 图标标识（前端据此渲染，如 github / microsoft / google / wechat） */
    @Column(name = "icon", length = 64)
    private String icon;

    /** 是否开放该登录方式（管理员开关） */
    @Column(name = "enabled", nullable = false)
    @Builder.Default
    private boolean enabled = false;

    @Column(name = "client_id", length = 255)
    private String clientId;

    @Column(name = "client_secret", length = 512)
    private String clientSecret;

    @Column(name = "authorize_url", length = 512)
    private String authorizeUrl;

    @Column(name = "token_url", length = 512)
    private String tokenUrl;

    @Column(name = "userinfo_url", length = 512)
    private String userInfoUrl;

    @Column(name = "scopes", length = 255)
    private String scopes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * 是否已配置凭据（缺 client_id / client_secret 即视为未配置）
     */
    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank();
    }

    /**
     * 是否可对外展示 / 可发起授权：既开启又已配置
     */
    public boolean isUsable() {
        return enabled && isConfigured();
    }
}
