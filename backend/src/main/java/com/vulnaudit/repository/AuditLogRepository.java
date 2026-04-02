package com.vulnaudit.repository;

import com.vulnaudit.entity.AuditLog;
import jakarta.annotation.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>, JpaSpecificationExecutor<AuditLog> {

    List<AuditLog> findByUserId(@Nullable UUID userId);
    Page<AuditLog> findByUserId(@Nullable UUID userId, Pageable pageable);
    List<AuditLog> findByAction(@Nullable String action);
    List<AuditLog> findByResourceType(@Nullable String resourceType);
    List<AuditLog> findByResourceId(@Nullable UUID resourceId);
    Page<AuditLog> findByResourceTypeAndResourceId(@Nullable String resourceType, @Nullable UUID resourceId, Pageable pageable);

    @Query("SELECT al FROM AuditLog al WHERE al.createdAt BETWEEN :startTime AND :endTime ORDER BY al.createdAt DESC")
    List<AuditLog> findByTimeRange(@Param("startTime") LocalDateTime startTime, @Param("endTime") LocalDateTime endTime);

    @Modifying
    @Query("DELETE FROM AuditLog al WHERE al.createdAt < :createdAt")
    int deleteByCreatedAtBefore(@Param("createdAt") LocalDateTime createdAt);

}
