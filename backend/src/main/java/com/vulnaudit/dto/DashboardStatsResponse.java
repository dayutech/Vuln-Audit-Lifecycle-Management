package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 仪表盘统计响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "仪表盘统计响应")
public class DashboardStatsResponse {

    @Schema(description = "总数")
    private long total;

    @Schema(description = "按状态统计")
    private Map<String, Long> byStatus;

    @Schema(description = "按类型统计")
    private Map<String, Long> byType;

    @Schema(description = "按优先级统计")
    private Map<String, Long> byPriority;
}
