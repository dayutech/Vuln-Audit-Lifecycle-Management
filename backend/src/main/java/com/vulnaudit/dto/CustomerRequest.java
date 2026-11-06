package com.vulnaudit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * 客户创建/更新请求（t68 / t129）
 *
 * @param code         客户编码；<b>为空时由服务端自动生成</b> {@code CUST-} + 7 位随机大写字母（总长 12），
 *                     显式传入时按原值保留（便于导入历史数据，校验格式与唯一性）
 * @param name         客户名称
 * @param industry     行业显示名（旧前端兼容；传 industryCode 时由服务端回填）
 * @param industryCode 行业编码（GB/T 4754，如 {@code I65}；必须存在且启用）
 * @param level        客户级别（如 key / normal）
 * @param contactName  联系人
 * @param contactPhone 联系电话
 * @param contactEmail 联系邮箱
 * @param ownerId      客户负责人（可空）
 * @param status       状态（active / inactive，创建默认 active）
 * @param description  备注
 */
public record CustomerRequest(
        @Size(max = 50) String code,
        @NotBlank(message = "客户名称不能为空") @Size(max = 200) String name,
        @Size(max = 100) String industry,
        @Size(max = 16) String industryCode,
        @Size(max = 20) String level,
        @Size(max = 100) String contactName,
        @Size(max = 50) String contactPhone,
        @Size(max = 200) String contactEmail,
        UUID ownerId,
        @Size(max = 20) String status,
        @Size(max = 1000) String description) {
}
