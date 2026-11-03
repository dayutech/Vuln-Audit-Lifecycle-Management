package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * 发起模拟视角请求（t90）
 *
 * @param userId 被模拟用户（必须存在、status=active、且不是 ADMIN）
 * @param reason 模拟原因（写入审计，强烈建议填写）
 */
public record ImpersonateRequest(
        @NotNull(message = "目标用户不能为空")
        @Schema(description = "被模拟的用户ID", requiredMode = Schema.RequiredMode.REQUIRED)
        UUID userId,
        @Size(max = 200, message = "模拟原因过长")
        @Schema(description = "模拟原因（写入审计）")
        String reason) {
}
