package com.vulnaudit.repository;

import com.vulnaudit.entity.AssetDeliverable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AssetDeliverableRepository extends JpaRepository<AssetDeliverable, UUID> {

    List<AssetDeliverable> findByAssetIdAndAssetVersionIdOrderByCreatedAtDesc(UUID assetId, UUID assetVersionId);

    List<AssetDeliverable> findByAssetIdAndAssetVersionIdAndStatusOrderByCreatedAtDesc(UUID assetId, UUID assetVersionId, String status);
}
