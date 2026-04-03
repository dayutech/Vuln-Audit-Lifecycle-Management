package com.vulnaudit.repository.metadata;

import com.vulnaudit.entity.metadata.AssetMetadataAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssetMetadataAccountRepository extends JpaRepository<AssetMetadataAccount, UUID> {
    Optional<AssetMetadataAccount> findByAssetIdAndAssetVersionId(UUID assetId, UUID assetVersionId);
    Optional<AssetMetadataAccount> findByAssetIdAndAssetVersionIdIsNull(UUID assetId);
}
