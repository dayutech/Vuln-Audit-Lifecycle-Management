package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/** 标签定义管理视图（t141） */
@Schema(description = "标签定义")
public record TagDefinitionVO(
        UUID id,
        String name,
        String color,
        boolean builtin,
        int sortOrder,
        LocalDateTime deletedAt) {
}
