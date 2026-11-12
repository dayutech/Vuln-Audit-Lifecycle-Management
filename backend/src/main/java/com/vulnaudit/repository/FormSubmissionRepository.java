package com.vulnaudit.repository;

import com.vulnaudit.entity.FormSubmission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** 表单实例仓储（t178/t179） */
@Repository
public interface FormSubmissionRepository extends JpaRepository<FormSubmission, UUID> {

    long countByDefinitionId(UUID definitionId);

    /** t179：我发起的（status 为 null 表示全部），按 createdAt 倒序 */
    @Query("select s from FormSubmission s where s.createdBy = :userId "
            + "and (:status is null or s.status = :status)")
    Page<FormSubmission> findMine(@Param("userId") UUID userId, @Param("status") String status, Pageable pageable);

    /**
     * t181：导出检索（createdAt 闭区间；status/definitionId 可空）
     *
     * <p>`scopeToUser=true` 时只返回「与我相关」的（我发起/指派给我/我确认），管理层传 false 取全部。
     */
    @Query("select s from FormSubmission s where (:definitionId is null or s.definitionId = :definitionId) "
            + "and (:status is null or s.status = :status) "
            + "and (:fromTime is null or s.createdAt >= :fromTime) "
            + "and (:toTime is null or s.createdAt <= :toTime) "
            + "and (:scopeToUser = false or s.createdBy = :userId or s.assignedTo = :userId "
            + "or s.confirmedBy = :userId) "
            + "order by s.createdAt asc")
    Page<FormSubmission> searchForExport(@Param("definitionId") UUID definitionId,
                                         @Param("status") String status,
                                         @Param("fromTime") java.time.LocalDateTime fromTime,
                                         @Param("toTime") java.time.LocalDateTime toTime,
                                         @Param("scopeToUser") boolean scopeToUser,
                                         @Param("userId") UUID userId,
                                         Pageable pageable);

    /** t180：待我处理（assignedTo = 我，含 RETURNED），按 createdAt 倒序 */
    @Query("select s from FormSubmission s where s.assignedTo = :userId "
            + "and (:status is null or s.status = :status)")
    Page<FormSubmission> findAssigned(@Param("userId") UUID userId, @Param("status") String status,
                                      Pageable pageable);
}
