package com.vulnaudit.controller;

import com.vulnaudit.dto.ApiResponse;
import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.dto.PhaseRequest;
import com.vulnaudit.dto.PhaseResponse;
import com.vulnaudit.dto.ProjectAssetItem;
import com.vulnaudit.dto.ProjectDetailResponse;
import com.vulnaudit.dto.ProjectRequest;
import com.vulnaudit.dto.ProjectResponse;
import com.vulnaudit.security.CurrentUserProvider;
import com.vulnaudit.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * 项目管理控制器（t68）
 *
 * <p>权限口径：<b>读</b>对所有登录用户开放；<b>写</b>为
 * {@code hasAnyRole('ADMIN','PROJECT_LEADER') or @accessGuard.canManageProject(#id, authentication)}
 * （team_leader 仅能写自己担任经理的项目）。创建没有 {@code #id}，故限 ADMIN / PROJECT_LEADER。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/projects")
@RequiredArgsConstructor
@Tag(name = "项目管理", description = "项目 CRUD、状态机、期次管理")
@SecurityRequirement(name = "bearerAuth")
public class ProjectController {

    /** 写权限表达式（读对所有登录用户开放） */
    private static final String WRITE_GUARD =
            "hasAnyRole('ADMIN','PROJECT_LEADER') or @accessGuard.canManageProject(#id, authentication)";

    private final ProjectService projectService;
    private final com.vulnaudit.service.AssetProjectLinkService assetProjectLinkService;
    private final com.vulnaudit.service.AccessGuard accessGuard;
    private final com.vulnaudit.service.impl.CustomerProjectStatsService statsService;
    private final CurrentUserProvider currentUserProvider;

    // ------------------------------------------------------------------ 项目

    @GetMapping
    @Operation(summary = "分页查询项目", description = "支持 customerId / status / type / keyword 筛选")
    public ResponseEntity<ApiResponse<PageResponse<ProjectResponse>>> listProjects(
            @Parameter(description = "页码（从0开始）") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "客户ID") @RequestParam(required = false) UUID customerId,
            @Parameter(description = "状态 DRAFT/ACTIVE/PAUSED/CLOSED") @RequestParam(required = false) String status,
            @Parameter(description = "类型") @RequestParam(required = false) String type,
            @Parameter(description = "关键词（编码/名称/合同号）") @RequestParam(required = false) String keyword) {

        log.debug("查询项目列表: page={}, size={}, customerId={}, status={}, type={}",
                page, size, customerId, status, type);
        return ResponseEntity.ok(ApiResponse.success(
                projectService.findAll(page, size, customerId, status, type, keyword)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "项目详情", description = "含 customerBrief、phases、assetCount")
    public ResponseEntity<ApiResponse<ProjectDetailResponse>> getProject(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(projectService.findDetail(id)));
    }

    @GetMapping("/{id}/stats")
    @Operation(summary = "项目统计（t70）",
            description = "phaseCount 不随可见性变化；assetCount / vulnerabilityCount / taskCount 与 byPhase "
                    + "按调用者可见资产范围收敛（管理层全量）")
    public ResponseEntity<ApiResponse<com.vulnaudit.dto.ProjectStatsResponse>> getProjectStats(
            @PathVariable UUID id,
            org.springframework.security.core.Authentication authentication) {

        java.util.Set<UUID> visible = accessGuard.visibleAssetIds(authentication);
        log.debug("项目统计: id={}, visibilityRestricted={}", id, visible != null);
        return ResponseEntity.ok(ApiResponse.success(statsService.projectStats(id, visible)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_LEADER')")
    @Operation(summary = "创建项目", description = "初始状态 DRAFT；customerId 必须存在（404）；code 冲突 409")
    public ResponseEntity<ApiResponse<ProjectResponse>> createProject(@Valid @RequestBody ProjectRequest request) {
        UUID operatorId = currentUserProvider.currentUserId().orElse(null);
        ProjectResponse created = projectService.create(request, operatorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "更新项目", description = "team_leader 仅能改自己担任经理的项目；关闭分期有期次挂靠时 409")
    public ResponseEntity<ApiResponse<ProjectResponse>> updateProject(
            @PathVariable UUID id,
            @Valid @RequestBody ProjectRequest request) {

        return ResponseEntity.ok(ApiResponse.success(projectService.update(id, request)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "变更项目状态",
            description = "状态机 DRAFT→ACTIVE→PAUSED↔ACTIVE→CLOSED；CLOSED 为终态；非法流转 400")
    public ResponseEntity<ApiResponse<ProjectResponse>> changeProjectStatus(
            @PathVariable UUID id,
            @Parameter(description = "目标状态") @RequestParam String status) {

        return ResponseEntity.ok(ApiResponse.success(projectService.changeStatus(id, status)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "删除项目", description = "该项目下仍有资产挂靠时 409")
    public ResponseEntity<ApiResponse<Void>> deleteProject(@PathVariable UUID id) {
        projectService.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ------------------------------------------------------------------ 期次

    @GetMapping("/{projectId}/phases")
    @Operation(summary = "期次列表", description = "按 phaseNo 升序")
    public ResponseEntity<ApiResponse<List<PhaseResponse>>> listPhases(@PathVariable UUID projectId) {
        return ResponseEntity.ok(ApiResponse.success(projectService.listPhases(projectId)));
    }

    @PostMapping("/{projectId}/phases")
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_LEADER') or @accessGuard.canManageProject(#projectId, authentication)")
    @Operation(summary = "创建期次", description = "仅 isPhased=true 的项目可建期次（否则 400）；期次号重复 409")
    public ResponseEntity<ApiResponse<PhaseResponse>> createPhase(
            @PathVariable UUID projectId,
            @Valid @RequestBody PhaseRequest request) {

        PhaseResponse created = projectService.createPhase(projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    // ------------------------------------------------------------------ 项目下的资产（t69 反向查询）

    @GetMapping("/{projectId}/assets")
    @Operation(summary = "项目下的资产",
            description = "返回资产摘要 + 挂靠信息，分页；phaseId 可选（不传 = 该项目全部挂靠，"
                    + "传则只看该期次）。读对所有登录用户开放，但**资产按调用者可见范围收敛**（t70）")
    public ResponseEntity<ApiResponse<PageResponse<ProjectAssetItem>>> listProjectAssets(
            @PathVariable UUID projectId,
            @Parameter(description = "期次ID（可选）") @RequestParam(required = false) UUID phaseId,
            @Parameter(description = "页码（从0开始）") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size,
            org.springframework.security.core.Authentication authentication) {

        log.debug("查询项目资产: projectId={}, phaseId={}, page={}, size={}", projectId, phaseId, page, size);
        // t70：项目可见 ≠ 项目下资产可见 —— 按 visibleAssetIds 收敛（管理层 null = 不限制）
        java.util.Set<UUID> visible = accessGuard.visibleAssetIds(authentication);
        return ResponseEntity.ok(ApiResponse.success(
                assetProjectLinkService.listAssetsByProject(projectId, phaseId, page, size, visible)));
    }
}
