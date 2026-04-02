package com.vulnaudit.service;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.AssetClaim;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 资产认领服务接口
 *
 * 功能说明：
 * - 定义资产认领的业务逻辑接口
 * - 提供认领CRUD操作
 * - 提供认领状态管理
 */
public interface AssetClaimService {

    @Nonnull
    AssetClaim createClaim(@Nonnull AssetClaim assetClaim);

    Optional<AssetClaim> findById(@Nullable UUID id);

    @Nonnull
    PageResponse<AssetClaim> findAll(int page, int size, @Nullable UUID assetId, @Nullable UUID userId, @Nullable String status);

    @Nonnull
    List<AssetClaim> findByAssetId(@Nullable UUID assetId);

    @Nonnull
    List<AssetClaim> findByUserId(@Nullable UUID userId);

    @Nonnull
    AssetClaim reviewClaim(@Nullable UUID id, boolean approved, @Nullable String reviewNote, @Nullable UUID reviewerId);

    void completeClaim(@Nullable UUID id, @Nullable UUID userId);

    void abandonClaim(@Nullable UUID id, @Nullable UUID userId, @Nullable String note);

    void revokeClaim(@Nullable UUID id, @Nullable UUID userId);

    boolean hasActiveClaim(@Nullable UUID assetId);

    Optional<AssetClaim> getActiveClaim(@Nullable UUID assetId);

    boolean hasActiveClaimByUser(@Nullable UUID assetId, @Nullable UUID userId);

    long countByUserIdAndStatus(@Nullable UUID userId, @Nullable String status);
}