package com.vulnaudit.controller;

import com.vulnaudit.dto.ApiResponse;
import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.AssetGroup;
import com.vulnaudit.entity.AssetGroupRelation;
import com.vulnaudit.repository.AssetGroupRepository;
import com.vulnaudit.repository.AssetGroupRelationRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

/**
 * 资产分组管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/asset-groups")
@RequiredArgsConstructor
@Tag(name = "资产分组管理", description = "资产分组CRUD、分组资产管理接口")
@SecurityRequirement(name = "bearerAuth")
public class AssetGroupController {

    private final AssetGroupRepository assetGroupRepository;
    private final AssetGroupRelationRepository assetGroupRelationRepository;

    @PostMapping
    @Operation(summary = "创建资产分组", description = "创建新的资产分组")
    public ResponseEntity<ApiResponse<AssetGroup>> createGroup(@Valid @RequestBody @Nonnull AssetGroup group) {
        log.info("创建资产分组: name={}, type={}", group.getName(), group.getType());

        if (assetGroupRepository.existsByName(group.getName())) {
            return ResponseEntity.ok(ApiResponse.error(409, "分组名称已存在"));
        }

        AssetGroup createdGroup = assetGroupRepository.save(group);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(createdGroup));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取分组详情", description = "根据ID获取分组详细信息")
    public ResponseEntity<ApiResponse<AssetGroup>> getGroup(@PathVariable @Nullable UUID id) {
        log.info("获取资产分组详情: id={}", id);

        if (id == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "分组ID不能为空"));
        }
        return assetGroupRepository.findById(id)
                .map(group -> ResponseEntity.ok(ApiResponse.success(group)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(404, "分组不存在")));
    }

    @GetMapping
    @Operation(summary = "分页查询分组列表", description = "支持按类型筛选")
    public ResponseEntity<ApiResponse<PageResponse<AssetGroup>>> listGroups(
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "分组类型") @RequestParam(required = false) @Nullable String type) {

        log.info("分页查询资产分组: page={}, size={}, type={}", page, size, type);

        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<AssetGroup> groupPage;

        if (type != null && !type.isEmpty()) {
            groupPage = assetGroupRepository.findByType(type, pageable);
        } else {
            groupPage = assetGroupRepository.findAll(pageable);
        }

        PageResponse<AssetGroup> response = PageResponse.<AssetGroup>builder()
                .content(groupPage.getContent())
                .totalElements(groupPage.getTotalElements())
                .totalPages(groupPage.getTotalPages())
                .currentPage(groupPage.getNumber())
                .pageSize(groupPage.getSize())
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/type/{type}")
    @Operation(summary = "按类型获取分组", description = "获取指定类型的所有分组")
    public ResponseEntity<ApiResponse<List<AssetGroup>>> listGroupsByType(@PathVariable @Nonnull String type) {
        log.info("按类型获取分组: type={}", type);

        List<AssetGroup> groups = assetGroupRepository.findByType(type);
        return ResponseEntity.ok(ApiResponse.success(groups));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新分组", description = "更新分组信息")
    public ResponseEntity<ApiResponse<AssetGroup>> updateGroup(
            @PathVariable @Nullable UUID id,
            @Valid @RequestBody @Nonnull AssetGroup group) {

        log.info("更新资产分组: id={}", id);

        if (id == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "分组ID不能为空"));
        }
        return assetGroupRepository.findById(id)
                .map(existingGroup -> {
                    if (group.getName() != null) {
                        existingGroup.setName(group.getName());
                    }
                    if (group.getDescription() != null) {
                        existingGroup.setDescription(group.getDescription());
                    }
                    AssetGroup updatedGroup = assetGroupRepository.save(Objects.requireNonNull(existingGroup));
                    return ResponseEntity.ok(ApiResponse.success(updatedGroup));
                })
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(404, "分组不存在")));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除分组", description = "删除资产分组")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> deleteGroup(@PathVariable @Nullable UUID id) {
        log.info("删除资产分组: id={}", id);

        if (id == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "分组ID不能为空"));
        }
        if (!assetGroupRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(404, "分组不存在"));
        }

        assetGroupRelationRepository.deleteByGroupId(id);
        assetGroupRepository.deleteById(id);

        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ==================== 分组资产管理 ====================

    @PostMapping("/{id}/assets")
    @Operation(summary = "添加资产到分组", description = "将资产添加到分组")
    public ResponseEntity<ApiResponse<Void>> addAssetToGroup(
            @PathVariable @Nullable UUID id,
            @Parameter(description = "资产ID") @RequestParam @Nullable UUID assetId) {

        log.info("添加资产到分组: groupId={}, assetId={}", id, assetId);

        if (id == null || assetId == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "分组ID和资产ID不能为空"));
        }
        
        if (!assetGroupRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(404, "分组不存在"));
        }
        
        // 检查是否已存在关联
        if (assetGroupRelationRepository.existsByGroupIdAndAssetId(id, assetId)) {
            return ResponseEntity.ok(ApiResponse.error(409, "资产已在分组中"));
        }
        
        AssetGroupRelation relation = AssetGroupRelation.builder()
                .groupId(id)
                .assetId(assetId)
                .build();
        assetGroupRelationRepository.save(Objects.requireNonNull(relation));
        
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/{groupId}/assets/{assetId}")
    @Operation(summary = "从分组移除资产", description = "将资产从分组中移除")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> removeAssetFromGroup(
            @PathVariable @Nullable UUID groupId,
            @PathVariable @Nullable UUID assetId) {

        log.info("从分组移除资产: groupId={}, assetId={}", groupId, assetId);

        if (groupId == null || assetId == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "分组ID和资产ID不能为空"));
        }
        
        assetGroupRelationRepository.deleteByGroupIdAndAssetId(groupId, assetId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{id}/assets")
    @Operation(summary = "获取分组中的资产", description = "获取分组中的所有资产ID")
    public ResponseEntity<ApiResponse<List<UUID>>> getGroupAssets(@PathVariable @Nullable UUID id) {
        log.info("获取分组中的资产: groupId={}", id);

        List<AssetGroupRelation> relations = assetGroupRelationRepository.findByGroupId(id);
        List<UUID> assetIds = relations.stream()
                .map(AssetGroupRelation::getAssetId)
                .filter(assetId -> assetId != null)
                .toList();

        return ResponseEntity.ok(ApiResponse.success(assetIds));
    }
}
