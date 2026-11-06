package com.vulnaudit.repository;

import com.vulnaudit.entity.IndustryType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/** 行业类型仓储（t129） */
@Repository
public interface IndustryTypeRepository extends JpaRepository<IndustryType, java.util.UUID> {

    Optional<IndustryType> findByCode(String code);

    Optional<IndustryType> findFirstByName(String name);

    boolean existsByCode(String code);

    long countByBuiltinTrue();

    /**
     * 关键字检索：code / name 模糊匹配（大小写不敏感），仅 enabled
     *
     * <p>排序：内置优先、sort_order、code；同名前缀命中优先由调用方排序。
     */
    @Query("""
            select t from IndustryType t
             where t.enabled = true
               and (:keyword = '' or lower(t.code) like lower(concat('%', :keyword, '%'))
                    or lower(t.name) like lower(concat('%', :keyword, '%')))
             order by case when lower(t.name) like lower(concat(:keyword, '%')) then 0 else 1 end asc,
                      t.sortOrder asc, t.code asc
            """)
    List<IndustryType> search(@Param("keyword") String keyword, Pageable pageable);

    List<IndustryType> findByEnabledTrueOrderBySortOrderAscCodeAsc();
}
