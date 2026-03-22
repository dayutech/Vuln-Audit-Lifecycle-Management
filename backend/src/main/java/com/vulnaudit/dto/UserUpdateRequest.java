package com.vulnaudit.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户更新请求DTO
 */
@Data
public class UserUpdateRequest {

    @Size(max = 50, message = "姓名长度不能超过50个字符")
    private String fullName;

    @Size(max = 100, message = "邮箱长度不能超过100个字符")
    private String email;

    @Size(max = 20, message = "手机号长度不能超过20个字符")
    private String phone;

    @Size(max = 500, message = "头像URL长度不能超过500个字符")
    private String avatar;

    @Size(max = 100, message = "职位长度不能超过100个字符")
    private String position;

    private String status;
}
