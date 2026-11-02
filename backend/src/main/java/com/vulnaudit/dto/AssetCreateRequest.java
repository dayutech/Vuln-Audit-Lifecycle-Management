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

    @NotBlank(message = "validation.asset.name.required")
    @Size(max = 200, message = "validation.asset.name.too_long")
    private String name;

    @NotBlank(message = "validation.asset.target_type.required")
    private String targetType;

    @NotBlank(message = "validation.asset.target_source.required")
    private String targetSource;

    @Size(max = 1000, message = "validation.asset.description.too_long")
    private String description;

    @NotBlank(message = "validation.asset.priority.required")
    private String priority;

    @NotBlank(message = "validation.asset.status.required")
    private String status;

    @NotNull(message = "validation.asset.team.required")
    private UUID teamId;
}
