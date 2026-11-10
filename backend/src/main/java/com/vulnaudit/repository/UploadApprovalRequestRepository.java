package com.vulnaudit.repository;

import com.vulnaudit.entity.UploadApprovalRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 大文件上传审批仓储（t160） */
@Repository
public interface UploadApprovalRequestRepository extends JpaRepository<UploadApprovalRequest, UUID> {

    Page<UploadApprovalRequest> findByStatus(String status, Pageable pageable);

    Page<UploadApprovalRequest> findAll(Pageable pageable);

    Optional<UploadApprovalRequest> findByIdAndRequestedBy(UUID id, UUID requestedBy);

    /** 过期清理：PENDING 且 purge_after 已过 */
    @Query("select r from UploadApprovalRequest r where r.status = 'PENDING' and r.purgeAfter is not null "
            + "and r.purgeAfter < :now")
    List<UploadApprovalRequest> findExpiredPending(@Param("now") LocalDateTime now);

    long countByStatus(String status);
}
