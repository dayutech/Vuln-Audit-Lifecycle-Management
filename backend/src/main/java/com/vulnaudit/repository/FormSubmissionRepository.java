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

    /** t180：待我处理（assignedTo = 我，含 RETURNED），按 createdAt 倒序 */
    @Query("select s from FormSubmission s where s.assignedTo = :userId "
            + "and (:status is null or s.status = :status)")
    Page<FormSubmission> findAssigned(@Param("userId") UUID userId, @Param("status") String status,
                                      Pageable pageable);
}
