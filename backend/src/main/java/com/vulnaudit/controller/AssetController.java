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
    private final com.vulnaudit.security.CurrentUserProvider currentUserProvider;
    private final com.vulnaudit.service.AccessGuard accessGuard;
    private final AssetTagRepository assetTagRepository;
    private final AssetHistoryRepository assetHistoryRepository;
    private final AssetRepository assetRepository;
    private final UserService userService;

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
    @Operation(summary = "创建资产", description = "创建新的资产记录")
    public ResponseEntity<ApiResponse<Asset>> createAsset(@Valid @RequestBody AssetCreateRequest request) {
        log.info("创建资产: name={}", request.getName());

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UUID currentUserId = getCurrentUserId(authentication);
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(401, "未授权"));
        }

        Asset asset = Asset.builder()
                .name(request.getName())
                .targetType(request.getTargetType())
                .targetSource(request.getTargetSource())
                .description(request.getDescription())
                .priority(request.getPriority())
                .status(request.getStatus())
                .teamId(request.getTeamId())
                .ownerId(currentUserId)
                .build();

        Asset createdAsset = assetService.createAsset(asset);
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

        return assetService.findByIdWithDetails(id)
                .map(asset -> ResponseEntity.ok(ApiResponse.success(asset)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(404, "资产不存在")));
    }

    @GetMapping
    @Operation(summary = "分页查询资产列表",
            description = "支持按关键词、状态、团队、优先级筛选；返回范围与详情共用同一可见性判定（AccessGuard.visibleAssetIds）")
    public ResponseEntity<ApiResponse<PageResponse<AssetResponse>>> listAssets(
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "搜索关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "资产状态") @RequestParam(required = false) String status,
            @Parameter(description = "团队ID") @RequestParam(required = false) UUID teamId,
            @Parameter(description = "优先级") @RequestParam(required = false) String priority) {

        log.info("分页查询资产: page={}, size={}, keyword={}, status={}", page, size, keyword, status);

        // 单一事实来源：列表范围 = 详情判定（visibleAssetIds 为 null 表示管理层不限制）。
        // 此前列表未做任何过滤，导致「列表可见 → 点进去 403」。
        java.util.Set<UUID> visibleAssetIds = accessGuard.visibleAssetIds(
                SecurityContextHolder.getContext().getAuthentication());

        PageResponse<AssetResponse> result = assetService.findAllWithDetails(
                page, size, keyword, status, teamId, priority, visibleAssetIds);
        return ResponseEntity.ok(ApiResponse.success(result));
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
    @Operation(summary = "删除资产", description = "软删除资产（归档）")
    public ResponseEntity<ApiResponse<Void>> deleteAsset(@PathVariable UUID id) {
        log.info("删除资产: id={}", id);
        
        UUID operatorId = getCurrentUserId(SecurityContextHolder.getContext().getAuthentication());
        String beforeStatus = assetRepository.findById(id).map(Asset::getStatus).orElse(null);
        assetService.deleteAsset(id, null);
        recordHistory(id, "status", beforeStatus, "archived", operatorId);
        return ResponseEntity.ok(ApiResponse.success(null));
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
    @Operation(summary = "申请资产访问权限", description = "创建资产访问申请")
    public ResponseEntity<ApiResponse<AssetAccess>> createAccessRequest(
            @PathVariable UUID id,
            @Valid @RequestBody AssetAccessRequest request) {

        log.info("申请资产访问权限: assetId={}", id);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
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
    @Operation(summary = "添加标签到资产", description = "为资产添加标签")
    public ResponseEntity<ApiResponse<AssetTag>> addAssetTag(
            @PathVariable UUID id,
            @Parameter(description = "标签名称") @RequestParam String tag) {
        log.info("添加资产标签: assetId={}, tag={}", id, tag);
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UUID currentUserId = getCurrentUserId(authentication);
        
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
    @Operation(summary = "移除资产的标签", description = "移除资产的指定标签")
    public ResponseEntity<ApiResponse<Void>> removeAssetTag(
            @PathVariable UUID assetId,
            @PathVariable UUID tagId) {
        log.info("移除资产标签: assetId={}, tagId={}", assetId, tagId);
        
        assetTagRepository.deleteById(Objects.requireNonNull(tagId, "tagId must not be null"));
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
