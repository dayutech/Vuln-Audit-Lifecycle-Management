package com.vulnaudit.controller;

import com.vulnaudit.dto.ApiResponse;
import com.vulnaudit.dto.AssetVersionCreateRequest;
import com.vulnaudit.entity.AssetVersion;
import com.vulnaudit.entity.metadata.AssetMetadataBase;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.AssetVersionRepository;
import com.vulnaudit.repository.metadata.AssetMetadataBaseRepository;
import com.vulnaudit.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/assets/{assetId}/versions")
@RequiredArgsConstructor
@Tag(name = "资产版本管理", description = "资产版本管理接口")
@SecurityRequirement(name = "bearerAuth")
public class AssetVersionController {

    private final AssetVersionRepository assetVersionRepository;
    private final AssetMetadataBaseRepository assetMetadataBaseRepository;
    private final UserService userService;

    @GetMapping
    @Operation(summary = "获取资产版本列表", description = "获取指定资产的全部版本")
    @Transactional
    public ResponseEntity<ApiResponse<List<AssetVersion>>> listVersions(@PathVariable UUID assetId) {
        log.info("获取资产版本列表: assetId={}", assetId);
        List<AssetVersion> versions = assetVersionRepository.findByAssetIdOrderByVersionNoDesc(assetId);
        return ResponseEntity.ok(ApiResponse.success(versions));
    }

    @GetMapping("/default")
    @Operation(summary = "获取默认版本", description = "获取资产当前默认版本")
    @Transactional
    public ResponseEntity<ApiResponse<AssetVersion>> getDefaultVersion(@PathVariable UUID assetId) {
        return assetVersionRepository.findByAssetIdAndIsDefaultTrue(assetId)
                .or(() -> assetVersionRepository.findFirstByAssetIdOrderByVersionNoDesc(assetId))
                .map(version -> ResponseEntity.ok(ApiResponse.success(version)))
                .orElse(ResponseEntity.ok(ApiResponse.error(404, "资产默认版本不存在")));
    }

    @PostMapping
    @Operation(summary = "创建资产版本", description = "为资产创建新版本")
    @Transactional
    public ResponseEntity<ApiResponse<AssetVersion>> createVersion(
            @PathVariable UUID assetId,
            @RequestBody(required = false) AssetVersionCreateRequest request,
            @RequestParam(defaultValue = "false") boolean setDefault) {
        log.info("创建资产版本: assetId={}, setDefault={}", assetId, setDefault);

        Integer nextVersionNo = assetVersionRepository.findFirstByAssetIdOrderByVersionNoDesc(assetId)
                .map(version -> version.getVersionNo() + 1)
                .orElse(1);

        boolean hasExistingVersion = !assetVersionRepository.findByAssetIdOrderByVersionNoDesc(assetId).isEmpty();
        boolean shouldSetDefault = setDefault || !hasExistingVersion;

        UUID currentUserId = getCurrentUserId();

        AssetVersion version = AssetVersion.builder()
                .assetId(assetId)
                .versionNo(nextVersionNo)
                .name(resolveName(request, nextVersionNo))
                .description(request == null ? null : request.getDescription())
                .isDefault(shouldSetDefault)
                .isLatest(true)
                .status(request == null || request.getStatus() == null || request.getStatus().isBlank() ? "active" : request.getStatus())
                .createdBy(currentUserId)
                .build();

        if (shouldSetDefault) {
            assetVersionRepository.clearDefaultByAssetId(assetId);
        }
        assetVersionRepository.clearLatestByAssetId(assetId);
        AssetVersion saved = assetVersionRepository.save(Objects.requireNonNull(version, "version must not be null"));

        if (shouldInheritBaseOverview(request)) {
            copyBaseOverview(assetId, saved.getId(), request.getInheritFromVersionId(), currentUserId);
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(saved));
    }

    @PutMapping("/{id}/default")
    @Operation(summary = "切换默认版本", description = "将指定版本设置为默认版本")
    @Transactional
    public ResponseEntity<ApiResponse<AssetVersion>> setDefaultVersion(
            @PathVariable UUID assetId,
            @PathVariable UUID id) {
        log.info("切换默认资产版本: assetId={}, versionId={}", assetId, id);

        AssetVersion version = assetVersionRepository.findByIdAndAssetId(id, assetId).orElse(null);
        if (version == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "版本不存在"));
        }

        assetVersionRepository.clearDefaultByAssetId(assetId);
        version.setIsDefault(true);
        AssetVersion saved = assetVersionRepository.save(version);
        return ResponseEntity.ok(ApiResponse.success(saved));
    }

    @PutMapping("/{id}/latest")
    @Operation(summary = "切换最新版本", description = "将指定版本设置为最新版本")
    @Transactional
    public ResponseEntity<ApiResponse<AssetVersion>> setLatestVersion(
            @PathVariable UUID assetId,
            @PathVariable UUID id) {
        log.info("切换最新资产版本: assetId={}, versionId={}", assetId, id);

        AssetVersion version = assetVersionRepository.findByIdAndAssetId(id, assetId).orElse(null);
        if (version == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "版本不存在"));
        }

        assetVersionRepository.clearLatestByAssetId(assetId);
        version.setIsLatest(true);
        AssetVersion saved = assetVersionRepository.save(version);
        return ResponseEntity.ok(ApiResponse.success(saved));
    }

    private String resolveName(AssetVersionCreateRequest request, Integer versionNo) {
        if (request == null || request.getName() == null || request.getName().isBlank()) {
            return "v" + versionNo;
        }
        return request.getName();
    }

    private boolean shouldInheritBaseOverview(AssetVersionCreateRequest request) {
        return request != null && Boolean.TRUE.equals(request.getInheritBaseOverview());
    }

    private void copyBaseOverview(UUID assetId, UUID targetVersionId, UUID sourceVersionId, UUID currentUserId) {
        if (sourceVersionId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请选择要带入产品概览的来源版本");
        }

        AssetVersion sourceVersion = assetVersionRepository.findByIdAndAssetId(sourceVersionId, assetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "来源版本不存在"));

        AssetMetadataBase sourceBase = assetMetadataBaseRepository.findByAssetIdAndAssetVersionId(assetId, sourceVersionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "来源版本不存在产品概览信息"));

        if (assetMetadataBaseRepository.findByAssetIdAndAssetVersionId(assetId, targetVersionId).isPresent()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "目标版本已存在产品概览信息");
        }

        AssetMetadataBase targetBase = new AssetMetadataBase();
        targetBase.setAssetId(assetId);
        targetBase.setAssetVersionId(targetVersionId);
        targetBase.setClaimedBy(currentUserId);
        targetBase.setVersion(1);
        targetBase.setSystemArchitecture(sourceBase.getSystemArchitecture());
        targetBase.setRuntimePlatform(sourceBase.getRuntimePlatform());
        targetBase.setDeploymentMethod(sourceBase.getDeploymentMethod());
        targetBase.setProgrammingLanguagesJson(sourceBase.getProgrammingLanguagesJson());
        targetBase.setProgrammingLanguage(sourceBase.getProgrammingLanguage());
        targetBase.setFramework(sourceBase.getFramework());
        targetBase.setCopiedFromAssetVersionId(sourceVersion.getId());
        assetMetadataBaseRepository.save(targetBase);
    }

    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || "anonymousUser".equals(authentication.getName())) {
            return null;
        }
        return userService.findByUsername(authentication.getName())
                .map(user -> user.getId())
                .orElse(null);
    }
}
