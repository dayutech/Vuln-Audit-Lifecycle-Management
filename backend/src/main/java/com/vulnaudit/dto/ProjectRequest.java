package com.vulnaudit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 项目创建/更新请求（t68）
 *
 * @param code        项目编码（创建必填，全局唯一）
 * @param customerId  所属客户（必须存在）
 * @param name        项目名称
 * @param type        项目类型（PENTEST / REAUDIT 等）
 * @param managerId   项目经理（必填且必须存在；DB projects.manager_id 为 NOT NULL，t80 起由 DTO 兜底为 400）
 * @param startDate   开始日期
 * @param endDate     结束日期
 * @param isPhased    是否分期
 * @param contractNo  合同号
 * @param description 备注
 */
public record ProjectRequest(
        @NotBlank(message = "项目编码不能为空") @Size(max = 50) String code,
        @NotNull(message = "所属客户不能为空") UUID customerId,
        @NotBlank(message = "项目名称不能为空") @Size(max = 200) String name,
        @Size(max = 30) String type,
        @NotNull(message = "项目经理不能为空") UUID managerId,
        LocalDate startDate,
        LocalDate endDate,
        Boolean isPhased,
        @Size(max = 100) String contractNo,
        @Size(max = 1000) String description) {
}
