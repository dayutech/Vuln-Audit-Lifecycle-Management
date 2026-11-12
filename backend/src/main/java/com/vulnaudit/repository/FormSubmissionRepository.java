package com.vulnaudit.repository;

import com.vulnaudit.entity.FormSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/** 表单实例仓储（t178，第 1/5 块只用到数量统计） */
@Repository
public interface FormSubmissionRepository extends JpaRepository<FormSubmission, UUID> {

    long countByDefinitionId(UUID definitionId);
}
