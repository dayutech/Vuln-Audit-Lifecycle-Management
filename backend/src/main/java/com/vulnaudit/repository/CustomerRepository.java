package com.vulnaudit.repository;

import com.vulnaudit.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 客户仓储（t67）
 */
@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Customer> {

    Optional<Customer> findByCode(String code);

    boolean existsByCode(String code);

    /** 引用某行业编码的客户数（t130：删除行业前的占用检查） */
    long countByIndustryCode(String industryCode);

    List<Customer> findByStatus(String status);

    List<Customer> findByOwnerId(UUID ownerId);

    // ------------------------------------------------------------------ 软删（t140）
    // 注意：实体上有 @SQLRestriction("deleted_at IS NULL")，因此上面所有派生查询都只看未删行；
    // 下面这些**原生 SQL** 有意绕过该限制，用于「含已删」的管理端列表、恢复与查重。

    /** 含已删：按 id 取（恢复前确认存在） */
    @org.springframework.data.jpa.repository.Query(
            value = "select * from customers where id = :id", nativeQuery = true)
    Optional<Customer> findByIdIncludingDeleted(@org.springframework.data.repository.query.Param("id") UUID id);

    /** 含已删：code 是否已被占用（软删记录仍占唯一键，返回 true 即 409） */
    @org.springframework.data.jpa.repository.Query(
            value = "select count(*) from customers where code = :code", nativeQuery = true)
    long countByCodeIncludingDeleted(@org.springframework.data.repository.query.Param("code") String code);

    /** 软删（幂等：已删行不再改写时间/人） */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            value = "update customers set deleted_at = :deletedAt, deleted_by = :deletedBy "
                    + "where id = :id and deleted_at is null", nativeQuery = true)
    int softDeleteById(@org.springframework.data.repository.query.Param("id") UUID id,
                       @org.springframework.data.repository.query.Param("deletedAt") java.time.LocalDateTime deletedAt,
                       @org.springframework.data.repository.query.Param("deletedBy") UUID deletedBy);

    /** 恢复（幂等：未删时影响 0 行） */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(
            value = "update customers set deleted_at = null, deleted_by = null where id = :id and deleted_at is not null", nativeQuery = true)
    int restoreById(@org.springframework.data.repository.query.Param("id") UUID id);

    /** 管理端列表（含已删），keyword 匹配 code/name */
    @org.springframework.data.jpa.repository.Query(
            value = "select * from customers where (:includeDeleted = true or deleted_at is null) "
                    + "and (:keyword = '' or lower(code) like lower(concat('%', :keyword, '%')) "
                    + "or lower(name) like lower(concat('%', :keyword, '%')))",
            countQuery = "select count(*) from customers where (:includeDeleted = true or deleted_at is null) "
                    + "and (:keyword = '' or lower(code) like lower(concat('%', :keyword, '%')) "
                    + "or lower(name) like lower(concat('%', :keyword, '%')))",
            nativeQuery = true)
    org.springframework.data.domain.Page<Customer> searchAdmin(
            @org.springframework.data.repository.query.Param("keyword") String keyword,
            @org.springframework.data.repository.query.Param("includeDeleted") boolean includeDeleted,
            org.springframework.data.domain.Pageable pageable);
}

