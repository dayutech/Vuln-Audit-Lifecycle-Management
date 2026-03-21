package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 资产更新请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "资产更新请求")
public class AssetUpdateRequest {

    @Schema(description = "资产名称")
    @Size(max = 200, message = "资产名称最长200字符")
    private String name;

    @Schema(description = "目标类型")
    @Size(max = 100, message = "目标类型最长100字符")
    private String targetType;

    @Schema(description = "目标来源")
    @Size(max = 20, message = "目标来源最长20字符")
    private String targetSource;

    @Schema(description = "资产描述")
    private String description;

    @Schema(description = "优先级")
    private String priority;

    @Schema(description = "所属团队ID")
    private UUID teamId;

    @Schema(description = "负责人ID")
    private UUID ownerId;
}
