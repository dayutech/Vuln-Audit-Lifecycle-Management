package com.vulnaudit.repository;

import com.vulnaudit.entity.EnvironmentSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EnvironmentSnapshotRepository extends JpaRepository<EnvironmentSnapshot, UUID> {

    List<EnvironmentSnapshot> findByEnvironmentIdOrderByVersionDesc(UUID environmentId);
    Optional<EnvironmentSnapshot> findByEnvironmentIdAndVersion(UUID environmentId, Integer version);
    
    @Query("SELECT es FROM EnvironmentSnapshot es WHERE es.environmentId = :environmentId ORDER BY es.version DESC LIMIT 1")
    Optional<EnvironmentSnapshot> findLatestSnapshot(@Param("environmentId") UUID environmentId);

}
