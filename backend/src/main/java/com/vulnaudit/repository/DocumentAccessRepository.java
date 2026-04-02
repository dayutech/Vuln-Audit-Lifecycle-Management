package com.vulnaudit.repository;

import com.vulnaudit.entity.DocumentAccess;
import jakarta.annotation.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DocumentAccessRepository extends JpaRepository<DocumentAccess, UUID> {

    List<DocumentAccess> findByDocumentId(@Nullable UUID documentId);
    List<DocumentAccess> findByUserId(@Nullable UUID userId);
    Optional<DocumentAccess> findByDocumentIdAndUserId(@Nullable UUID documentId, @Nullable UUID userId);

    void deleteByDocumentIdAndUserId(@Nullable UUID documentId, @Nullable UUID userId);
    boolean existsByDocumentIdAndUserIdAndAccessType(@Nullable UUID documentId, @Nullable UUID userId, @Nullable String accessType);

}
