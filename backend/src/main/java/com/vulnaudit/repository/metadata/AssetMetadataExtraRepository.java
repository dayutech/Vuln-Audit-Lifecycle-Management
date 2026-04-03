package com.vulnaudit.repository.metadata;

import com.vulnaudit.entity.metadata.AssetMetadataExtra;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssetMetadataExtraRepository extends JpaRepository<AssetMetadataExtra, UUID> {
    Optional<AssetMetadataExtra> findByAssetIdAndAssetVersionId(UUID assetId, UUID assetVersionId);
    Optional<AssetMetadataExtra> findByAssetIdAndAssetVersionIdIsNull(UUID assetId);
}
