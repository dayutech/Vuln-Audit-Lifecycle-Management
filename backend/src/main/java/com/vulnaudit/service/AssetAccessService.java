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
}