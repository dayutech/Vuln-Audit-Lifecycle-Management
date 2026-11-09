package com.vulnaudit.repository;

import com.vulnaudit.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 项目仓储（t67）
 */
@Repository
public interface ProjectRepository extends JpaRepository<Project, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Project> {

    Optional<Project> findByCode(String code);

    boolean existsByCode(String code);

    /** 引用某项目类型的项目数（t130：删除项目类型前的占用检查） */
    long countByTypeCode(String typeCode);

    /** 某客户下的全部项目 */
    List<Project> findByCustomerId(UUID customerId);

    /** 某人负责（项目经理）的全部项目 */
    List<Project> findByManagerId(UUID managerId);

    List<Project> findByCustomerIdAndStatus(UUID customerId, String status);

    List<Project> findByStatus(String status);

    // ------------------------------------------------------------------ 软删（t140）
    // 实体上有 @SQLRestriction("deleted_at IS NULL")，上面派生查询只看未删行；以下原生 SQL 有意绕过。

    @org.springframework.data.jpa.repository.Query(
            value = "select * from projects where id = :id", nativeQuery = true)
    Optional<Project> findByIdIncludingDeleted(@org.springframework.data.repository.query.Param("id") UUID id);

    /** 含已删：code 是否被占用（软删记录仍占唯一键） */
    @org.springframework.data.jpa.repository.Query(
            value = "select count(*) from projects where code = :code", nativeQuery = true)
    long countByCodeIncludingDeleted(@org.springframework.data.repository.query.Param("code") String code);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            value = "update projects set deleted_at = :deletedAt, deleted_by = :deletedBy "
                    + "where id = :id and deleted_at is null", nativeQuery = true)
    int softDeleteById(@org.springframework.data.repository.query.Param("id") UUID id,
                       @org.springframework.data.repository.query.Param("deletedAt") java.time.LocalDateTime deletedAt,
                       @org.springframework.data.repository.query.Param("deletedBy") UUID deletedBy);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            value = "update projects set deleted_at = null, deleted_by = null where id = :id and deleted_at is not null", nativeQuery = true)
    int restoreById(@org.springframework.data.repository.query.Param("id") UUID id);

    @org.springframework.data.jpa.repository.Query(
            value = "select * from projects where (:includeDeleted = true or deleted_at is null) "
                    + "and (:keyword = '' or lower(code) like lower(concat('%', :keyword, '%')) "
                    + "or lower(name) like lower(concat('%', :keyword, '%')))",
            countQuery = "select count(*) from projects where (:includeDeleted = true or deleted_at is null) "
                    + "and (:keyword = '' or lower(code) like lower(concat('%', :keyword, '%')) "
                    + "or lower(name) like lower(concat('%', :keyword, '%')))",
            nativeQuery = true)
    org.springframework.data.domain.Page<Project> searchAdmin(
            @org.springframework.data.repository.query.Param("keyword") String keyword,
            @org.springframework.data.repository.query.Param("includeDeleted") boolean includeDeleted,
            org.springframework.data.domain.Pageable pageable);
}

