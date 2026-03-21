package com.vulnaudit.repository;

import com.vulnaudit.entity.Environment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EnvironmentRepository extends JpaRepository<Environment, UUID> {

    List<Environment> findByAssetId(UUID assetId);
    List<Environment> findByCreatedBy(UUID createdBy);
    List<Environment> findByStatus(String status);

}
