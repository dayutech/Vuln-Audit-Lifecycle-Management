package com.vulnaudit.repository;

import com.vulnaudit.entity.DocumentVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, UUID> {

    List<DocumentVersion> findByDocumentIdOrderByVersionDesc(UUID documentId);
    Optional<DocumentVersion> findByDocumentIdAndVersion(UUID documentId, Integer version);
    
    @Query("SELECT dv FROM DocumentVersion dv WHERE dv.documentId = :documentId ORDER BY dv.version DESC LIMIT 1")
    Optional<DocumentVersion> findLatestVersion(@Param("documentId") UUID documentId);

}
