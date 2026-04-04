package com.vulnaudit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/**
 * 资产创建请求DTO
 */
@Data
public class AssetCreateRequest {

    @NotBlank(message = "资产名称不能为空")
    @Size(max = 200, message = "名称长度不能超过200个字符")
    private String name;

    @NotBlank(message = "目标类型不能为空")
    private String targetType;

    @NotBlank(message = "目标来源不能为空")
    private String targetSource;

    @Size(max = 1000, message = "描述长度不能超过1000个字符")
    private String description;

    @NotBlank(message = "优先级不能为空")
    private String priority;

    @NotBlank(message = "资产状态不能为空")
    private String status;

    @NotNull(message = "所属团队不能为空")
    private UUID teamId;
}
