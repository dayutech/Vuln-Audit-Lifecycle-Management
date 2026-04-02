package com.vulnaudit.repository;

import com.vulnaudit.entity.Environment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EnvironmentRepository extends JpaRepository<Environment, UUID> {

    List<Environment> findByAssetId(UUID assetId);
    Page<Environment> findByAssetId(UUID assetId, Pageable pageable);
    Page<Environment> findByAssetIdAndAssetVersionId(UUID assetId, UUID assetVersionId, Pageable pageable);
    List<Environment> findByCreatedBy(UUID createdBy);
    List<Environment> findByStatus(String status);

}
