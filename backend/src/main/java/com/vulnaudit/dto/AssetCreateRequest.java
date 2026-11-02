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

    /**
     * 所属项目（t69 硬约束）：创建资产必须挂靠到一个项目。
     *
     * <p>历史资产已由 V18 迁移回填到「历史项目」（PRJ-HISTORY），因此不再提供
     * 「无项目资产」的创建路径；缺失时返回 400「创建资产必须指定所属项目」。
     */
    @NotNull(message = "validation.asset.project.required")
    private UUID projectId;

    /** 可选期次：非空时项目必须启用分期且期次属于该项目 */
    private UUID phaseId;

    /** 挂靠类型，缺省 TARGET */
    private String linkType;

    /** 挂靠备注 */
    @Size(max = 500, message = "validation.asset_link.remark.too_long")
    private String remark;
}
