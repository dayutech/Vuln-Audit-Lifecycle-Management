package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * 解绑 SSO 请求（t104）：**必须带当前密码**，防止拿到已登录会话的人直接解绑。
 */
@Schema(description = "解绑 SSO 请求")
public record UnbindSsoRequest(
        @NotBlank(message = "validation.sso.password.required") String password) {
}
