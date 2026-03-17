package com.vulnaudit.repository;

import com.vulnaudit.entity.AssetEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface AssetRepository extends JpaRepository<AssetEntity, UUID> {
    Optional<AssetEntity> findByCode(String code);

    Page<AssetEntity> findByStatus(AssetEntity.AssetStatus status, Pageable pageable);

    Page<AssetEntity> findByTeamId(UUID teamId, Pageable pageable);

    @Query("SELECT a FROM AssetEntity a WHERE a.name LIKE %:keyword% OR a.description LIKE %:keyword%")
    Page<AssetEntity> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
}
