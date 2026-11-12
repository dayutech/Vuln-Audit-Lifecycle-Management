package com.vulnaudit.repository;

import com.vulnaudit.entity.FormField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 表单字段仓储（t178/t179）
 *
 * <p>**模板字段 = `submission_id IS NULL`**；实例级字段带 `submission_id`，模板侧一律排除
 * （否则管理员会在模板详情里看到别人实例里的临时字段）。
 */
@Repository
public interface FormFieldRepository extends JpaRepository<FormField, UUID> {

    /** 模板字段（只含 submission_id IS NULL） */
    List<FormField> findByDefinitionIdAndSubmissionIdIsNullAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(
            UUID definitionId);

    List<FormField> findByDefinitionIdAndSubmissionIdIsNullAndDeletedAtIsNull(UUID definitionId);

    Optional<FormField> findByIdAndDefinitionIdAndDeletedAtIsNull(UUID id, UUID definitionId);

    /** 模板字段按 id 取（排除实例级字段：管理员不能改别人实例里的临时字段） */
    Optional<FormField> findByIdAndDefinitionIdAndSubmissionIdIsNullAndDeletedAtIsNull(UUID id, UUID definitionId);

    long countByDefinitionIdAndSubmissionIdIsNullAndDeletedAtIsNull(UUID definitionId);

    /** field_key 同模板唯一（含软删行，避免复用时撞唯一索引；只在模板字段里比） */
    Optional<FormField> findByDefinitionIdAndSubmissionIdIsNullAndFieldKey(UUID definitionId, String fieldKey);

    // ------------------------------------------------------------------ t179 实例级字段

    /** 某实例的全部字段（模板字段 + 本实例字段），按 sort_order */
    @Query("select f from FormField f where f.deletedAt is null and (f.submissionId = :submissionId "
            + "or (f.submissionId is null and f.definitionId = :definitionId)) "
            + "order by f.sortOrder asc, f.createdAt asc")
    List<FormField> findFieldsForSubmission(@Param("submissionId") UUID submissionId,
                                           @Param("definitionId") UUID definitionId);

    Optional<FormField> findByIdAndSubmissionIdAndDeletedAtIsNull(UUID id, UUID submissionId);

    long countBySubmissionIdAndDeletedAtIsNull(UUID submissionId);

    /** 实例级 field_key 生成用：该实例已用的 key 数 */
    long countBySubmissionId(UUID submissionId);

    /** t181：批量取多个模板的模板字段（导出用，避免逐模板查询） */
    List<FormField> findByDefinitionIdInAndSubmissionIdIsNullAndDeletedAtIsNullOrderBySortOrderAsc(
            Collection<UUID> definitionIds);

    /** t181：批量取多个实例的实例级字段（导出用） */
    List<FormField> findBySubmissionIdInAndDeletedAtIsNullOrderBySortOrderAsc(Collection<UUID> submissionIds);

    /** t179：批量统计模板字段数（列表页 fieldCount，避免 N+1） */
    @Query("select f.definitionId, count(f) from FormField f where f.deletedAt is null "
            + "and f.submissionId is null and f.definitionId in :ids group by f.definitionId")
    List<Object[]> countTemplateFieldsByDefinitionIds(@Param("ids") Collection<UUID> ids);

    /** t179：批量统计实例级字段数 */
    @Query("select f.submissionId, count(f) from FormField f where f.deletedAt is null "
            + "and f.submissionId in :ids group by f.submissionId")
    List<Object[]> countInstanceFieldsBySubmissionIds(@Param("ids") Collection<UUID> ids);
}
