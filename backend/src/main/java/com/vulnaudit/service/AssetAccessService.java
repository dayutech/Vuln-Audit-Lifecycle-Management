package com.vulnaudit.service;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.AssetAccess;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 资产访问服务接口
 * 
 * 功能说明：
 * - 定义资产访问申请的业务逻辑接口
 * - 提供访问申请CRUD操作
 * - 提供访问申请审批功能
 */
public interface AssetAccessService {

    /**
     * 创建访问申请
     * 
     * @param assetAccess 访问申请实体
     * @return 创建后的访问申请实体
     */
    AssetAccess createAccessRequest(AssetAccess assetAccess);

    /**
     * 根据ID查询访问申请
     * 
     * @param id 访问申请ID
     * @return 访问申请实体
     */
    Optional<AssetAccess> findById(UUID id);

    /**
     * 分页查询访问申请列表
     * 
     * @param page 页码
     * @param size 每页大小
     * @param assetId 资产ID（可选）
     * @param userId 用户ID（可选）
     * @param status 状态（可选）
     * @return 分页访问申请列表
     */
    PageResponse<AssetAccess> findAll(int page, int size, UUID assetId, UUID userId, String status);

    /**
     * 根据资产ID查询访问申请列表
     * 
     * @param assetId 资产ID
     * @return 访问申请列表
     */
    List<AssetAccess> findByAssetId(UUID assetId);

    /**
     * 根据用户ID查询访问申请列表
     * 
     * @param userId 用户ID
     * @return 访问申请列表
     */
    List<AssetAccess> findByUserId(UUID userId);

    /**
     * 审批访问申请
     * 
     * @param id 访问申请ID
     * @param approved 是否批准
     * @param reviewNote 审批备注
     * @param reviewerId 审批人ID
     * @return 更新后的访问申请实体
     */
    AssetAccess reviewAccessRequest(UUID id, boolean approved, String reviewNote, UUID reviewerId);

    /**
     * 撤销访问申请
     * 
     * @param id 访问申请ID
     * @param userId 申请人ID
     */
    void revokeAccessRequest(UUID id, UUID userId);

    /**
     * 检查用户是否有资产的已批准访问权限
     * 
     * @param assetId 资产ID
     * @param userId 用户ID
     * @return 是否有权限
     */
    boolean hasApprovedAccess(UUID assetId, UUID userId);

    /**
     * 获取用户对资产的访问权限
     * 
     * @param assetId 资产ID
     * @param userId 用户ID
     * @return 访问申请实体（如果存在）
     */
    Optional<AssetAccess> getApprovedAccess(UUID assetId, UUID userId);

    /**
     * 统计用户待审核的访问申请数量
     * 
     * @param userId 用户ID
     * @return 待审核数量
     */
    long countPendingByUser(UUID userId);

    /**
     * 获取用户待审批的访问申请列表
     * 
     * @param userId 用户ID（审批人）
     * @param page 页码
     * @param size 每页大小
     * @return 分页访问申请列表
     */
    PageResponse<AssetAccess> findPendingApprovalByUser(UUID userId, int page, int size);
}
