package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * 管理员开关 SSO 提供商（t104）
 */
@Schema(description = "开关 SSO 提供商")
public record UpdateSsoProviderRequest(
        @Schema(description = "是否开放该登录方式", example = "true")
        @NotNull(message = "validation.sso.enabled.required") Boolean enabled) {
}
