package com.vulnaudit.dto;

import com.vulnaudit.entity.RegistrationRequest;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 注册申请视图（t102 / t104）
 *
 * <p>对外<b>绝不</b>包含 passwordHash。
 *
 * @param source       LOCAL（自助注册）/ SSO（第三方首次登录，t104）
 * @param providerCode SSO 提供商代码（source=SSO 时非空）
 * @param externalId   第三方稳定用户ID
 * @param displayName  第三方昵称
 */
@Schema(description = "注册申请")
public record RegistrationRequestVO(
        UUID id,
        String username,
        String phone,
        String email,
        boolean phoneVerified,
        boolean emailVerified,
        String status,
        UUID reviewerId,
        String reviewNote,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt,
        String source,
        String providerCode,
        String externalId,
        String displayName) {

    /** 由实体转换（不含密码哈希） */
    public static RegistrationRequestVO from(RegistrationRequest entity) {
        return new RegistrationRequestVO(
                entity.getId(),
                entity.getUsername(),
                entity.getPhone(),
                entity.getEmail(),
                entity.isPhoneVerified(),
                entity.isEmailVerified(),
                entity.getStatus() == null ? null : entity.getStatus().name(),
                entity.getReviewerId(),
                entity.getReviewNote(),
                entity.getCreatedAt(),
                entity.getReviewedAt(),
                entity.getSource(),
                entity.getProviderCode(),
                entity.getExternalId(),
                entity.getDisplayName());
    }
}
