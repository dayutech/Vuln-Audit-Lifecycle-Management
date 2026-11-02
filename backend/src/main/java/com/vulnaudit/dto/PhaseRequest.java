package com.vulnaudit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 期次创建/更新请求（t68）
 *
 * @param phaseNo   期次号（留空则按项目内最大号 +1 自动分配）
 * @param name      期次名称
 * @param startDate 开始日期
 * @param endDate   结束日期
 * @param status    状态（PLANNED / IN_PROGRESS / DONE 等，默认 PLANNED）
 */
public record PhaseRequest(
        Integer phaseNo,
        @NotBlank(message = "期次名称不能为空") @Size(max = 200) String name,
        LocalDate startDate,
        LocalDate endDate,
        @Size(max = 20) String status) {
}
