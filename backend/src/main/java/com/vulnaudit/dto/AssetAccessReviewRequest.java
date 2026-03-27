package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 资产访问审批请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "资产访问审批请求")
public class AssetAccessReviewRequest {

    @Schema(description = "是否批准")
    private boolean approved;

    @Schema(description = "审批备注")
    private String reviewNote;
}
