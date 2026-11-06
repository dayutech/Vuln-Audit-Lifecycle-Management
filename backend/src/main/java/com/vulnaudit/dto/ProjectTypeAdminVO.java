package com.vulnaudit.dto;

import com.vulnaudit.entity.ProjectType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/** 管理员视图：项目类型（t130） */
@Schema(description = "项目类型（管理员）")
public record ProjectTypeAdminVO(
        UUID id,
        String code,
        String name,
        boolean builtin,
        boolean enabled,
        int sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static ProjectTypeAdminVO from(ProjectType type) {
        return new ProjectTypeAdminVO(type.getId(), type.getCode(), type.getName(), type.isBuiltin(),
                type.isEnabled(), type.getSortOrder(), type.getCreatedAt(), type.getUpdatedAt());
    }
}
