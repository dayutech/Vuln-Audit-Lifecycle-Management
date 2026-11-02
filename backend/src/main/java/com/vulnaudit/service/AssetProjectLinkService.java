package com.vulnaudit.service;

import com.vulnaudit.dto.AssetCreateRequest;
import com.vulnaudit.dto.AssetProjectLinkRequest;
import com.vulnaudit.dto.AssetProjectLinkResponse;
import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.dto.ProjectAssetItem;
import com.vulnaudit.entity.Asset;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 资产—项目挂靠服务（t69）
 *
 * <p>核心不变式：
 * <ol>
 *   <li><b>建资产必须挂项目</b>：{@link #createAssetWithProject} 是唯一的创建入口，
 *       资产与挂靠在<b>同一事务</b>内写入；</li>
 *   <li><b>资产至少挂一个项目</b>：解绑最后一条挂靠返回 409，不允许出现「无项目资产」；</li>
 *   <li><b>同一 (asset, project, phase) 唯一</b>：重复挂靠 409；跨客户挂靠<b>允许</b>
 *       （同一资产可同时服务多个客户的项目）。</li>
 * </ol>
 */
public interface AssetProjectLinkService {

    /**
     * 创建资产并挂靠到项目（同一事务）
     *
     * @param request   资产创建请求（含 projectId / phaseId）
     * @param ownerId   资产负责人（当前登录用户）
     * @return 已落库的资产
     * @throws com.vulnaudit.exception.BusinessException 缺 projectId → 400；项目/客户不存在 → 404；
     *                                                   期次不属于项目或项目未分期 → 400；重复挂靠 → 409
     */
    @Nonnull
    Asset createAssetWithProject(@Nonnull AssetCreateRequest request, @Nullable UUID ownerId);

    /**
     * 创建资产并挂靠到项目（入参是已拼好的 Asset 草稿，供 CSV 导入复用，t78）
     *
     * <p>与 {@link #createAssetWithProject(AssetCreateRequest, UUID)} 同一条实现路径：
     * 校验项目/客户/期次 → {@code assetService.createAsset} → 写挂靠，整体在**同一事务**内，
     * 任一步失败都整体回滚，因此不会出现「资产建成但没有挂靠」的中间态。
     *
     * @param assetDraft  资产草稿（名称/类型/来源/优先级/状态/团队/负责人/描述）
     * @param projectId   目标项目（必填）
     * @param phaseId     期次（可空；非空时项目必须启用分期且期次属于该项目）
     * @param linkType    挂靠类型（缺省 TARGET；导入「未指定项目」的场景用 SHARED）
     * @param remark      挂靠备注
     * @param operatorId  操作者（写入 linkedBy）
     * @throws com.vulnaudit.exception.BusinessException 项目/客户/期次不存在 → 404；
     *                                                   期次不属于项目或项目未分期 → 400
     */
    @Nonnull
    Asset createAssetWithLink(@Nonnull com.vulnaudit.entity.Asset assetDraft,
                              @Nonnull UUID projectId,
                              @Nullable UUID phaseId,
                              @Nullable String linkType,
                              @Nullable String remark,
                              @Nullable UUID operatorId);

    /**
     * 为已存在的资产追加挂靠
     *
     * @throws com.vulnaudit.exception.BusinessException 资产/项目/客户不存在 → 404；
     *                                                   期次非法 → 400；重复挂靠 → 409
     */
    @Nonnull
    AssetProjectLinkResponse link(@Nullable UUID assetId, @Nonnull AssetProjectLinkRequest request,
                                  @Nullable UUID operatorId);

    /**
     * 解绑
     *
     * @throws com.vulnaudit.exception.BusinessException 资产/挂靠记录不存在或不属于该资产 → 404；
     *                                                   仅剩一条挂靠 → 409
     */
    void unlink(@Nullable UUID assetId, @Nullable UUID linkId);

    /** 某资产的全部挂靠（含项目/客户/期次摘要） */
    @Nonnull
    List<AssetProjectLinkResponse> listByAsset(@Nullable UUID assetId);

    /**
     * 反向查询：项目下的资产（可按期次过滤，分页）
     *
     * @throws com.vulnaudit.exception.BusinessException 项目不存在 → 404
     */
    @Nonnull
    PageResponse<ProjectAssetItem> listAssetsByProject(@Nullable UUID projectId, @Nullable UUID phaseId,
                                                       int page, int size);

    /**
     * 反向查询：项目下的资产（按可见资产范围收敛，t70）
     *
     * <p><b>项目可见 ≠ 项目下资产可见</b>：非管理层只能看到 {@code visibleAssetIds} 内的资产，
     * 否则会通过项目维度绕过 t46 的资产读边界。管理层传 {@code null}（不限制）。
     *
     * @param visibleAssetIds 可见资产集合；{@code null} 表示不限制（管理层）
     */
    @Nonnull
    PageResponse<ProjectAssetItem> listAssetsByProject(@Nullable UUID projectId, @Nullable UUID phaseId,
                                                       int page, int size,
                                                       @Nullable java.util.Set<UUID> visibleAssetIds);

    /**
     * 批量组装资产的归属摘要（客户/项目），供列表接口避免 N+1
     *
     * @param assetIds 本页资产 ID
     * @return assetId → 归属摘要；无挂靠的资产不在 map 中
     */
    @Nonnull
    Map<UUID, Ownership> ownershipOf(@Nonnull Collection<UUID> assetIds);

    /**
     * 按挂靠条件过滤出资产 ID（供 {@code GET /assets} 的 customerId/projectId/phaseId 筛选）
     *
     * @return {@code null} 表示未指定任何挂靠筛选（不限制）
     */
    @Nullable
    Set<UUID> filterAssetIds(@Nullable UUID customerId, @Nullable UUID projectId, @Nullable UUID phaseId);

    /** 归属摘要 */
    record Ownership(List<com.vulnaudit.dto.CustomerBrief> customers,
                     List<com.vulnaudit.dto.ProjectBrief> projects) {
    }
}
