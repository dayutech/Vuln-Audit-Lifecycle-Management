package com.vulnaudit.repository;

import com.vulnaudit.entity.ProjectType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** 项目类型仓储（t130） */
@Repository
public interface ProjectTypeRepository extends JpaRepository<ProjectType, UUID> {

    Optional<ProjectType> findByCode(String code);

    /** code 全局唯一（含软删行）：建新项时用它做冲突判断 */
    boolean existsByCode(String code);

    Optional<ProjectType> findByIdAndDeletedAtIsNull(UUID id);

    Optional<ProjectType> findByCodeAndDeletedAtIsNull(String code);

    long countByBuiltinTrueAndDeletedAtIsNull();

    /** 管理员列表：含停用项、排除软删；关键字匹配 code/name（大小写不敏感） */
    @Query("""
            select t from ProjectType t
             where t.deletedAt is null
               and (:includeDisabled = true or t.enabled = true)
               and (:keyword = '' or lower(t.code) like lower(concat('%', :keyword, '%'))
                    or lower(t.name) like lower(concat('%', :keyword, '%')))
             order by t.builtin desc, t.sortOrder asc, t.code asc
            """)
    Page<ProjectType> searchAdmin(@Param("keyword") String keyword,
                                 @Param("includeDisabled") boolean includeDisabled,
                                 Pageable pageable);

    /** 下拉用：仅启用且未软删，内置优先 + sort_order */
    List<ProjectType> findByEnabledTrueAndDeletedAtIsNullOrderByBuiltinDescSortOrderAscCodeAsc();
}
