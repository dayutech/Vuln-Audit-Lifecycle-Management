package com.vulnaudit.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 项目视图（t68）
 */
public record ProjectResponse(
        UUID id,
        String code,
        UUID customerId,
        String customerName,
        String name,
        String type,
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
