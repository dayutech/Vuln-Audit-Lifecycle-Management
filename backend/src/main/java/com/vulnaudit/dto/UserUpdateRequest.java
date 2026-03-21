package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户更新请求DTO
 * 
 * 功能说明：
 * - 封装用户更新请求参数
 * - 包含可更新的用户信息字段
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "用户更新请求")
public class UserUpdateRequest {

    @Schema(description = "邮箱", example = "zhangsan@example.com")
    @Email(message = "邮箱格式不正确")
    @Size(max = 255, message = "邮箱长度不能超过255字符")
    private String email;

    @Schema(description = "手机号", example = "13800138000")
    @Size(max = 20, message = "手机号长度不能超过20字符")
    private String phone;

    @Schema(description = "真实姓名", example = "张三")
    @Size(max = 100, message = "真实姓名长度不能超过100字符")
    private String fullName;

    @Schema(description = "头像URL", example = "https://example.com/avatar.jpg")
    @Size(max = 500, message = "头像URL长度不能超过500字符")
    private String avatar;

    @Schema(description = "职位", example = "高级安全工程师")
    @Size(max = 100, message = "职位长度不能超过100字符")
    private String position;

}
