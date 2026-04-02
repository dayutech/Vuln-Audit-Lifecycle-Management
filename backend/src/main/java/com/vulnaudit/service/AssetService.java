package com.vulnaudit.service;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.Asset;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 资产服务接口
 *
 * 功能说明：
 * - 定义资产管理的业务逻辑接口
 * - 提供资产CRUD操作
 * - 提供资产分组管理
 * - 提供资产搜索功能
 */
public interface AssetService {

    /**
     * 创建资产
     *
     * @param asset 资产实体
     * @return 创建后的资产实体
     */
    @Nonnull
    Asset createAsset(@Nonnull Asset asset);

    /**
     * 根据ID查询资产
     *
     * @param id 资产ID
     * @return 资产实体
     */
    Optional<Asset> findById(@Nullable UUID id);

    /**
     * 根据资产编号查询资产
     *
     * @param code 资产编号
     * @return 资产实体
     */
    Optional<Asset> findByCode(@Nullable String code);

    /**
     * 分页查询资产列表
     *
     * @param page 页码（从0开始）
     * @param size 每页大小
     * @param keyword 搜索关键词（可选）
     * @param status 资产状态（可选）
     * @param teamId 团队ID（可选）
     * @param priority 优先级（可选）
     * @return 分页资产列表
     */
    @Nonnull
    PageResponse<Asset> findAll(int page, int size, @Nullable String keyword, @Nullable String status, @Nullable UUID teamId, @Nullable String priority);

    /**
     * 根据团队ID查询资产列表
     *
     * @param teamId 团队ID
     * @return 资产列表
     */
    @Nonnull
    List<Asset> findByTeamId(@Nullable UUID teamId);

    /**
     * 根据负责人ID查询资产列表
     *
     * @param ownerId 负责人ID
     * @return 资产列表
     */
    @Nonnull
    List<Asset> findByOwnerId(@Nullable UUID ownerId);

    /**
     * 更新资产信息
     *
     * @param id 资产ID
     * @param asset 更新后的资产信息
     * @return 更新后的资产实体
     */
    @Nonnull
    Asset updateAsset(@Nullable UUID id, @Nonnull Asset asset);

    /**
     * 更新资产状态
     *
     * @param id 资产ID
     * @param status 新状态
     * @param operatorId 操作者ID
     * @return 更新后的资产实体
     */
    @Nonnull
    Asset updateStatus(@Nullable UUID id, @Nullable String status, @Nullable UUID operatorId);

    /**
     * 删除资产（软删除，设置状态为archived）
     *
     * @param id 资产ID
     * @param operatorId 操作者ID
     */
    void deleteAsset(@Nullable UUID id, @Nullable UUID operatorId);

    /**
     * 检查资产编号是否存在
     *
     * @param code 资产编号
     * @return 是否存在
     */
    boolean existsByCode(@Nullable String code);

    /**
     * 统计团队下的资产数量
     *
     * @param teamId 团队ID
     * @return 资产数量
     */
    long countByTeamId(@Nullable UUID teamId);

    /**
     * 统计指定状态的资产数量
     *
     * @param status 状态
     * @return 资产数量
     */
    long countByStatus(@Nullable String status);

    /**
     * 检查用户是否有资产访问权限
     *
     * @param assetId 资产ID
     * @param userId 用户ID
     * @return 是否有权限
     */
    boolean hasAccess(@Nullable UUID assetId, @Nullable UUID userId);

    /**
     * 获取用户可访问的资产列表
     *
     * @param userId 用户ID
     * @return 资产列表
     */
    @Nonnull
    List<Asset> findAccessibleAssets(@Nullable UUID userId);

    /**
     * 获取用户已获授权的资产列表
     *
     * @param userId 用户ID
     * @return 已授权资产列表
     */
    @Nonnull
    List<Asset> findAuthorizedAssetsByUser(@Nullable UUID userId);
}