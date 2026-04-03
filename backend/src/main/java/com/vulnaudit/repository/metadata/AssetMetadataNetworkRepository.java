package com.vulnaudit.repository.metadata;

import com.vulnaudit.entity.metadata.AssetMetadataNetwork;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssetMetadataNetworkRepository extends JpaRepository<AssetMetadataNetwork, UUID> {
    Optional<AssetMetadataNetwork> findByAssetIdAndAssetVersionId(UUID assetId, UUID assetVersionId);
    Optional<AssetMetadataNetwork> findByAssetIdAndAssetVersionIdIsNull(UUID assetId);
}
