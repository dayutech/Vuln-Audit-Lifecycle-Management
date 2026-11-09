package com.vulnaudit.repository;

import com.vulnaudit.entity.Asset;
import jakarta.annotation.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 资产数据访问层接口
 *
 * 功能说明：
 * - 提供资产实体的CRUD操作
 * - 提供资产查询相关的自定义方法
 * - 支持复杂条件查询
 */
@Repository
public interface AssetRepository extends JpaRepository<Asset, UUID>, JpaSpecificationExecutor<Asset> {

    /**
     * 根据资产编号查询资产
     *
     * @param code 资产编号
     * @return 资产实体（如果存在）
     */
    Optional<Asset> findByCode(@Nullable String code);

    /**
     * 根据团队ID查询资产列表
     *
     * @param teamId 团队ID
     * @return 资产列表
     */
    List<Asset> findByTeamId(@Nullable UUID teamId);

    /**
     * 根据负责人ID查询资产列表
     *
     * @param ownerId 负责人ID
     * @return 资产列表
     */
    List<Asset> findByOwnerId(@Nullable UUID ownerId);

    /**
     * 根据状态查询资产列表
     *
     * @param status 状态
     * @return 资产列表
     */
    List<Asset> findByStatus(@Nullable String status);

    /**
     * 根据优先级查询资产列表
     *
     * @param priority 优先级
     * @return 资产列表
     */
    List<Asset> findByPriority(@Nullable String priority);

    /**
     * 根据团队ID和状态查询资产列表
     *
     * @param teamId 团队ID
     * @param status 状态
     * @return 资产列表
     */
    List<Asset> findByTeamIdAndStatus(@Nullable UUID teamId, @Nullable String status);

    /**
     * 检查资产编号是否已存在
     *
     * @param code 资产编号
     * @return 如果已存在返回true
     */
    boolean existsByCode(@Nullable String code);

    /**
     * 统计团队下的资产数量
     * 
     * @param teamId 团队ID
     * @return 资产数量
     */
    long countByTeamId(UUID teamId);

    /**
     * 统计指定状态的资产数量
     * 
     * @param status 状态
     * @return 资产数量
     */
    long countByStatus(String status);

    @Query("SELECT DISTINCT a.targetType FROM Asset a WHERE a.targetType IS NOT NULL AND a.targetType <> '' ORDER BY a.targetType ASC")
    List<String> findDistinctTargetTypes();

    @Query("SELECT DISTINCT a.targetSource FROM Asset a WHERE a.targetSource IS NOT NULL AND a.targetSource <> '' ORDER BY a.targetSource ASC")
    List<String> findDistinctTargetSources();

    // ------------------------------------------------------------------ 软删（t140）
    // 实体上有 @SQLRestriction("deleted_at IS NULL")，上面查询只看未删行；以下原生 SQL 有意绕过。

    @Query(value = "select * from assets where id = :id", nativeQuery = true)
    Optional<Asset> findByIdIncludingDeleted(@org.springframework.data.repository.query.Param("id") UUID id);

    /** 含已删：资产编号是否被占用（软删记录仍占唯一键） */
    @Query(value = "select count(*) from assets where code = :code", nativeQuery = true)
    long countByCodeIncludingDeleted(@org.springframework.data.repository.query.Param("code") String code);

    @org.springframework.data.jpa.repository.Modifying
    @Query(value = "update assets set deleted_at = :deletedAt, deleted_by = :deletedBy "
            + "where id = :id and deleted_at is null", nativeQuery = true)
    int softDeleteById(@org.springframework.data.repository.query.Param("id") UUID id,
                       @org.springframework.data.repository.query.Param("deletedAt") java.time.LocalDateTime deletedAt,
                       @org.springframework.data.repository.query.Param("deletedBy") UUID deletedBy);

    @org.springframework.data.jpa.repository.Modifying
    @Query(value = "update assets set deleted_at = null, deleted_by = null where id = :id and deleted_at is not null", nativeQuery = true)
    int restoreById(@org.springframework.data.repository.query.Param("id") UUID id);

    @Query(value = "select * from assets where (:includeDeleted = true or deleted_at is null) "
            + "and (:keyword = '' or lower(code) like lower(concat('%', :keyword, '%')) "
            + "or lower(name) like lower(concat('%', :keyword, '%')))",
            countQuery = "select count(*) from assets where (:includeDeleted = true or deleted_at is null) "
                    + "and (:keyword = '' or lower(code) like lower(concat('%', :keyword, '%')) "
                    + "or lower(name) like lower(concat('%', :keyword, '%')))",
            nativeQuery = true)
    org.springframework.data.domain.Page<Asset> searchAdmin(
            @org.springframework.data.repository.query.Param("keyword") String keyword,
            @org.springframework.data.repository.query.Param("includeDeleted") boolean includeDeleted,
            org.springframework.data.domain.Pageable pageable);

}
