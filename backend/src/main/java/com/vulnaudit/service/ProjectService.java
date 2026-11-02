package com.vulnaudit.service;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.dto.PhaseRequest;
import com.vulnaudit.dto.PhaseResponse;
import com.vulnaudit.dto.ProjectDetailResponse;
import com.vulnaudit.dto.ProjectRequest;
import com.vulnaudit.dto.ProjectResponse;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 项目与期次服务（t68）
 *
 * <p>项目是组织维度（不作为权限边界）：读对所有登录用户开放；写由控制器
 * {@code hasAnyRole('ADMIN','PROJECT_LEADER') or @accessGuard.canManageProject(#id)} 把关，
 * 其中 team_leader 只能写 {@code managerId == 自己} 的项目。
 */
public interface ProjectService {

    /**
     * 分页查询项目
     *
     * @param page       页码（从 0 开始）
     * @param size       每页大小（1..500）
     * @param customerId 客户筛选
     * @param status     状态筛选（DRAFT / ACTIVE / PAUSED / CLOSED）
     * @param type       类型筛选
     * @param keyword    关键词（匹配 code / name / contractNo）
     */
    @Nonnull
    PageResponse<ProjectResponse> findAll(int page, int size, @Nullable UUID customerId,
                                          @Nullable String status, @Nullable String type,
                                          @Nullable String keyword);

    /**
     * 项目详情（含 customerBrief、phases、assetCount）
     *
     * @throws com.vulnaudit.exception.BusinessException 项目不存在 → 404
     */
    @Nonnull
    ProjectDetailResponse findDetail(@Nullable UUID id);

    /**
     * 创建项目
     *
     * @throws com.vulnaudit.exception.BusinessException 客户不存在 → 404；编码重复 → 409；经理不存在 → 404
     */
    @Nonnull
    ProjectResponse create(@Nonnull ProjectRequest request, @Nullable UUID operatorId);

    /**
     * 更新项目（含 isPhased 关闭的期次约束）
     *
     * @throws com.vulnaudit.exception.BusinessException 不存在 → 404；编码重复 → 409；有按期次挂靠时关闭分期 → 409
     */
    @Nonnull
    ProjectResponse update(@Nullable UUID id, @Nonnull ProjectRequest request);

    /**
     * 变更项目状态（状态机：DRAFT→ACTIVE→PAUSED↔ACTIVE→CLOSED，CLOSED 不可再流转）
     *
     * @throws com.vulnaudit.exception.BusinessException 不存在 → 404；非法流转 → 400（含当前状态与合法下一步）
     */
    @Nonnull
    ProjectResponse changeStatus(@Nullable UUID id, @Nullable String status);

    /**
     * 删除项目
     *
     * @throws com.vulnaudit.exception.BusinessException 不存在 → 404；仍有资产挂靠 → 409
     */
    void delete(@Nullable UUID id);

    /** 期次列表（按 phaseNo 升序） */
    @Nonnull
    List<PhaseResponse> listPhases(@Nullable UUID projectId);

    /**
     * 创建期次（仅 isPhased=true 的项目）
     *
     * @throws com.vulnaudit.exception.BusinessException 项目不存在 → 404；未启用分期 → 400；期次号重复 → 409
     */
    @Nonnull
    PhaseResponse createPhase(@Nullable UUID projectId, @Nonnull PhaseRequest request);

    /**
     * 更新期次
     *
     * @throws com.vulnaudit.exception.BusinessException 期次不存在 → 404；期次号重复 → 409
     */
    @Nonnull
    PhaseResponse updatePhase(@Nullable UUID phaseId, @Nonnull PhaseRequest request);

    /**
     * 删除期次
     *
     * @throws com.vulnaudit.exception.BusinessException 期次不存在 → 404；仍有按期次挂靠 → 409
     */
    void deletePhase(@Nullable UUID phaseId);

    /**
     * 按项目ID取其项目经理（供控制器/审计复用，可为空）
     */
    @Nullable
    UUID findManagerId(@Nullable UUID projectId);
}
