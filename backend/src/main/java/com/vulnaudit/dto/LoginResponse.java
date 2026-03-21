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

}
