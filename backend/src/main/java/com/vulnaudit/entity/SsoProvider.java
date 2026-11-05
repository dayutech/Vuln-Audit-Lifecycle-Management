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
 * 第三方 SSO 提供商配置（t104 / t116）
 *
 * <p><b>凭据不写死在代码里</b>：`client_id` / `client_secret` / 三个 URL / scopes 都在本表，
 * 管理员可通过 Web 配置接口（{@code PUT /api/v1/admin/sso/providers/{code}}）维护，**无需上服务器改库**。
 *
 * <p><b>「已配置」判定（t116）</b>：`client_id`、`client_secret`、`authorize_url`、`token_url`、`userinfo_url`
 * 五项都非空才算 configured（scopes 可选）。但「是否对登录页展示」只看 {@code enabled}：
 * 开启但未配置时登录页仍展示按钮（带 {@code configured=false}，前端提示未配置），
 * 真正点进去调 authorize-url 才会 503。
 *
 * <p>默认 4 条（github / microsoft / google / wechat）由启动初始化器在表为空时插入，全部 `enabled=false`
 * （authorize/token/userinfo URL 与 scopes 预填，凭据留空）。
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

    /** 最后修改人（t116：Web 配置接口写入，管理员用户ID） */
    @Column(name = "updated_by")
    private UUID updatedBy;

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
     * 是否已完整配置（t116）：5 个必填项都非空才算 ——
     * {@code client_id}、{@code client_secret}、{@code authorize_url}、{@code token_url}、{@code userinfo_url}。
     *
     * <p>注意：**是否展示**只看 {@code enabled}；configured 只是给前端/管理端标注「点了能不能真的登录」。
     */
    public boolean isConfigured() {
        return notBlank(clientId) && notBlank(clientSecret)
                && notBlank(authorizeUrl) && notBlank(tokenUrl) && notBlank(userInfoUrl);
    }

    /**
     * 是否可发起授权：既开启又已配置（两者缺一，authorize-url 分别返回 400 / 503）
     */
    public boolean isUsable() {
        return enabled && isConfigured();
    }

    /**
     * secret 掩码（t116）：最多露首 4 位与末 4 位，中间固定 4 个星号；空值返回 null。
     *
     * <p>输出示例：{@code gh_****abcd}；过短（≤4 位）一律返回 {@code ****}，避免把整个 secret 露出来。
     */
    public String maskedClientSecret() {
        if (!notBlank(clientSecret)) {
            return null;
        }
        int length = clientSecret.length();
        if (length <= 4) {
            return "****";
        }
        int prefixLength = Math.min(4, length - 4);
        String prefix = clientSecret.substring(0, prefixLength);
        String suffix = clientSecret.substring(length - 4);
        return prefix + "****" + suffix;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
