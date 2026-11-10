package com.vulnaudit.service;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.AssetAccess;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 资产访问服务接口
 *
 * 功能说明：
 * - 定义资产访问申请的业务逻辑接口
 * - 提供访问申请CRUD操作
 * - 提供访问申请审批功能
 */
public interface AssetAccessService {

    @Nonnull
    AssetAccess createAccessRequest(@Nonnull AssetAccess assetAccess);

    Optional<AssetAccess> findById(@Nullable UUID id);

    @Nonnull
    PageResponse<AssetAccess> findAll(int page, int size, @Nullable UUID assetId, @Nullable UUID userId, @Nullable String status);

    @Nonnull
    List<AssetAccess> findByAssetId(@Nullable UUID assetId);

    @Nonnull
    List<AssetAccess> findByUserId(@Nullable UUID userId);

    @Nonnull
    AssetAccess reviewAccessRequest(@Nullable UUID id, boolean approved, @Nullable String reviewNote, @Nullable UUID reviewerId);

    void revokeAccessRequest(@Nullable UUID id, @Nullable UUID userId);

    boolean hasApprovedAccess(@Nullable UUID assetId, @Nullable UUID userId);

    Optional<AssetAccess> getApprovedAccess(@Nullable UUID assetId, @Nullable UUID userId);

    long countPendingByUser(@Nullable UUID userId);

    @Nonnull
    PageResponse<AssetAccess> findPendingApprovalByUser(@Nullable UUID userId, int page, int size);

    // ------------------------------------------------------------------ 审批历史（t156）

    /**
     * 我的审批历史（只含已处理：approved / rejected）
     *
     * @param asReviewer true = 我审批过的（reviewedBy = 我）；false = 我申请且已被处理的（userId = 我）
     * @param status     approved / rejected / all（null 视为 all）
     * @param keyword    资产名称/编号模糊（含已软删资产）
     */
    PageResponse<com.vulnaudit.dto.ApprovalHistoryItem> findApprovalHistory(@Nullable UUID userId,
                                                                            boolean asReviewer,
                                                                            @Nullable String status,
                                                                            @Nullable UUID assetId,
                                                                            @Nullable String keyword,
                                                                            int page, int size);

    /** 我的审批历史计数（前端角标用） */
    com.vulnaudit.dto.ApprovalHistoryCount countApprovalHistory(@Nullable UUID userId, boolean asReviewer);
}