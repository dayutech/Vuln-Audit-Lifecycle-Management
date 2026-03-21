package com.vulnaudit.service;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.AssetClaim;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 资产认领服务接口
 * 
 * 功能说明：
 * - 定义资产认领的业务逻辑接口
 * - 提供认领CRUD操作
 * - 提供认领状态管理
 */
public interface AssetClaimService {

    /**
     * 创建认领申请
     * 
     * @param assetClaim 认领申请实体
     * @return 创建后的认领申请实体
     */
    AssetClaim createClaim(AssetClaim assetClaim);

    /**
     * 根据ID查询认领申请
     * 
     * @param id 认领申请ID
     * @return 认领申请实体
     */
    Optional<AssetClaim> findById(UUID id);

    /**
     * 分页查询认领申请列表
     * 
     * @param page 页码
     * @param size 每页大小
     * @param assetId 资产ID（可选）
     * @param userId 用户ID（可选）
     * @param status 状态（可选）
     * @return 分页认领申请列表
     */
    PageResponse<AssetClaim> findAll(int page, int size, UUID assetId, UUID userId, String status);

    /**
     * 根据资产ID查询认领申请列表
     * 
     * @param assetId 资产ID
     * @return 认领申请列表
     */
    List<AssetClaim> findByAssetId(UUID assetId);

    /**
     * 根据用户ID查询认领申请列表
     * 
     * @param userId 用户ID
     * @return 认领申请列表
     */
    List<AssetClaim> findByUserId(UUID userId);

    /**
     * 审批认领申请
     * 
     * @param id 认领申请ID
     * @param approved 是否批准
     * @param reviewNote 审批备注
     * @param reviewerId 审批人ID
     * @return 更新后的认领申请实体
     */
    AssetClaim reviewClaim(UUID id, boolean approved, String reviewNote, UUID reviewerId);

    /**
     * 完成认领
     * 
     * @param id 认领申请ID
     * @param userId 认领人ID
     */
    void completeClaim(UUID id, UUID userId);

    /**
     * 放弃认领
     * 
     * @param id 认领申请ID
     * @param userId 认领人ID
     * @param note 放弃原因
     */
    void abandonClaim(UUID id, UUID userId, String note);

    /**
     * 撤销认领申请
     * 
     * @param id 认领申请ID
     * @param userId 申请人ID
     */
    void revokeClaim(UUID id, UUID userId);

    /**
     * 检查资产是否有活跃认领
     * 
     * @param assetId 资产ID
     * @return 是否有活跃认领
     */
    boolean hasActiveClaim(UUID assetId);

    /**
     * 获取资产的当前活跃认领
     * 
     * @param assetId 资产ID
     * @return 认领申请实体（如果存在）
     */
    Optional<AssetClaim> getActiveClaim(UUID assetId);

    /**
     * 检查用户是否有资产的活跃认领
     * 
     * @param assetId 资产ID
     * @param userId 用户ID
     * @return 是否有活跃认领
     */
    boolean hasActiveClaimByUser(UUID assetId, UUID userId);

    /**
     * 统计用户指定状态的认领数量
     * 
     * @param userId 用户ID
     * @param status 状态
     * @return 认领数量
     */
    long countByUserIdAndStatus(UUID userId, String status);
}
