package com.vulnaudit.repository.metadata;

import com.vulnaudit.entity.metadata.AssetMetadataEnv;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssetMetadataEnvRepository extends JpaRepository<AssetMetadataEnv, UUID> {
    Optional<AssetMetadataEnv> findByAssetIdAndAssetVersionId(UUID assetId, UUID assetVersionId);
    Optional<AssetMetadataEnv> findByAssetIdAndAssetVersionIdIsNull(UUID assetId);
}
