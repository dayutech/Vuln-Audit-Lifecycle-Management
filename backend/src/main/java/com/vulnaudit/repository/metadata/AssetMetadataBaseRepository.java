package com.vulnaudit.repository.metadata;

import com.vulnaudit.entity.metadata.AssetMetadataBase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssetMetadataBaseRepository extends JpaRepository<AssetMetadataBase, UUID> {
    Optional<AssetMetadataBase> findByAssetIdAndAssetVersionId(UUID assetId, UUID assetVersionId);
    Optional<AssetMetadataBase> findByAssetIdAndAssetVersionIdIsNull(UUID assetId);
}
