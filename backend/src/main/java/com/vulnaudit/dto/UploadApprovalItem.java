package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/** 上传审批条目（t160，管理端列表 / 申请人查询共用） */
@Schema(description = "上传审批条目")
public record UploadApprovalItem(
        UUID id,
        String targetType,
        UUID targetId,
        String fileName,
        Long fileSize,
        String contentType,
        String status,
        UploadUserBrief requester,
        LocalDateTime requestedAt,
        UploadUserBrief reviewer,
        LocalDateTime reviewedAt,
        String reviewNote,
        LocalDateTime purgeAfter,
        /** 临时文件是否仍存在（拒绝/过期后为 false，便于前端与排查） */
        boolean pendingFilePresent) {

    @Schema(description = "用户摘要")
    public record UploadUserBrief(UUID id, String username) {
    }
}
