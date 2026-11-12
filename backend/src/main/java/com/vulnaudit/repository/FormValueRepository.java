package com.vulnaudit.repository;

import com.vulnaudit.entity.FormValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** 表单字段值仓储（t178/t179） */
@Repository
public interface FormValueRepository extends JpaRepository<FormValue, UUID> {

    /** 字段是否已有提交数据（用于「禁止改类型/删除」判断） */
    long countByFieldId(UUID fieldId);

    List<FormValue> findBySubmissionId(UUID submissionId);

    /** t181：批量取多个实例的值（导出用） */
    List<FormValue> findBySubmissionIdIn(Collection<UUID> submissionIds);

    /** t179：批量统计每个实例「已填写」的字段数（列表页进度 N/M，避免 N+1） */
    @Query("select v.submissionId, count(v) from FormValue v where v.submissionId in :ids "
            + "and v.filledValue is not null and v.filledValue <> '' group by v.submissionId")
    List<Object[]> countFilledBySubmissionIds(@Param("ids") Collection<UUID> ids);
}
