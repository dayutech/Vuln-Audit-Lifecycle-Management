package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    @NotBlank(message = "任务标题不能为空")
    private String title;

    @Schema(description = "任务类型")
    @NotBlank(message = "任务类型不能为空")
    private String type;

    @Schema(description = "关联ID（资产/漏洞等）")
    private UUID referenceId;

    @Schema(description = "指派人ID")
    @NotNull(message = "指派人不能为空")
    private UUID assigneeId;

    @Schema(description = "优先级")
    private String priority;

    @Schema(description = "截止日期")
    private LocalDateTime dueDate;

    @Schema(description = "任务描述")
    private String description;
}
