package com.vulnaudit.repository;

import com.vulnaudit.entity.DocumentAccess;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentAccessRepository extends JpaRepository<DocumentAccess, UUID> {

    List<DocumentAccess> findByDocumentId(UUID documentId);
    List<DocumentAccess> findByUserId(UUID userId);
    Optional<DocumentAccess> findByDocumentIdAndUserId(UUID documentId, UUID userId);
    
    void deleteByDocumentIdAndUserId(UUID documentId, UUID userId);
    boolean existsByDocumentIdAndUserIdAndAccessType(UUID documentId, UUID userId, String accessType);

}
