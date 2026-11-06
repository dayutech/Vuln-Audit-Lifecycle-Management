package com.vulnaudit.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 项目视图（t68；t130 增加 {@code typeCode}）
 */
public record ProjectResponse(
        UUID id,
        String code,
        UUID customerId,
        String customerName,
        String name,
        String type,
        /** t130：项目类型引用（{@code project_types.code}，可空） */
        String typeCode,
        String status,
        UUID managerId,
        String managerName,
        LocalDate startDate,
        LocalDate endDate,
        Boolean isPhased,
        String contractNo,
        String description,
        UUID createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
