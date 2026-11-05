package com.vulnaudit.entity;

import com.vulnaudit.entity.enums.VerificationPurpose;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import jakarta.persistence.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 验证码（t102）
 *
 * <h3>安全约定</h3>
 * <ul>
 *   <li><b>只存哈希</b>：明文验证码只出现在「发送」那一刻（交给 {@code VerificationCodeSender}），
 *       库里落 BCrypt 哈希，任何查询接口都不会回传明文；</li>
 *   <li><b>一次性</b>：校验成功即写 {@code consumed_at}，之后不可复用；</li>
 *   <li><b>有期限</b>：{@code expires_at} 默认 created_at + 300s；</li>
 *   <li><b>有次数</b>：{@code attempts} 每次校验失败 +1，达到上限后该码作废（需重新获取）；</li>
 *   <li><b>限流</b>：同一 target+purpose 在 resend-interval 内只允许发送一次（服务层 429）。</li>
 * </ul>
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "verification_codes",
        indexes = {
                @Index(name = "idx_verification_codes_target", columnList = "target, purpose, created_at")
        })
public class VerificationCode {

    @Id
    @GeneratedValue(generator = "UUID")
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** 接收方：手机号或邮箱（统一小写去空格后存储） */
    @Column(name = "target", length = 120, nullable = false)
    private String target;

    /** 用途 */
    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", length = 20, nullable = false)
    private VerificationPurpose purpose;

    /** BCrypt 哈希（不存明文） */
    @Column(name = "code_hash", length = 255, nullable = false)
    private String codeHash;

    /** 过期时间 */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** 失败尝试次数 */
    @Column(name = "attempts", nullable = false)
    @Builder.Default
    private int attempts = 0;

    /** 消费时间（非空即已使用） */
    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    /** 生成时间 */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    /** 是否已消费 */
    public boolean isConsumed() {
        return consumedAt != null;
    }

    /** 是否已过期 */
    public boolean isExpired() {
        return expiresAt == null || expiresAt.isBefore(LocalDateTime.now());
    }
}
