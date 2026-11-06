package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录请求DTO
 * 
 * 功能说明：
 * - 封装用户登录请求参数
 * - 包含用户名和密码字段
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "登录请求")
public class LoginRequest {

    @Schema(description = "用户名", example = "admin", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "validation.auth.username.required")
    private String username;

    @Schema(description = "密码", example = "password123", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "validation.auth.password.required")
    private String password;

    /**
     * 是否记住登录状态（t124）
     *
     * <p>true 时登录成功额外下发独立的 remember-me 长效 cookie（{@code owl_rm}，默认 30 天）；
     * **JWT 本身不延长**。JWT 过期后可用 {@code GET /auth/refresh} 凭该 cookie 轮换刷新。
     * 省略/null = 不记住。
     */
    @Schema(description = "记住登录（下发 remember-me cookie，JWT 不延长）", example = "false")
    private Boolean rememberMe;

}
