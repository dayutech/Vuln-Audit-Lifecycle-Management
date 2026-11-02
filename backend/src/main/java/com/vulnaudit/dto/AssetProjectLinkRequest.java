package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/**
 * 资产挂靠请求（t69）
 *
 * <p>同一资产可挂到<b>多个项目、多个客户</b>的项目（跨客户挂靠允许），
 * 但同一 {@code (asset, project, phase)} 只能有一条，重复挂靠返回 409。
 */
@Data
@Schema(description = "资产挂靠请求")
public class AssetProjectLinkRequest {

    @NotNull(message = "validation.asset_link.project.required")
    @Schema(description = "项目ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private UUID projectId;

    @Schema(description = "期次ID（可空；非空时项目必须启用分期且期次属于该项目）")
    private UUID phaseId;

    @Schema(description = "挂靠类型 TARGET / SHARED / REFERENCE，默认 TARGET")
    private String linkType;

    @Size(max = 500, message = "validation.asset_link.remark.too_long")
    @Schema(description = "备注")
    private String remark;
}
