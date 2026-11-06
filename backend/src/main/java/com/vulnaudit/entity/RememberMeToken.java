package com.vulnaudit.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * remember-me 长效令牌（t124）
 *
 * <p>Cookie 里只放 {@code <seriesId>.<secret>}；库里只存 {@code SHA-256(secret)}。
 * 轮换（rotation）时保留 series、作废旧行，检测到旧行重放则整条 series 撤销。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "remember_me_tokens", indexes = {
        @Index(name = "idx_rm_user", columnList = "user_id"),
        @Index(name = "idx_rm_series", columnList = "series_id"),
        @Index(name = "idx_rm_hash", columnList = "token_hash")
})
public class RememberMeToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "series_id", nullable = false, length = 64)
    private String seriesId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "ip", length = 64)
    private String ip;

    /** 是否仍可用（未撤销且未过期） */
    public boolean isUsable() {
        return revokedAt == null && expiresAt != null && expiresAt.isAfter(LocalDateTime.now());
    }

    @PrePersist
    void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
