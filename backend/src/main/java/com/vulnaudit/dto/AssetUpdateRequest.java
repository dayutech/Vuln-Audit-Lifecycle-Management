package com.vulnaudit.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/**
 * 资产更新请求DTO
 */
@Data
public class AssetUpdateRequest {

    @Size(max = 200, message = "名称长度不能超过200个字符")
    private String name;

    @Size(max = 1000, message = "描述长度不能超过1000个字符")
    private String description;

    private String priority;

    private String status;

    private String targetType;

    private String targetSource;

    private UUID teamId;

    private UUID ownerId;
}
