package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/** 拒绝上传请求（t160） */
@Schema(description = "拒绝上传请求")
public record RejectUploadRequest(
        @Schema(description = "拒绝理由（可选，≤500）") @Size(max = 500) String note) {
}
