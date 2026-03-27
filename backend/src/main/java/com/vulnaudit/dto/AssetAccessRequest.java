package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 资产访问申请请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "资产访问申请请求")
public class AssetAccessRequest {

    @Schema(description = "资产ID")
    private UUID assetId;

    @Schema(description = "是否可查看文档")
    private Boolean canViewDocuments;

    @Schema(description = "是否可查看环境配置")
    private Boolean canViewEnvironments;

    @Schema(description = "是否可查看文件")
    private Boolean canViewFiles;

    @Schema(description = "允许访问的文件类型")
    private String allowedFileTypes;

    @Schema(description = "申请说明")
    private String requestNote;
}
