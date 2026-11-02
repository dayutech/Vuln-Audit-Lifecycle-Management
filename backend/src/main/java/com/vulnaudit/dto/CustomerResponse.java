package com.vulnaudit.dto;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 客户视图（t68）
 */
public record CustomerResponse(
        UUID id,
        String code,
        String name,
        String industry,
        String level,
        String contactName,
        String contactPhone,
        String contactEmail,
        UUID ownerId,
        String status,
        String description,
        UUID createdBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
