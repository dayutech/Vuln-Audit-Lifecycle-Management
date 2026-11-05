package com.vulnaudit.dto;

import com.vulnaudit.entity.RegistrationRequest;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 注册申请视图（t102）
 *
 * <p>对外<b>绝不</b>包含 passwordHash。
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
        LocalDateTime reviewedAt) {

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
                entity.getReviewedAt());
    }
}
