package com.vulnaudit.repository;

import com.vulnaudit.entity.AssetVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssetVersionRepository extends JpaRepository<AssetVersion, UUID> {

    List<AssetVersion> findByAssetIdOrderByVersionNoDesc(UUID assetId);

    Optional<AssetVersion> findByAssetIdAndVersionNo(UUID assetId, Integer versionNo);

    Optional<AssetVersion> findByAssetIdAndIsDefaultTrue(UUID assetId);

    Optional<AssetVersion> findFirstByAssetIdOrderByVersionNoDesc(UUID assetId);

    Optional<AssetVersion> findByIdAndAssetId(UUID id, UUID assetId);

    boolean existsByAssetIdAndVersionNo(UUID assetId, Integer versionNo);

    @Modifying
    @Query("UPDATE AssetVersion av SET av.isDefault = false WHERE av.assetId = :assetId")
    int clearDefaultByAssetId(@Param("assetId") UUID assetId);

    @Modifying
    @Query("UPDATE AssetVersion av SET av.isLatest = false WHERE av.assetId = :assetId")
    int clearLatestByAssetId(@Param("assetId") UUID assetId);
}
