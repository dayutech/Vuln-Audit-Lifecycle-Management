package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 任务审核动作请求DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "任务审核动作请求")
public class TaskReviewActionRequest {

    @Schema(description = "审核签字")
    private String signature;

    @Schema(description = "审核备注")
    private String comment;
}
