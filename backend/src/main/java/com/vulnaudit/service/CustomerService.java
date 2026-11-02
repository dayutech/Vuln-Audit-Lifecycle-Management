package com.vulnaudit.service;

import com.vulnaudit.dto.CustomerDetailResponse;
import com.vulnaudit.dto.CustomerRequest;
import com.vulnaudit.dto.CustomerResponse;
import com.vulnaudit.dto.CustomerSummaryResponse;
import com.vulnaudit.dto.PageResponse;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.UUID;

/**
 * 客户服务（t68）
 *
 * <p>客户是项目的归属维度：读对所有登录用户开放（组织维度不作为权限边界），写由控制器角色门槛把关。
 */
public interface CustomerService {

    /**
     * 分页查询客户
     *
     * @param page    页码（从 0 开始，负数按 0 处理）
     * @param size    每页大小（1..500）
     * @param keyword 关键词（匹配 code / name / industry）
     * @param status  状态筛选（active / inactive）
     * @param level   级别筛选
     */
    @Nonnull
    PageResponse<CustomerResponse> findAll(int page, int size, @Nullable String keyword,
                                          @Nullable String status, @Nullable String level);

    /**
     * 客户详情（含 projectCount / activeProjectCount / assetCount 聚合）
     *
     * @throws com.vulnaudit.exception.BusinessException 客户不存在 → 404
     */
    @Nonnull
    CustomerDetailResponse findDetail(@Nullable UUID id);

    /**
     * 客户详情聚合（t76）：客户本体 + 统计 + 项目列表（含期次数/资产数）+ 最近 10 个资产
     *
     * <p>一次请求返回全部数据，消除前端「先查项目列表、再逐项目查详情」的 N+1。
     * 期次数与项目级资产数用 group by 聚合查询一次算出。
     *
     * @param visibleAssetIds 可见资产 ID 集合（{@code AccessGuard.visibleAssetIds(auth)}）；
     *                        {@code null} = 不限制（管理层）。**只作用于 stats.assetCount /
     *                        vulnerabilityCount / recentAssets**，项目列表与 phaseCount 不受影响
     * @throws com.vulnaudit.exception.BusinessException 客户不存在 → 404
     */
    @Nonnull
    CustomerSummaryResponse findSummary(@Nullable UUID id, @Nullable java.util.Set<UUID> visibleAssetIds);

    /**
     * 创建客户
     *
     * @param request   请求
     * @param operatorId 操作者（写入 createdBy）
     * @throws com.vulnaudit.exception.BusinessException 编码重复 → 409
     */
    @Nonnull
    CustomerResponse create(@Nonnull CustomerRequest request, @Nullable UUID operatorId);

    /**
     * 更新客户
     *
     * @throws com.vulnaudit.exception.BusinessException 不存在 → 404；编码重复 → 409
     */
    @Nonnull
    CustomerResponse update(@Nullable UUID id, @Nonnull CustomerRequest request);

    /**
     * 变更客户状态（active ↔ inactive）
     *
     * @throws com.vulnaudit.exception.BusinessException 不存在 → 404；非法状态 → 400
     */
    @Nonnull
    CustomerResponse changeStatus(@Nullable UUID id, @Nullable String status);

    /**
     * 删除客户
     *
     * @throws com.vulnaudit.exception.BusinessException 不存在 → 404；仍有项目 → 409
     */
    void delete(@Nullable UUID id);
}
