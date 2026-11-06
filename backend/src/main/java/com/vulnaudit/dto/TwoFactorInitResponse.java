package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** 双因子初始化响应（t124）：secret 只在 init 时返回一次，服务端加密存储 */
@Schema(description = "双因子初始化响应")
public record TwoFactorInitResponse(
        String secretBase32,
        String otpauthUri) {
}
