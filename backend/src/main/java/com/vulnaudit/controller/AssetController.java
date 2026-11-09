package com.vulnaudit.controller;

import com.vulnaudit.dto.*;
import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.AssetAccess;
import com.vulnaudit.entity.AssetClaim;
import com.vulnaudit.entity.AssetHistory;
import com.vulnaudit.entity.AssetTag;
import com.vulnaudit.repository.AssetHistoryRepository;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.AssetTagRepository;
import com.vulnaudit.service.AssetService;
import com.vulnaudit.service.AssetAccessService;
import com.vulnaudit.service.AssetClaimService;
import com.vulnaudit.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.annotation.Nullable;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 资产管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/assets")
@RequiredArgsConstructor
@Tag(name = "资产管理", description = "资产CRUD、分组、认领、访问申请等接口")
@SecurityRequirement(name = "bearerAuth")
public class AssetController {

    private final AssetService assetService;
    private final AssetAccessService assetAccessService;
    private final AssetClaimService assetClaimService;
    private final com.vulnaudit.service.AssetProjectLinkService assetProjectLinkService;
    private final com.vulnaudit.security.CurrentUserProvider currentUserProvider;
    private final com.vulnaudit.service.AccessGuard accessGuard;
    private final com.vulnaudit.service.SubresourceGuard subresourceGuard;
    private final AssetTagRepository assetTagRepository;
    private final AssetHistoryRepository assetHistoryRepository;
    private final AssetRepository assetRepository;
    private final UserService userService;
    /** t141：标签筛选（tags=AND / tagsAny=OR） */
    private final com.vulnaudit.service.TagDefinitionService tagDefinitionService;

    private static final List<Map<String, String>> ASSET_PRIORITY_OPTIONS = List.of(
            Map.of("value", "P0", "label", "P0 - 核心/关键"),
            Map.of("value", "P1", "label", "P1 - 重要"),
            Map.of("value", "P2", "label", "P2 - 一般"),
            Map.of("value", "P3", "label", "P3 - 边缘")
    );
    private static final List<Map<String, String>> ASSET_STATUS_OPTIONS = List.of(
            Map.of("value", "active", "label", "活跃"),
            Map.of("value", "under_test", "label", "测试中"),
            Map.of("value", "suspended", "label", "暂停"),
            Map.of("value", "archived", "label", "已归档")
    );

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_LEADER','TEAM_LEADER') and @accessGuard.canCreateAssetInTeam(#request.teamId, authentication)")
    @Operation(summary = "创建资产", description = "创建资产并挂靠到指定项目（projectId 必填，t69 硬约束）")
    public ResponseEntity<ApiResponse<Asset>> createAsset(@Valid @RequestBody AssetCreateRequest request) {
        log.info("创建资产: name={}, projectId={}, phaseId={}",
                request.getName(), request.getProjectId(), request.getPhaseId());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UUID currentUserId = getCurrentUserId(authentication);
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(401, "未授权"));
        }

        // t69：资产与挂靠在同一事务内创建；缺 projectId → 400，项目/客户不存在 → 404
        Asset createdAsset = assetProjectLinkService.createAssetWithProject(request, currentUserId);
        recordHistory(createdAsset.getId(), "created", null, createdAsset.getName(), currentUserId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(createdAsset));
    }

    @GetMapping("/options")
    @Operation(summary = "获取资产选项", description = "获取资产类型、来源、优先级、状态选项")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getAssetOptions() {
        List<Map<String, String>> targetTypeOptions = assetRepository.findDistinctTargetTypes().stream()
                .map(item -> Map.of("value", item, "label", item))
                .toList();
        List<Map<String, String>> targetSourceOptions = assetRepository.findDistinctTargetSources().stream()
                .map(item -> Map.of("value", item, "label", item))
                .toList();

        Map<String, Object> result = Map.of(
                "targetTypes", targetTypeOptions,
                "targetSources", targetSourceOptions,
                "priorities", ASSET_PRIORITY_OPTIONS,
                "statuses", ASSET_STATUS_OPTIONS
        );
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@accessGuard.canReadAsset(#id, authentication)")
    @Operation(summary = "获取资产详情", description = "根据ID获取资产详细信息")
    public ResponseEntity<ApiResponse<AssetResponse>> getAsset(@PathVariable UUID id) {
        log.info("获取资产详情: id={}", id);

        Optional<AssetResponse> detail = assetService.findByIdWithDetails(id);
        detail.ifPresent(response -> {
            // t69：详情附带完整挂靠明细（列表接口不填，避免响应体膨胀）
            response.setProjectLinks(assetProjectLinkService.listByAsset(id));
            Map<UUID, com.vulnaudit.service.AssetProjectLinkService.Ownership> ownership =
                    assetProjectLinkService.ownershipOf(List.of(id));
            com.vulnaudit.service.AssetProjectLinkService.Ownership own = ownership.get(id);
            if (own != null) {
                response.setCustomers(own.customers());
                response.setProjects(own.projects());
            }
        });
        return detail.map(asset -> ResponseEntity.ok(ApiResponse.success(asset)))
                // 走全局异常处理器输出，文案经 i18n 解析（en / zh），响应结构不变
                .orElseThrow(() -> new com.vulnaudit.exception.BusinessException(
                        com.vulnaudit.exception.ErrorCode.ASSET_NOT_FOUND));
    }

    @GetMapping
    @Operation(summary = "分页查询资产列表",
            description = "支持按关键词、状态、团队、优先级，以及归属筛选 customerId / projectId / phaseId；"
                    + "返回范围与详情共用同一可见性判定（AccessGuard.visibleAssetIds）；"
                    + "每条资产附带 customers / projects 摘要（一次批量查询组装，无 N+1）")
    public ResponseEntity<ApiResponse<PageResponse<AssetResponse>>> listAssets(
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "搜索关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "资产状态") @RequestParam(required = false) String status,
            @Parameter(description = "团队ID") @RequestParam(required = false) UUID teamId,
            @Parameter(description = "优先级") @RequestParam(required = false) String priority,
            @Parameter(description = "客户ID（按挂靠筛选）") @RequestParam(required = false) UUID customerId,
            @Parameter(description = "项目ID（按挂靠筛选）") @RequestParam(required = false) UUID projectId,
            @Parameter(description = "期次ID（按挂靠筛选）") @RequestParam(required = false) UUID phaseId,
            @Parameter(description = "标签名，逗号分隔，**AND** 语义（t141）") @RequestParam(required = false) String tags,
            @Parameter(description = "标签名，逗号分隔，**OR** 语义（t141）") @RequestParam(required = false) String tagsAny) {

        log.info("分页查询资产: page={}, size={}, keyword={}, status={}, customerId={}, projectId={}, phaseId={}, tags={}, tagsAny={}",
                page, size, keyword, status, customerId, projectId, phaseId, tags, tagsAny);

        // 单一事实来源：列表范围 = 详情判定（visibleAssetIds 为 null 表示管理层不限制）。
        // 此前列表未做任何过滤，导致「列表可见 → 点进去 403」。
        java.util.Set<UUID> visibleAssetIds = accessGuard.visibleAssetIds(
                SecurityContextHolder.getContext().getAuthentication());

        // t69：归属筛选 → 先取命中的资产 ID（null 表示未指定筛选）
        java.util.Set<UUID> ownershipFilteredIds =
                assetProjectLinkService.filterAssetIds(customerId, projectId, phaseId);

        // t141：标签筛选（tags=AND，tagsAny=OR）→ 与归属筛选取交集；可见性仍在服务层再收敛一次
        java.util.Set<UUID> tagFilteredIds = resolveTagFilteredIds(tags, tagsAny, visibleAssetIds);
        java.util.Set<UUID> filteredIds = intersect(ownershipFilteredIds, tagFilteredIds);

        PageResponse<AssetResponse> result = assetService.findAllWithDetails(
                page, size, keyword, status, teamId, priority, visibleAssetIds, filteredIds);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 标签筛选（t141）
     *
     * <p>选型：**同时支持** {@code tags}（AND）与 {@code tagsAny}（OR）；两者都给时取前者与后者的交集
     * （即「同时满足全部 tags」且「至少命中一个 tagsAny」）。
     *
     * @return null 表示未指定标签筛选；空集表示无命中（调用方按「返回空页」处理）
     */
    private java.util.Set<UUID> resolveTagFilteredIds(@Nullable String tags, @Nullable String tagsAny,
                                                     @Nullable java.util.Set<UUID> visibleAssetIds) {
        java.util.Set<UUID> andIds = tagDefinitionService.resolveAssetIdsByTags(splitTags(tags));
        java.util.Set<UUID> orIds = null;
        java.util.List<String> anyList = splitTags(tagsAny);
        if (anyList != null) {
            orIds = new java.util.HashSet<>();
            for (String name : anyList) {
                orIds.addAll(tagDefinitionService.assetIdsWithTag(name, visibleAssetIds));
            }
        }
        if (andIds == null) {
            return orIds;
        }
        if (orIds == null) {
            return andIds;
        }
        andIds.retainAll(orIds);
        return andIds;
    }

    /** 逗号分隔 → 去空白/去重的名称列表；空串返回 null（= 未指定） */
    private java.util.List<String> splitTags(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        java.util.List<String> names = new java.util.ArrayList<>();
        for (String part : raw.split(",")) {
            if (!part.isBlank()) {
                names.add(part.trim());
            }
        }
        return names.isEmpty() ? null : names;
    }

    /** 两个「受限 id 集合」求交；null = 不限制 */
    private java.util.Set<UUID> intersect(@Nullable java.util.Set<UUID> left,
                                          @Nullable java.util.Set<UUID> right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        java.util.Set<UUID> result = new java.util.HashSet<>(left);
        result.retainAll(right);
        return result;
    }

    /** 批量端点最多接受的 id 数（t145） */
    private static final int MAX_TAG_BATCH_IDS = 100;

    @GetMapping("/tags")
    @Operation(summary = "批量查询资产标签（t145）",
            description = "ids 逗号分隔（最多 100 个）；返回 { assetId: [{id,name,color,builtin}] }；"
                    + "不可见/不存在的 id 直接跳过（不报错、不泄露存在性）")
    public ApiResponse<java.util.Map<UUID, java.util.List<com.vulnaudit.dto.AssetTagBrief>>> batchAssetTags(
            @Parameter(description = "资产ID，逗号分隔，最多 100 个") @RequestParam(required = false) String ids,
            org.springframework.security.core.Authentication authentication) {

        java.util.List<UUID> requested = new java.util.ArrayList<>();
        if (ids != null && !ids.isBlank()) {
            for (String part : ids.split(",")) {
                String value = part.trim();
                if (value.isEmpty()) {
                    continue;
                }
                if (requested.size() >= MAX_TAG_BATCH_IDS) {
                    throw new com.vulnaudit.exception.BusinessException(
                            com.vulnaudit.exception.ErrorCode.BAD_REQUEST,
                            "一次最多查询 " + MAX_TAG_BATCH_IDS + " 个资产的标签");
                }
                try {
                    requested.add(UUID.fromString(value));
                } catch (IllegalArgumentException ex) {
                    throw new com.vulnaudit.exception.BusinessException(
                            com.vulnaudit.exception.ErrorCode.BAD_REQUEST, "资产ID 格式不正确: " + value);
                }
            }
        }
        if (requested.isEmpty()) {
            return ApiResponse.success(java.util.Map.of());
        }

        // 与可见性取交集：不可见资产直接跳过（不泄露存在性）
        java.util.Set<UUID> visibleAssetIds = accessGuard.visibleAssetIds(authentication);
        java.util.List<UUID> allowed = visibleAssetIds == null
                ? requested
                : requested.stream().filter(visibleAssetIds::contains).toList();
        if (allowed.isEmpty()) {
            return ApiResponse.success(java.util.Map.of());
        }
        log.info("批量查询资产标签: requested={}, allowed={}", requested.size(), allowed.size());
        return ApiResponse.success(assetService.tagsByAssetIds(allowed));
    }

    // ------------------------------------------------------------------ 资产-项目挂靠（t69）

    @GetMapping("/{assetId}/project-links")
    @PreAuthorize("@accessGuard.canReadAsset(#assetId, authentication)")
    @Operation(summary = "查询资产的项目挂靠", description = "返回项目与客户摘要；跨客户挂靠会返回多条")
    public ResponseEntity<ApiResponse<List<AssetProjectLinkResponse>>> listProjectLinks(
            @PathVariable UUID assetId) {

        log.info("查询资产挂靠: assetId={}", assetId);
        return ResponseEntity.ok(ApiResponse.success(assetProjectLinkService.listByAsset(assetId)));
    }

    @PostMapping("/{assetId}/project-links")
    @PreAuthorize("@accessGuard.canWriteAsset(#assetId, authentication)")
    @Operation(summary = "追加项目挂靠",
            description = "同一 (资产, 项目, 期次) 重复挂靠 409；允许跨客户挂靠（同一资产服务多个客户的项目）")
    public ResponseEntity<ApiResponse<AssetProjectLinkResponse>> addProjectLink(
            @PathVariable UUID assetId,
            @Valid @RequestBody AssetProjectLinkRequest request) {

        log.info("追加资产挂靠: assetId={}, projectId={}, phaseId={}",
                assetId, request.getProjectId(), request.getPhaseId());
        AssetProjectLinkResponse created = assetProjectLinkService.link(
                assetId, request, getCurrentUserId(SecurityContextHolder.getContext().getAuthentication()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @DeleteMapping("/{assetId}/project-links/{linkId}")
    @PreAuthorize("@accessGuard.canWriteAsset(#assetId, authentication)")
    @Operation(summary = "解除项目挂靠",
            description = "挂靠记录不存在或不属于该资产 → 404；若为该资产最后一条挂靠 → 409（资产必须至少挂靠一个项目）")
    public ResponseEntity<ApiResponse<Void>> removeProjectLink(
            @PathVariable UUID assetId,
            @PathVariable UUID linkId) {

        log.info("解除资产挂靠: assetId={}, linkId={}", assetId, linkId);
        assetProjectLinkService.unlink(assetId, linkId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@accessGuard.canWriteAsset(#id, authentication)")
    @Operation(summary = "更新资产", description = "更新资产信息")
    public ResponseEntity<ApiResponse<Asset>> updateAsset(
            @PathVariable UUID id,
            @Valid @RequestBody AssetUpdateRequest request) {
        
        log.info("更新资产: id={}", id);
        
        UUID operatorId = getCurrentUserId(SecurityContextHolder.getContext().getAuthentication());

        // 注意：开启 open-in-view 后，findById 返回的实体与 Service 内更新的是同一持久化上下文实例，
        // 更新后旧值会被覆盖。因此先把旧值快照成不可变字符串，再执行更新。
        String oldName = null;
        String oldDescription = null;
        String oldTargetType = null;
        String oldTargetSource = null;
        String oldPriority = null;
        String oldTeamId = null;
        String oldOwnerId = null;
        Asset snapshot = assetRepository.findById(id).orElse(null);
        if (snapshot != null) {
            oldName = snapshot.getName();
            oldDescription = snapshot.getDescription();
            oldTargetType = snapshot.getTargetType();
            oldTargetSource = snapshot.getTargetSource();
            oldPriority = snapshot.getPriority();
            oldTeamId = snapshot.getTeamId() == null ? null : snapshot.getTeamId().toString();
            oldOwnerId = snapshot.getOwnerId() == null ? null : snapshot.getOwnerId().toString();
        }

        Asset asset = Asset.builder()
                .name(request.getName())
                .targetType(request.getTargetType())
                .targetSource(request.getTargetSource())
                .description(request.getDescription())
                .priority(request.getPriority())
                .teamId(request.getTeamId())
                .ownerId(request.getOwnerId())
                .build();
        
        Asset updatedAsset = assetService.updateAsset(id, asset);

        // 字段级变更历史：仅记录真正发生变化的字段
        if (snapshot != null) {
            recordIfChanged(id, "name", oldName, updatedAsset.getName(), operatorId);
            recordIfChanged(id, "description", oldDescription, updatedAsset.getDescription(), operatorId);
            recordIfChanged(id, "targetType", oldTargetType, updatedAsset.getTargetType(), operatorId);
            recordIfChanged(id, "targetSource", oldTargetSource, updatedAsset.getTargetSource(), operatorId);
            recordIfChanged(id, "priority", oldPriority, updatedAsset.getPriority(), operatorId);
            recordIfChanged(id, "teamId", oldTeamId,
                    updatedAsset.getTeamId() == null ? null : updatedAsset.getTeamId().toString(), operatorId);
            recordIfChanged(id, "ownerId", oldOwnerId,
                    updatedAsset.getOwnerId() == null ? null : updatedAsset.getOwnerId().toString(), operatorId);
        }

        return ResponseEntity.ok(ApiResponse.success(updatedAsset));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@accessGuard.canWriteAsset(#id, authentication)")
    @Operation(summary = "删除资产", description = "软删除资产（写 deleted_at，列表/详情/统计不再返回；唯一编号仍占用）")
    public ResponseEntity<ApiResponse<Void>> deleteAsset(@PathVariable UUID id) {
        log.info("删除资产: id={}", id);

        UUID operatorId = getCurrentUserId(SecurityContextHolder.getContext().getAuthentication());
        String beforeStatus = assetRepository.findById(id).map(Asset::getStatus).orElse(null);
        assetService.deleteAsset(id, operatorId);
        recordHistory(id, "status", beforeStatus, "archived", operatorId);
        // t140：删除改为软删，响应语义统一为 204（不再返回 body）
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("@accessGuard.canWriteAsset(#id, authentication)")
    @Operation(summary = "更新资产状态", description = "更新资产状态")
    public ResponseEntity<ApiResponse<Asset>> updateStatus(
            @PathVariable UUID id,
            @Parameter(description = "新状态") @RequestParam String status) {
        
        log.info("更新资产状态: id={}, status={}", id, status);
        
        UUID operatorId = getCurrentUserId(SecurityContextHolder.getContext().getAuthentication());
        String beforeStatus = assetRepository.findById(id).map(Asset::getStatus).orElse(null);
        Asset updatedAsset = assetService.updateStatus(id, status, operatorId);
        recordIfChanged(id, "status", beforeStatus, updatedAsset.getStatus(), operatorId);
        return ResponseEntity.ok(ApiResponse.success(updatedAsset));
    }

    // ==================== 访问申请相关 ====================

    @PostMapping("/{id}/access-requests")
    @Operation(summary = "申请资产访问权限",
            description = "创建资产访问申请；资产不在调用者可见范围内 → 403（t93 与同类守卫统一，且不泄露存在性）")
    public ResponseEntity<ApiResponse<AssetAccess>> createAccessRequest(
            @PathVariable UUID id,
            @Valid @RequestBody AssetAccessRequest request) {

        log.info("申请资产访问权限: assetId={}", id);

        // t65：父资源不存在 → 404（此前会写入指向不存在资产的访问申请）
        subresourceGuard.requireAsset(id);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        // t93：「不可见即无权限」—— 访问申请只针对「能看到但需额外授权（文档/文件/环境）」的资产；
        // 完全不可见的资产按 403 处理（此前会一路走到服务层的 400/无权访问，语义与状态码都不统一）
        if (!accessGuard.canRequestAssetAccess(id, authentication)) {
            throw new com.vulnaudit.exception.BusinessException(
                    com.vulnaudit.exception.ErrorCode.ASSET_ACCESS_DENIED,
                    "无权访问该资产（你与该资产没有工作关系，不在本团队内）");
        }
        UUID currentUserId = getCurrentUserId(authentication);

        AssetAccess access = AssetAccess.builder()
                .assetId(id)
                .userId(currentUserId)
                .canViewDocuments(request.getCanViewDocuments())
                .canViewEnvironments(request.getCanViewEnvironments())
                .canViewFiles(request.getCanViewFiles())
                .allowedFileTypes(request.getAllowedFileTypes())
                .requestNote(request.getRequestNote())
                .build();

        AssetAccess createdAccess = assetAccessService.createAccessRequest(access);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(createdAccess));
    }

    @GetMapping("/{id}/access-requests")
    @PreAuthorize("@accessGuard.canReadAsset(#id, authentication) and @accessGuard.canSeeAssetAccessDetails(#id, authentication)")
    @Operation(summary = "获取资产访问申请列表", description = "获取指定资产的访问申请列表")
    public ResponseEntity<ApiResponse<PageResponse<AssetAccess>>> listAccessRequests(
            @PathVariable UUID id,
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "状态") @RequestParam(required = false) String status) {
        
        log.info("获取资产访问申请列表: assetId={}", id);
        
        PageResponse<AssetAccess> result = assetAccessService.findAll(page, size, id, null, status);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/access-requests/{requestId}/review")
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_LEADER','TEAM_LEADER')")
    @Operation(summary = "审批访问申请", description = "审批资产访问申请")
    public ResponseEntity<ApiResponse<AssetAccess>> reviewAccessRequest(
            @PathVariable UUID requestId,
            @Valid @RequestBody AssetAccessReviewRequest request) {
        
        log.info("审批访问申请: requestId={}, approved={}", requestId, request.isApproved());

        // 传入真实审批人，服务层据此校验「是否被授权审批该资产」
        UUID reviewerId = getCurrentUserId(SecurityContextHolder.getContext().getAuthentication());
        AssetAccess reviewedAccess = assetAccessService.reviewAccessRequest(
                requestId, request.isApproved(), request.getReviewNote(), reviewerId);
        return ResponseEntity.ok(ApiResponse.success(reviewedAccess));
    }

    // ==================== 认领相关 ====================

    @PostMapping("/{id}/claims")
    @Operation(summary = "认领资产", description = "创建资产认领申请")
    public ResponseEntity<ApiResponse<AssetClaim>> createClaim(
            @PathVariable UUID id,
            @Valid @RequestBody AssetClaimRequest request) {

        log.info("认领资产: assetId={}", id);

        // 与 complete/abandon 一致：统一从 CurrentUserProvider 取操作者（缺失 401），
        // 避免 userId=null 传到服务层 requireNonNull → 500
        UUID currentUserId = currentUserProvider.requireCurrentUserId();

        AssetClaim claim = AssetClaim.builder()
                .assetId(id)
                .userId(currentUserId)
                .note(request.getNote())
                .build();

        AssetClaim createdClaim = assetClaimService.createClaim(claim);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(createdClaim));
    }

    @GetMapping("/{id}/claims")
    @PreAuthorize("@accessGuard.canReadAsset(#id, authentication)")
    @Operation(summary = "获取资产认领记录", description = "获取指定资产的认领记录")
    public ResponseEntity<ApiResponse<PageResponse<AssetClaim>>> listClaims(
            @PathVariable UUID id,
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "状态") @RequestParam(required = false) String status) {
        
        log.info("获取资产认领记录: assetId={}", id);
        
        PageResponse<AssetClaim> result = assetClaimService.findAll(page, size, id, null, status);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/claims/{claimId}/review")
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_LEADER','TEAM_LEADER')")
    @Operation(summary = "审批认领申请", description = "审批资产认领申请")
    public ResponseEntity<ApiResponse<AssetClaim>> reviewClaim(
            @PathVariable UUID claimId,
            @Valid @RequestBody AssetAccessReviewRequest request) {
        
        log.info("审批认领申请: claimId={}, approved={}", claimId, request.isApproved());

        UUID reviewerId = getCurrentUserId(SecurityContextHolder.getContext().getAuthentication());
        AssetClaim reviewedClaim = assetClaimService.reviewClaim(
                claimId, request.isApproved(), request.getReviewNote(), reviewerId);
        return ResponseEntity.ok(ApiResponse.success(reviewedClaim));
    }

    @PostMapping("/claims/{claimId}/complete")
    @Operation(summary = "完成认领", description = "标记认领为已完成（仅认领人本人可执行）")
    public ResponseEntity<ApiResponse<Void>> completeClaim(@PathVariable UUID claimId) {
        log.info("完成认领: claimId={}", claimId);

        // 此前 userId 硬编码 null → 服务层「必须是认领人」校验必然失败，接口恒 403（状态机断链）。
        // 统一走 CurrentUserProvider（缺失直接 401），不再新增第四处解析实现。
        UUID currentUserId = currentUserProvider.requireCurrentUserId();
        assetClaimService.completeClaim(claimId, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/claims/{claimId}/abandon")
    @Operation(summary = "放弃认领", description = "放弃资产认领；认领人本人或管理层（ADMIN/PROJECT_LEADER/TEAM_LEADER）可强制放弃")
    public ResponseEntity<ApiResponse<Void>> abandonClaim(
            @PathVariable UUID claimId,
            @Parameter(description = "放弃原因") @RequestParam(required = false) String note) {

        log.info("放弃认领: claimId={}", claimId);

        UUID currentUserId = currentUserProvider.requireCurrentUserId();
        assetClaimService.abandonClaim(claimId, currentUserId, note);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ==================== 标签管理 ====================

    /**
     * 获取资产的标签列表
     */
    @GetMapping("/{id}/tags")
    @PreAuthorize("@accessGuard.canReadAsset(#id, authentication)")
    @Operation(summary = "获取资产的标签列表", description = "获取指定资产的所有标签")
    public ResponseEntity<ApiResponse<List<AssetTag>>> getAssetTags(@PathVariable UUID id) {
        log.info("获取资产标签列表: assetId={}", id);
        
        List<AssetTag> tags = assetTagRepository.findByAssetId(id);
        return ResponseEntity.ok(ApiResponse.success(tags));
    }

    /**
     * 添加标签到资产
     */
    @PostMapping("/{id}/tags")
    @PreAuthorize("@accessGuard.canWriteAsset(#id, authentication)")
    @Operation(summary = "添加标签到资产", description = "为资产添加标签")
    public ResponseEntity<ApiResponse<AssetTag>> addAssetTag(
            @PathVariable UUID id,
            @Parameter(description = "标签名称") @RequestParam String tag) {
        log.info("添加资产标签: assetId={}, tag={}", id, tag);
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UUID currentUserId = getCurrentUserId(authentication);

        // t65：父资源不存在 → 404（此前会对不存在的资产静默写入孤儿标签行）
        subresourceGuard.requireAsset(id);

        if (assetTagRepository.existsByAssetIdAndTag(id, tag)) {
            return ResponseEntity.ok(ApiResponse.error(400, "标签已存在"));
        }
        
        AssetTag assetTag = AssetTag.builder()
                .assetId(id)
                .tag(tag)
                .createdBy(currentUserId)
                .createdAt(LocalDateTime.now())
                .build();
        
        AssetTag savedTag = assetTagRepository.save(Objects.requireNonNull(assetTag));
        log.info("资产标签添加成功: assetId={}, tag={}", id, tag);
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(savedTag));
    }

    /**
     * 移除资产的标签
     */
    @DeleteMapping("/{assetId}/tags/{tagId}")
    @PreAuthorize("@accessGuard.canWriteAsset(#assetId, authentication)")
    @Operation(summary = "移除资产的标签", description = "移除资产的指定标签")
    public ResponseEntity<ApiResponse<Void>> removeAssetTag(
            @PathVariable UUID assetId,
            @PathVariable UUID tagId) {
        log.info("移除资产标签: assetId={}, tagId={}", assetId, tagId);

        // t65：标签必须存在且属于该资产，否则 404（此前 deleteById 对任意 tagId 都返回 200）
        AssetTag tag = subresourceGuard.requireAssetTag(assetId, tagId);
        assetTagRepository.delete(tag);
        log.info("资产标签移除成功: assetId={}, tagId={}", assetId, tagId);
        
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ==================== 历史记录 ====================

    /**
     * 获取资产历史记录
     */
    @GetMapping("/{id}/history")
    @PreAuthorize("@accessGuard.canReadAsset(#id, authentication)")
    @Operation(summary = "获取资产历史记录", description = "获取资产的所有变更历史")
    public ResponseEntity<ApiResponse<List<AssetHistory>>> getAssetHistory(@PathVariable UUID id) {
        log.info("获取资产历史记录: assetId={}", id);
        
        List<AssetHistory> history = assetHistoryRepository.findByAssetIdOrderByCreatedAtDesc(id);
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    // ==================== "我的"相关API ====================

    /**
     * 获取我发起的资产访问申请
     */
    @GetMapping("/my/access-requests")
    @Operation(summary = "获取我发起的资产访问申请", description = "获取当前用户发起的所有资产访问申请")
    public ResponseEntity<ApiResponse<PageResponse<AssetAccess>>> getMyAccessRequests(
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "状态") @RequestParam(required = false) String status) {
        
        log.info("获取我发起的资产访问申请");
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UUID currentUserId = getCurrentUserId(authentication);
        
        PageResponse<AssetAccess> result = assetAccessService.findAll(page, size, null, currentUserId, status);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 获取我的认领列表
     */
    @GetMapping("/my/claims")
    @Operation(summary = "获取我的认领列表", description = "获取当前用户的所有认领记录")
    public ResponseEntity<ApiResponse<PageResponse<AssetClaim>>> getMyClaims(
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "状态") @RequestParam(required = false) String status) {
        
        log.info("获取我的认领列表");

        // 当前用户无法解析时返回 401：否则 userId=null 会让查询退化为「全部认领」，造成越权可见
        UUID currentUserId = currentUserProvider.requireCurrentUserId();

        PageResponse<AssetClaim> result = assetClaimService.findAll(page, size, null, currentUserId, status);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 获取待审批的资产访问申请
     */
    @GetMapping("/my/access-approval-requests")
    @Operation(summary = "获取待审批的资产访问申请", description = "获取当前用户需要审批的资产访问申请")
    public ResponseEntity<ApiResponse<PageResponse<AssetAccess>>> getMyAccessApprovalRequests(
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size) {
        
        log.info("获取待审批的资产访问申请");
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UUID currentUserId = getCurrentUserId(authentication);
        
        PageResponse<AssetAccess> result = assetAccessService.findPendingApprovalByUser(currentUserId, page, size);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 获取我已获授权的资产
     */
    @GetMapping("/my/authorized-assets")
    @Operation(summary = "获取我已获授权的资产", description = "获取当前用户已获授权访问的资产列表")
    public ResponseEntity<ApiResponse<List<Asset>>> getMyAuthorizedAssets() {
        log.info("获取我已获授权的资产");
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UUID currentUserId = getCurrentUserId(authentication);
        
        List<Asset> assets = assetService.findAuthorizedAssetsByUser(currentUserId);
        return ResponseEntity.ok(ApiResponse.success(assets));
    }

    /**
     * 写入一条资产变更历史。
     *
     * <p>此前 asset_history 只有查询接口、没有任何写入点，导致历史永远为空。
     */
    private void recordHistory(UUID assetId, String fieldName, String oldValue, String newValue, UUID changedBy) {
        if (assetId == null || fieldName == null) {
            return;
        }
        try {
            assetHistoryRepository.save(AssetHistory.builder()
                    .assetId(assetId)
                    .fieldName(fieldName)
                    .oldValue(oldValue)
                    .newValue(newValue)
                    .changedBy(changedBy)
                    .build());
        } catch (Exception e) {
            log.warn("记录资产历史失败: assetId={}, field={}", assetId, fieldName, e);
        }
    }

    /**
     * 仅当值发生变化时写入历史，避免产生大量无意义记录。
     */
    private void recordIfChanged(UUID assetId, String fieldName, String oldValue, String newValue, UUID changedBy) {
        if (Objects.equals(oldValue, newValue)) {
            return;
        }
        recordHistory(assetId, fieldName, oldValue, newValue, changedBy);
    }

    /**
     * 获取当前用户ID的辅助方法
     */
    private UUID getCurrentUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || "anonymousUser".equals(authentication.getName())) {
            return null;
        }
        return userService.findByUsername(authentication.getName())
                .map(user -> user.getId())
                .orElse(null);
    }

}
