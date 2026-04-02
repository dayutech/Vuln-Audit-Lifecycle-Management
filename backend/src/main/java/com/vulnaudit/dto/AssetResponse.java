package com.vulnaudit.dto;

import com.vulnaudit.entity.Asset;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 资产响应DTO
 * 
 * 功能说明：
 * - 封装资产信息返回给前端
 * - 包含关联的团队名称和负责人名称
 * - 用于资产列表和详情展示
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

    @Schema(description = "所属团队名称")
    private String teamName;

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

    @Schema(description = "负责人用户名")
    private String ownerName;

    @Schema(description = "创建时间")
    private LocalDateTime createdAt;

    @Schema(description = "更新时间")
    private LocalDateTime updatedAt;

    /**
     * 从Asset实体创建AssetResponse
     * 
     * @param asset 资产实体
     * @param teamName 团队名称（可为null）
     * @param ownerName 负责人用户名（可为null）
     * @return AssetResponse对象
     */
    public static AssetResponse fromAsset(Asset asset, String teamName, String ownerName) {
        if (asset == null) {
            return null;
        }
        return AssetResponse.builder()
                .id(asset.getId())
                .name(asset.getName())
                .code(asset.getCode())
                .teamId(asset.getTeamId())
                .teamName(teamName)
                .targetType(asset.getTargetType())
                .targetSource(asset.getTargetSource())
                .description(asset.getDescription())
                .priority(asset.getPriority())
                .status(asset.getStatus())
                .ownerId(asset.getOwnerId())
                .ownerName(ownerName)
                .createdAt(asset.getCreatedAt())
                .updatedAt(asset.getUpdatedAt())
                .build();
    }
}
