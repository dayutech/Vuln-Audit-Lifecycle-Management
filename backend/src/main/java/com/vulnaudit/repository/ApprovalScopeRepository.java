package com.vulnaudit.repository;

import com.vulnaudit.entity.ApprovalScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 审批范围Repository接口
 */
@Repository
public interface ApprovalScopeRepository extends JpaRepository<ApprovalScope, UUID> {

    /**
     * 按审批者ID查询
     */
    List<ApprovalScope> findByApproverId(UUID approverId);

    /**
     * 按审批者ID和范围类型查询
     */
    List<ApprovalScope> findByApproverIdAndScopeType(UUID approverId, String scopeType);

    /**
     * 查询审批者的有效审批范围
     */
    @Query("SELECT a FROM ApprovalScope a WHERE a.approverId = :approverId AND (a.expiresAt IS NULL OR a.expiresAt > :now)")
    List<ApprovalScope> findValidScopes(UUID approverId, LocalDateTime now);

    /**
     * 检查审批者是否有指定范围的权限
     */
    @Query("SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END FROM ApprovalScope a WHERE a.approverId = :approverId AND a.scopeType = :scopeType AND a.scopeId = :scopeId AND (a.expiresAt IS NULL OR a.expiresAt > :now)")
    boolean hasScope(UUID approverId, String scopeType, UUID scopeId, LocalDateTime now);

    /**
     * 删除审批者的所有范围
     */
    void deleteByApproverId(UUID approverId);
}
