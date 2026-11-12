package com.vulnaudit.repository;

import com.vulnaudit.entity.FormDefinition;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/** 表单模板仓储（t178） */
@Repository
public interface FormDefinitionRepository extends JpaRepository<FormDefinition, UUID> {

    /** code 唯一性检查**含软删行**（编码不复用） */
    Optional<FormDefinition> findByCode(String code);

    Optional<FormDefinition> findByIdAndDeletedAtIsNull(UUID id);

    @Query("select d from FormDefinition d where d.deletedAt is null "
            + "and (:enabled is null or d.enabled = :enabled) "
            + "and (:keyword is null or lower(d.name) like :keyword or lower(d.code) like :keyword)")
    Page<FormDefinition> search(@Param("keyword") String keyword,
                                @Param("enabled") Boolean enabled,
                                Pageable pageable);
}
