package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/** 大文件上传提交结果（t160） */
@Schema(description = "上传审批提交结果")
public record UploadPendingResponse(
        UUID requestId,
        /** PENDING（超门限，等审批）/ DIRECT（未超门限，已直传落库） */
        String status,
        String targetType,
        /** 直传时为已创建的业务实体 id；PENDING 时为 null */
        UUID targetId,
        String fileName,
        Long fileSize,
        LocalDateTime purgeAfter) {
}
