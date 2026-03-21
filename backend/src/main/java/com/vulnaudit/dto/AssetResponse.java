package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 资产响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "资产响应")
public class AssetResponse {

    @Schema(description = "资产ID")
    private UUID id;

    @Schema(description = "资产名称")
    private String name;

    @Schema(description = "资产编号")
    private String code;

    @Schema(description = "所属团队ID")
    private UUID teamId;

    @Schema(description = "目标类型")
    private String targetType;

    @Schema(description = "目标来源")
    private String targetSource;

    @Schema(description = "资产描述")
    private String description;

    @Schema(description = "优先级")
    private String priority;

    @Schema(description = "资产状态")
    private String status;

    @Schema(description = "负责人ID")
    private UUID ownerId;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;
}
