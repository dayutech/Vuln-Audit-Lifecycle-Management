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
import org.springframework.security.access.prepost.PreAuthorize;
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
        AssetVersion saved;
        try {
            // saveAndFlush：让 (asset_id, version_no) 唯一键冲突在可控范围内抛出
            saved = assetVersionRepository.saveAndFlush(Objects.requireNonNull(version, "version must not be null"));
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            if (com.vulnaudit.util.PersistenceErrors.isUniqueViolation(e)) {
                // 并发创建同一版本号时收敛为 409（而不是 500），由前端重试
                log.warn("资产版本号冲突: assetId={}, versionNo={}", assetId, nextVersionNo);
                throw new BusinessException(ErrorCode.CONFLICT, "资产版本号冲突，请重试");
            }
            String detail = com.vulnaudit.util.PersistenceErrors.detail(e);
            log.warn("资产版本数据完整性校验失败: assetId={}, detail={}", assetId, detail);
            throw new BusinessException(ErrorCode.BAD_REQUEST, "资产版本数据不完整或不符合约束: " + detail);
        }

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

    /**
     * 删除资产版本（t34）
     *
     * <p>此前该路由不存在，前端调用落到默认错误处理返回 500；这里补齐删除语义：
     * <ul>
     *     <li>版本必须属于该资产，否则 404；</li>
     *     <li>同事务内先删该版本的产品概览元数据，再删版本本身（{@code @Transactional} 保证
     *         JPA delete 在事务内执行，避免 "no transaction is in progress" 一类的 500）；</li>
     *     <li>若删除的是默认/最新版本，把剩余的最高版本提升为默认/最新。</li>
     * </ul>
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("@accessGuard.canWriteAsset(#assetId, authentication)")
    @Operation(summary = "删除资产版本", description = "删除指定资产版本及其产品概览元数据")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> deleteVersion(
            @PathVariable UUID assetId,
            @PathVariable UUID id) {
        log.info("删除资产版本: assetId={}, versionId={}", assetId, id);

        AssetVersion version = assetVersionRepository.findByIdAndAssetId(id, assetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "资产版本不存在或不属于该资产"));

        boolean wasDefault = Boolean.TRUE.equals(version.getIsDefault());
        boolean wasLatest = Boolean.TRUE.equals(version.getIsLatest());

        // 同一事务内先删产品概览元数据，避免残留孤儿行
        assetMetadataBaseRepository.findByAssetIdAndAssetVersionId(assetId, id)
                .ifPresent(assetMetadataBaseRepository::delete);

        assetVersionRepository.delete(version);
        assetVersionRepository.flush();

        if (wasDefault || wasLatest) {
            assetVersionRepository.findFirstByAssetIdOrderByVersionNoDesc(assetId).ifPresent(next -> {
                if (wasDefault) {
                    next.setIsDefault(true);
                }
                if (wasLatest) {
                    next.setIsLatest(true);
                }
                assetVersionRepository.save(next);
                log.info("删除后提升版本标记: assetId={}, promotedVersionNo={}, default={}, latest={}",
                        assetId, next.getVersionNo(), wasDefault, wasLatest);
            });
        }

        return ResponseEntity.ok(ApiResponse.success(null));
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
