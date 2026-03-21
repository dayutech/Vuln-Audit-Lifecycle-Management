package com.vulnaudit.controller;

import com.vulnaudit.dto.*;
import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.AssetAccess;
import com.vulnaudit.entity.AssetClaim;
import com.vulnaudit.entity.AssetHistory;
import com.vulnaudit.entity.AssetTag;
import com.vulnaudit.repository.AssetHistoryRepository;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
    private final AssetTagRepository assetTagRepository;
    private final AssetHistoryRepository assetHistoryRepository;
    private final UserService userService;

    @PostMapping
    @Operation(summary = "创建资产", description = "创建新的资产记录")
    public ResponseEntity<ApiResponse<Asset>> createAsset(@Valid @RequestBody AssetCreateRequest request) {
        log.info("创建资产: name={}", request.getName());
        
        Asset asset = Asset.builder()
                .name(request.getName())
                .code(request.getCode())
                .targetType(request.getTargetType())
                .targetSource(request.getTargetSource())
                .description(request.getDescription())
                .priority(request.getPriority())
                .teamId(request.getTeamId())
                .ownerId(request.getOwnerId())
                .build();
        
        Asset createdAsset = assetService.createAsset(asset);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(createdAsset));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取资产详情", description = "根据ID获取资产详细信息")
    public ResponseEntity<ApiResponse<Asset>> getAsset(@PathVariable UUID id) {
        log.info("获取资产详情: id={}", id);
        
        return assetService.findById(id)
                .map(asset -> ResponseEntity.ok(ApiResponse.success(asset)))
                .orElse(ResponseEntity.ok(ApiResponse.error(404, "资产不存在")));
    }

    @GetMapping
    @Operation(summary = "分页查询资产列表", description = "支持按关键词、状态、团队、优先级筛选")
    public ResponseEntity<ApiResponse<PageResponse<Asset>>> listAssets(
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "搜索关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "资产状态") @RequestParam(required = false) String status,
            @Parameter(description = "团队ID") @RequestParam(required = false) UUID teamId,
            @Parameter(description = "优先级") @RequestParam(required = false) String priority) {
        
        log.info("分页查询资产: page={}, size={}, keyword={}, status={}", page, size, keyword, status);
        
        PageResponse<Asset> result = assetService.findAll(page, size, keyword, status, teamId, priority);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新资产", description = "更新资产信息")
    public ResponseEntity<ApiResponse<Asset>> updateAsset(
            @PathVariable UUID id,
            @Valid @RequestBody AssetUpdateRequest request) {
        
        log.info("更新资产: id={}", id);
        
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
        return ResponseEntity.ok(ApiResponse.success(updatedAsset));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除资产", description = "软删除资产（归档）")
    public ResponseEntity<ApiResponse<Void>> deleteAsset(@PathVariable UUID id) {
        log.info("删除资产: id={}", id);
        
        assetService.deleteAsset(id, null);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "更新资产状态", description = "更新资产状态")
    public ResponseEntity<ApiResponse<Asset>> updateStatus(
            @PathVariable UUID id,
            @Parameter(description = "新状态") @RequestParam String status) {
        
        log.info("更新资产状态: id={}, status={}", id, status);
        
        Asset updatedAsset = assetService.updateStatus(id, status, null);
        return ResponseEntity.ok(ApiResponse.success(updatedAsset));
    }

    // ==================== 访问申请相关 ====================

    @PostMapping("/{id}/access-requests")
    @Operation(summary = "申请资产访问权限", description = "创建资产访问申请")
    public ResponseEntity<ApiResponse<AssetAccess>> createAccessRequest(
            @PathVariable UUID id,
            @Valid @RequestBody AssetAccessRequest request) {
        
        log.info("申请资产访问权限: assetId={}", id);
        
        AssetAccess access = AssetAccess.builder()
                .assetId(id)
                .userId(null) // 从当前用户获取
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
    @Operation(summary = "审批访问申请", description = "审批资产访问申请")
    public ResponseEntity<ApiResponse<AssetAccess>> reviewAccessRequest(
            @PathVariable UUID requestId,
            @Valid @RequestBody AssetAccessReviewRequest request) {
        
        log.info("审批访问申请: requestId={}, approved={}", requestId, request.isApproved());
        
        AssetAccess reviewedAccess = assetAccessService.reviewAccessRequest(
                requestId, request.isApproved(), request.getReviewNote(), null);
        return ResponseEntity.ok(ApiResponse.success(reviewedAccess));
    }

    // ==================== 认领相关 ====================

    @PostMapping("/{id}/claims")
    @Operation(summary = "认领资产", description = "创建资产认领申请")
    public ResponseEntity<ApiResponse<AssetClaim>> createClaim(
            @PathVariable UUID id,
            @Valid @RequestBody AssetClaimRequest request) {
        
        log.info("认领资产: assetId={}", id);
        
        AssetClaim claim = AssetClaim.builder()
                .assetId(id)
                .userId(null) // 从当前用户获取
                .note(request.getNote())
                .build();
        
        AssetClaim createdClaim = assetClaimService.createClaim(claim);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(createdClaim));
    }

    @GetMapping("/{id}/claims")
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
    @Operation(summary = "审批认领申请", description = "审批资产认领申请")
    public ResponseEntity<ApiResponse<AssetClaim>> reviewClaim(
            @PathVariable UUID claimId,
            @Valid @RequestBody AssetAccessReviewRequest request) {
        
        log.info("审批认领申请: claimId={}, approved={}", claimId, request.isApproved());
        
        AssetClaim reviewedClaim = assetClaimService.reviewClaim(
                claimId, request.isApproved(), request.getReviewNote(), null);
        return ResponseEntity.ok(ApiResponse.success(reviewedClaim));
    }

    @PostMapping("/claims/{claimId}/complete")
    @Operation(summary = "完成认领", description = "标记认领为已完成")
    public ResponseEntity<ApiResponse<Void>> completeClaim(@PathVariable UUID claimId) {
        log.info("完成认领: claimId={}", claimId);
        
        assetClaimService.completeClaim(claimId, null);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/claims/{claimId}/abandon")
    @Operation(summary = "放弃认领", description = "放弃资产认领")
    public ResponseEntity<ApiResponse<Void>> abandonClaim(
            @PathVariable UUID claimId,
            @Parameter(description = "放弃原因") @RequestParam(required = false) String note) {
        
        log.info("放弃认领: claimId={}", claimId);
        
        assetClaimService.abandonClaim(claimId, null, note);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ==================== 标签管理 ====================

    /**
     * 获取资产的标签列表
     */
    @GetMapping("/{id}/tags")
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
        
        AssetTag savedTag = assetTagRepository.save(assetTag);
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
        
        assetTagRepository.deleteById(tagId);
        log.info("资产标签移除成功: assetId={}, tagId={}", assetId, tagId);
        
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ==================== 历史记录 ====================

    /**
     * 获取资产历史记录
     */
    @GetMapping("/{id}/history")
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
        
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UUID currentUserId = getCurrentUserId(authentication);
        
        PageResponse<AssetClaim> result = assetClaimService.findAll(page, size, null, currentUserId, status);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    /**
     * 获取当前用户ID的辅助方法
     */
    private UUID getCurrentUserId(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        String username = authentication.getName();
        return userService.findByUsername(username)
                .map(user -> user.getId())
                .orElse(null);
    }

}
