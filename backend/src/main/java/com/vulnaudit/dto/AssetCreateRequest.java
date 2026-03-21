package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 资产创建请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "资产创建请求")
public class AssetCreateRequest {

    @Schema(description = "资产名称", example = "目标系统A")
    @NotBlank(message = "资产名称不能为空")
    @Size(max = 200, message = "资产名称最长200字符")
    private String name;

    @Schema(description = "资产编号", example = "ASSET-001")
    @NotBlank(message = "资产编号不能为空")
    @Size(max = 50, message = "资产编号最长50字符")
    private String code;

    @Schema(description = "目标类型", example = "Web应用")
    @NotBlank(message = "目标类型不能为空")
    @Size(max = 100, message = "目标类型最长100字符")
    private String targetType;

    @Schema(description = "目标来源", example = "客户委托")
    @NotBlank(message = "目标来源不能为空")
    @Size(max = 20, message = "目标来源最长20字符")
    private String targetSource;

    @Schema(description = "资产描述")
    private String description;

    @Schema(description = "优先级", example = "P2")
    private String priority;

    @Schema(description = "所属团队ID")
    @NotNull(message = "所属团队不能为空")
    private UUID teamId;

    @Schema(description = "负责人ID")
    @NotNull(message = "负责人不能为空")
    private UUID ownerId;
}
