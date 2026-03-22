package com.vulnaudit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

    @NotBlank(message = "资产编码不能为空")
    @Size(max = 50, message = "编码长度不能超过50个字符")
    private String code;

    @NotBlank(message = "目标类型不能为空")
    private String targetType;

    @NotBlank(message = "目标来源不能为空")
    private String targetSource;

    @Size(max = 1000, message = "描述长度不能超过1000个字符")
    private String description;

    private String priority;

    private UUID ownerId;

    private UUID teamId;
}
