package com.vulnaudit.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 期次视图（t68）
 */
public record PhaseResponse(
        UUID id,
        UUID projectId,
        Integer phaseNo,
        String name,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
