package com.vulnaudit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * 客户创建/更新请求（t68）
 *
 * @param code         客户编码（创建必填，全局唯一）
 * @param name         客户名称
 * @param industry     行业
 * @param level        客户级别（如 key / normal）
 * @param contactName  联系人
 * @param contactPhone 联系电话
 * @param contactEmail 联系邮箱
 * @param ownerId      客户负责人（可空）
 * @param status       状态（active / inactive，创建默认 active）
 * @param description  备注
 */
public record CustomerRequest(
        @NotBlank(message = "客户编码不能为空") @Size(max = 50) String code,
        @NotBlank(message = "客户名称不能为空") @Size(max = 200) String name,
        @Size(max = 100) String industry,
        @Size(max = 20) String level,
        @Size(max = 100) String contactName,
        @Size(max = 50) String contactPhone,
        @Size(max = 200) String contactEmail,
        UUID ownerId,
        @Size(max = 20) String status,
        @Size(max = 1000) String description) {
}
