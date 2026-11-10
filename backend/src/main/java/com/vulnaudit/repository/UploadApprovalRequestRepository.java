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

    /**
     * t166：我的上传（只含 requested_by = 我），status 为 null 表示全部
     *
     * <p>keyword 匹配 fileName 或 displayName（大小写不敏感）；非法 status 在 service 层白名单拦掉。
     */
    @Query("select r from UploadApprovalRequest r where r.requestedBy = :userId "
            + "and (:status is null or r.status = :status) "
            + "and (:keyword is null or lower(r.fileName) like :keyword or lower(r.displayName) like :keyword)")
    Page<UploadApprovalRequest> findMine(@Param("userId") UUID userId,
                                         @Param("status") String status,
                                         @Param("keyword") String keyword,
                                         Pageable pageable);

    /** t166：管理端列表增加 keyword（fileName/displayName）与 requestedBy 过滤 */
    @Query("select r from UploadApprovalRequest r where "
            + "(:status is null or r.status = :status) "
            + "and (:requestedBy is null or r.requestedBy = :requestedBy) "
            + "and (:keyword is null or lower(r.fileName) like :keyword or lower(r.displayName) like :keyword)")
    Page<UploadApprovalRequest> searchAdmin(@Param("status") String status,
                                            @Param("requestedBy") UUID requestedBy,
                                            @Param("keyword") String keyword,
                                            Pageable pageable);

    Page<UploadApprovalRequest> findAll(Pageable pageable);

    Optional<UploadApprovalRequest> findByIdAndRequestedBy(UUID id, UUID requestedBy);

    /** 过期清理：PENDING 且 purge_after 已过 */
    @Query("select r from UploadApprovalRequest r where r.status = 'PENDING' and r.purgeAfter is not null "
            + "and r.purgeAfter < :now")
    List<UploadApprovalRequest> findExpiredPending(@Param("now") LocalDateTime now);

    long countByStatus(String status);
}
