package com.vulnaudit.dto;

import com.vulnaudit.entity.IndustryType;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 管理员视图：行业类型（t130）
 *
 * <p>比下拉用的 {@link IndustryTypeOption} 多出 id / enabled / sortOrder / 时间戳，
 * 供管理端表格渲染与编辑回填。
 */
@Schema(description = "行业类型（管理员）")
public record IndustryTypeAdminVO(
        UUID id,
        String code,
        String name,
        String parentCode,
        boolean builtin,
        boolean enabled,
        int sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static IndustryTypeAdminVO from(IndustryType type) {
        return new IndustryTypeAdminVO(type.getId(), type.getCode(), type.getName(), type.getParentCode(),
                type.isBuiltin(), type.isEnabled(), type.getSortOrder(), type.getCreatedAt(), type.getUpdatedAt());
    }
}
