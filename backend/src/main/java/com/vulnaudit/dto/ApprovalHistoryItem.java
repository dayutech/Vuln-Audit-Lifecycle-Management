package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 审批历史条目（t156）
 *
 * <p>用于替换前端 `localStorage` 兜底：已通过/已拒绝的审批记录从**后端**留存并可按页拉取。
 *
 * @param assetDeleted 资产已软删（历史仍需可见，故用标志位而不是隐藏）
 * @param status       approved / rejected（历史只含已处理，pending 不在此列）
 * @param requestedAt  申请时间（= AssetAccess.createdAt）
 */
@Schema(description = "审批历史条目")
public record ApprovalHistoryItem(
        UUID id,
        UUID assetId,
        String assetCode,
        String assetName,
        boolean assetDeleted,
        ApprovalUserBrief applicant,
        String status,
        LocalDateTime requestedAt,
        LocalDateTime reviewedAt,
        ApprovalUserBrief reviewer,
        String reviewNote,
        AccessScope scope) {

    /** 申请人 / 审批人摘要（id + username，与本项目既有风格一致） */
    @Schema(description = "用户摘要")
    public record ApprovalUserBrief(UUID id, String username) {
    }

    /** 申请范围（沿用 AssetAccess 的四个字段，不另造模型） */
    @Schema(description = "申请范围")
    public record AccessScope(boolean canViewDocuments,
                              boolean canViewEnvironments,
                              boolean canViewFiles,
                              String allowedFileTypes) {
    }
}
