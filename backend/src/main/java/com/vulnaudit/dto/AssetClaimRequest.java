package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * 资产认领请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "资产认领请求")
public class AssetClaimRequest {

    @Schema(description = "资产ID")
    private UUID assetId;

    @Schema(description = "认领备注")
    private String note;
}
