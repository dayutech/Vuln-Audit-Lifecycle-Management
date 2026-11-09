package com.vulnaudit.repository;

import com.vulnaudit.entity.TagDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 标签定义仓储（t141）
 *
 * <p>注意：**没有** {@code @SQLRestriction} —— 标签定义需要「含已删」的 name 唯一性检查
 * （软删后同名也不可复用），因此一律显式带 {@code deletedAt} 条件，避免两套口径混用。
 */
@Repository
public interface TagDefinitionRepository extends JpaRepository<TagDefinition, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<TagDefinition> {

    Optional<TagDefinition> findByName(String name);

    /** t145：按名称批量取定义（列表内联 tags 的第二条查询） */
    List<TagDefinition> findByNameIn(java.util.Collection<String> names);

    Optional<TagDefinition> findByIdAndDeletedAtIsNull(UUID id);

    /** 含软删行的 name 占用检查（唯一索引含已删行） */
    long countByName(String name);

    boolean existsByName(String name);

    /** 下拉：未删、内置优先 + sort_order */
    List<TagDefinition> findByDeletedAtIsNullOrderByBuiltinDescSortOrderAscNameAsc();

    @Query("select t from TagDefinition t where (:includeDeleted = true or t.deletedAt is null) "
            + "and (:keyword = '' or lower(t.name) like lower(concat('%', :keyword, '%'))) "
            + "order by t.builtin desc, t.sortOrder asc, t.name asc")
    org.springframework.data.domain.Page<TagDefinition> searchAdmin(
            @org.springframework.data.repository.query.Param("keyword") String keyword,
            @org.springframework.data.repository.query.Param("includeDeleted") boolean includeDeleted,
            org.springframework.data.domain.Pageable pageable);

    long countByBuiltinTrueAndDeletedAtIsNull();
}
