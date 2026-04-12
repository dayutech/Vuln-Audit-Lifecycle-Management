package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * 任务更新请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "任务更新请求")
public class TaskUpdateRequest {

    @Schema(description = "任务状态")
    private String status;

    @Schema(description = "任务标题")
    private String title;

    @Schema(description = "任务描述")
    private String description;

    @Schema(description = "截止日期")
    private String dueDate;

    @Schema(description = "审计结论")
    private String auditConclusion;

    @Schema(description = "预估工时")
    private BigDecimal estimatedHours;

    @Schema(description = "环境信息收集阶段文档ID")
    private UUID envCollectDocId;

    @Schema(description = "环境搭建阶段文档ID")
    private UUID envSetupDocId;

    @Schema(description = "代码审计阶段文档ID")
    private UUID codeAuditDocId;

}
