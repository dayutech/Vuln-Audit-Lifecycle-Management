package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/** 双因子状态（t124）：不返回 secret */
@Schema(description = "双因子状态")
public record TwoFactorStatusResponse(
        boolean enabled,
        LocalDateTime confirmedAt,
        long recoveryCodesLeft,
        boolean forced,
        boolean available) {
}
