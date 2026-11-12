package com.vulnaudit.repository;

import com.vulnaudit.entity.FormField;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 表单字段仓储（t178） */
@Repository
public interface FormFieldRepository extends JpaRepository<FormField, UUID> {

    List<FormField> findByDefinitionIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(UUID definitionId);

    List<FormField> findByDefinitionIdAndDeletedAtIsNull(UUID definitionId);

    Optional<FormField> findByIdAndDefinitionIdAndDeletedAtIsNull(UUID id, UUID definitionId);

    long countByDefinitionIdAndDeletedAtIsNull(UUID definitionId);

    /** field_key 同模板唯一（含软删行，避免复用时撞唯一索引） */
    Optional<FormField> findByDefinitionIdAndFieldKey(UUID definitionId, String fieldKey);
}
