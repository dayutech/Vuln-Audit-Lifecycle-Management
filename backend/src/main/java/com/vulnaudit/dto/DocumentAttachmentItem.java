package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/** 知识库/文档附件元数据（t162） */
@Schema(description = "附件元数据")
public record DocumentAttachmentItem(
        UUID id,
        String ownerType,
        UUID ownerId,
        String fileName,
        Long fileSize,
        String contentType,
        LocalDateTime uploadedAt,
        UUID uploadedBy,
        String uploaderName) {
}
