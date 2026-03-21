package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 修改密码请求DTO
 * 
 * 功能说明：
 * - 封装修改密码请求参数
 * - 包含旧密码和新密码
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "修改密码请求")
public class ChangePasswordRequest {

    @Schema(description = "旧密码", example = "OldPassword@123", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "旧密码不能为空")
    private String oldPassword;

    @Schema(description = "新密码", example = "NewPassword@456", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 100, message = "新密码长度必须在6-100字符之间")
    private String newPassword;

}
