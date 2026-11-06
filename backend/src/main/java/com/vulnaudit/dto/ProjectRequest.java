package com.vulnaudit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/**
 * 项目创建/更新请求（t68）
 *
 * @param code        项目编码；<b>为空时由服务端自动生成</b> {@code PRJ-} + {@code yyyy-MM-dd} +
 *                    {@code -} + 6 位随机大写字母（例 {@code PRJ-2026-11-06-KXQZAB}，总长 21），
 *                    显式传入时原样保留（便于导入历史数据，校验格式与唯一性）；更新时为空则保留原编码
 * @param typeCode    项目类型引用（t130，{@code project_types.code}）；空则按 {@code type} 尽力回填
 * @param projectCode 项目编码别名（t130，与 {@code code} 等价，二者都空时自动生成）
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
        @Size(max = 50) String code,
        @NotNull(message = "所属客户不能为空") UUID customerId,
        @NotBlank(message = "项目名称不能为空") @Size(max = 200) String name,
        @Size(max = 30) String type,
        @NotNull(message = "项目经理不能为空") UUID managerId,
        LocalDate startDate,
        LocalDate endDate,
        Boolean isPhased,
        @Size(max = 100) String contractNo,
        @Size(max = 1000) String description,
        /** t130：项目类型引用（`project_types.code`），可空；传入时校验存在且启用（400） */
        @Size(max = 32) String typeCode,
        /** t130：项目编码别名；与 `code` 等价，二者都空时自动生成 */
        @Size(max = 50) String projectCode) {
}
