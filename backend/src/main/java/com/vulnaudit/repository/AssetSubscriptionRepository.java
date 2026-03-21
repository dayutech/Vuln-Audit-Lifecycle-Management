package com.vulnaudit.repository;

import com.vulnaudit.entity.AssetSubscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 资产订阅Repository接口
 */
@Repository
public interface AssetSubscriptionRepository extends JpaRepository<AssetSubscription, UUID> {

    /**
     * 按用户ID查询订阅列表
     */
    List<AssetSubscription> findByUserId(UUID userId);

    /**
     * 按资产ID查询订阅列表
     */
    List<AssetSubscription> findByAssetId(UUID assetId);

    /**
     * 按用户ID和资产ID查询
     */
    Optional<AssetSubscription> findByUserIdAndAssetId(UUID userId, UUID assetId);

    /**
     * 检查是否已订阅
     */
    boolean existsByUserIdAndAssetId(UUID userId, UUID assetId);

    /**
     * 删除订阅
     */
    void deleteByUserIdAndAssetId(UUID userId, UUID assetId);
}
