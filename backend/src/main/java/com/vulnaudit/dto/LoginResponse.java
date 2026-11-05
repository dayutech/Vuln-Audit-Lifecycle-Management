package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应DTO
 * 
 * 功能说明：
 * - 封装登录成功后的响应数据
 * - 包含JWT令牌和用户基本信息
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "登录响应")
public class LoginResponse {

    @Schema(description = "JWT访问令牌", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...")
    private String token;

    @Schema(description = "令牌类型", example = "Bearer")
    private String tokenType;

    @Schema(description = "令牌过期时间（毫秒）", example = "86400000")
    private Long expiresIn;

    @Schema(description = "用户ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String userId;

    @Schema(description = "用户名", example = "admin")
    private String username;

    @Schema(description = "用户全名", example = "张三")
    private String fullName;

    @Schema(description = "用户邮箱", example = "admin@example.com")
    private String email;

    /**
     * 登录页主题偏好（t96）：登录后直接拿到，避免再发一次 /users/me/preferences
     *
     * <p>t112 三态：{@code null} 表示该用户「跟随系统默认」（管理员缺省值）。
     */
    @Schema(description = "登录页主题偏好；null 表示跟随系统默认", example = "pencil", nullable = true)
    private String loginTheme;

    /**
     * 是否跟随系统默认（t112）：等价于 {@code loginTheme == null}
     *
     * <p>单独给布尔位，避免前端把 {@code loginTheme:null} 误当成「字段缺失」而回退到本地缓存。
     */
    @Schema(description = "是否跟随系统默认（loginTheme 为 null 时 true）", example = "false")
    private Boolean followDefault;

}
