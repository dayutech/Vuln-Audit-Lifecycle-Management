package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 任务创建请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "任务创建请求")
public class TaskCreateRequest {

    @Schema(description = "任务标题")
    private String title;

    @Schema(description = "关联ID（资产/漏洞等）")
    private UUID referenceId;

    @Schema(description = "关联资产ID")
    private UUID assetId;

    @Schema(description = "关联资产版本ID")
    private UUID assetVersionId;

    @Schema(description = "指派人ID")
    private UUID assigneeId;

    @Schema(description = "截止日期")
    private LocalDateTime dueDate;

    @Schema(description = "任务描述")
    private String description;

    @Schema(description = "预估工时")
    private BigDecimal estimatedHours;

    @Schema(description = "任务状态")
    private String status;
}
