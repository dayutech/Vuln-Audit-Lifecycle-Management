package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * SSO 回调请求（t104）
 *
 * @param code  第三方授权码
 * @param state 授权前拿到的 state（一次性，5 分钟有效）
 */
@Schema(description = "SSO 回调请求")
public record SsoCallbackRequest(
        @NotBlank(message = "validation.sso.code.required") String code,
        @NotBlank(message = "validation.sso.state.required") String state) {
}
