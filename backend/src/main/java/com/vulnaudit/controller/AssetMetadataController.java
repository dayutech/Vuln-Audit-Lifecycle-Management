package com.vulnaudit.controller;

import com.vulnaudit.dto.ApiResponse;
import com.vulnaudit.entity.AssetAppMetadata;
import com.vulnaudit.entity.AssetAppMetadataHistory;
import com.vulnaudit.repository.AssetAppMetadataRepository;
import com.vulnaudit.repository.AssetAppMetadataHistoryRepository;
import com.vulnaudit.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 资产应用元数据控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/assets/{assetId}/metadata")
@RequiredArgsConstructor
@Tag(name = "资产元数据管理", description = "资产应用元数据CRUD接口")
@SecurityRequirement(name = "bearerAuth")
public class AssetMetadataController {

    private final AssetAppMetadataRepository metadataRepository;
    private final AssetAppMetadataHistoryRepository historyRepository;
    private final UserService userService;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    @Operation(summary = "获取资产元数据", description = "获取指定资产的应用元数据")
    public ResponseEntity<ApiResponse<AssetAppMetadata>> listMetadata(@PathVariable UUID assetId) {
        log.info("获取资产元数据: assetId={}", assetId);
        
        return metadataRepository.findByAssetId(assetId)
                .map(metadata -> ResponseEntity.ok(ApiResponse.success(metadata)))
                .orElse(ResponseEntity.ok(ApiResponse.error(404, "元数据不存在")));
    }

    @GetMapping("/detail/{id}")
    @Operation(summary = "获取元数据详情", description = "根据ID获取元数据详细信息")
    public ResponseEntity<ApiResponse<AssetAppMetadata>> getMetadata(
            @PathVariable UUID assetId,
            @PathVariable UUID id) {
        
        log.info("获取元数据详情: assetId={}, id={}", assetId, id);
        
        return metadataRepository.findById(Objects.requireNonNull(id, "id must not be null"))
                .map(metadata -> ResponseEntity.ok(ApiResponse.success(metadata)))
                .orElse(ResponseEntity.ok(ApiResponse.error(404, "元数据不存在")));
    }

    @PostMapping
    @Operation(summary = "创建元数据", description = "创建新的应用元数据")
    public ResponseEntity<ApiResponse<AssetAppMetadata>> createMetadata(
            @PathVariable UUID assetId,
            @RequestBody AssetAppMetadata metadata) {
        
        log.info("创建元数据: assetId={}", assetId);
        if (metadataRepository.existsByAssetId(assetId)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(400, "该资产已存在元数据，请使用更新接口"));
        }

        UUID currentUserId = getCurrentUserId();
        metadata.setAssetId(assetId);
        metadata.setClaimedBy(currentUserId);
        metadata.setVersion(1);
        if (metadata.getAdminPasswordEncrypted() != null && !metadata.getAdminPasswordEncrypted().isBlank()) {
            metadata.setAdminPasswordEncrypted(passwordEncoder.encode(metadata.getAdminPasswordEncrypted()));
        }
        AssetAppMetadata savedMetadata = metadataRepository.save(metadata);
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(savedMetadata));
    }

    @PutMapping
    @Operation(summary = "更新元数据", description = "按资产ID更新应用元数据")
    public ResponseEntity<ApiResponse<AssetAppMetadata>> updateMetadataByAsset(
            @PathVariable UUID assetId,
            @RequestBody AssetAppMetadata metadata,
            @Parameter(description = "变更原因") @RequestParam(required = false) String changeReason) {
        return metadataRepository.findByAssetId(assetId)
                .map(existing -> updateMetadataInternal(existing, metadata, changeReason))
                .orElse(ResponseEntity.ok(ApiResponse.error(404, "元数据不存在")));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新元数据", description = "更新应用元数据")
    public ResponseEntity<ApiResponse<AssetAppMetadata>> updateMetadata(
            @PathVariable UUID assetId,
            @PathVariable UUID id,
            @RequestBody AssetAppMetadata metadata,
            @Parameter(description = "变更原因") @RequestParam(required = false) String changeReason) {
        
        log.info("更新元数据: assetId={}, id={}", assetId, id);
        
        return metadataRepository.findById(Objects.requireNonNull(id, "id must not be null"))
                .map(existing -> updateMetadataInternal(existing, metadata, changeReason))
                .orElse(ResponseEntity.ok(ApiResponse.error(404, "元数据不存在")));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除元数据", description = "删除应用元数据")
    public ResponseEntity<ApiResponse<Void>> deleteMetadata(
            @PathVariable UUID assetId,
            @PathVariable UUID id) {
        
        log.info("删除元数据: assetId={}, id={}", assetId, id);
        
        UUID metadataId = Objects.requireNonNull(id, "id must not be null");
        if (!metadataRepository.existsById(metadataId)) {
            return ResponseEntity.ok(ApiResponse.error(404, "元数据不存在"));
        }
        
        metadataRepository.deleteById(metadataId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{id}/history")
    @Operation(summary = "获取元数据历史", description = "获取元数据的变更历史")
    public ResponseEntity<ApiResponse<List<AssetAppMetadataHistory>>> getMetadataHistory(
            @PathVariable UUID assetId,
            @PathVariable UUID id) {
        
        log.info("获取元数据历史: assetId={}, metadataId={}", assetId, id);
        
        List<AssetAppMetadataHistory> history = historyRepository.findByMetadataIdOrderByVersionDesc(id);
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    @GetMapping("/history")
    @Operation(summary = "获取资产元数据历史", description = "按资产ID获取元数据历史")
    public ResponseEntity<ApiResponse<List<AssetAppMetadataHistory>>> getMetadataHistoryByAsset(
            @Parameter(description = "资产ID", required = true)
            @PathVariable UUID assetId) {
        return metadataRepository.findByAssetId(assetId)
                .map(metadata -> ResponseEntity.ok(ApiResponse.success(
                        historyRepository.findByMetadataIdOrderByVersionDesc(metadata.getId())
                )))
                .orElse(ResponseEntity.ok(ApiResponse.error(404, "元数据不存在")));
    }

    @GetMapping("/versions/{version}")
    @Operation(summary = "获取历史版本", description = "按资产ID与版本号获取元数据历史版本")
    public ResponseEntity<ApiResponse<AssetAppMetadataHistory>> getMetadataVersionByAsset(
            @PathVariable UUID assetId,
            @PathVariable Integer version) {
        return metadataRepository.findByAssetId(assetId)
                .map(metadata -> {
                    AssetAppMetadataHistory history = historyRepository.findByMetadataIdAndVersion(metadata.getId(), version);
                    if (history == null) {
                        return ResponseEntity.ok(ApiResponse.<AssetAppMetadataHistory>error(404, "历史版本不存在"));
                    }
                    return ResponseEntity.ok(ApiResponse.success(history));
                })
                .orElse(ResponseEntity.ok(ApiResponse.<AssetAppMetadataHistory>error(404, "元数据不存在")));
    }

    @GetMapping("/{id}/history/{version}")
    @Operation(summary = "获取历史版本", description = "获取元数据的指定历史版本")
    public ResponseEntity<ApiResponse<AssetAppMetadataHistory>> getMetadataVersion(
            @PathVariable UUID assetId,
            @PathVariable UUID id,
            @Parameter(description = "历史版本ID") @PathVariable("version") UUID version) {
        
        log.info("获取元数据历史版本: assetId={}, metadataId={}, version={}", assetId, id, version);
        
        return historyRepository.findById(Objects.requireNonNull(version, "version must not be null"))
                .map(history -> ResponseEntity.ok(ApiResponse.success(history)))
                .orElse(ResponseEntity.ok(ApiResponse.error(404, "历史版本不存在")));
    }

    private ResponseEntity<ApiResponse<AssetAppMetadata>> updateMetadataInternal(
            AssetAppMetadata existing,
            AssetAppMetadata metadata,
            String changeReason) {
        UUID currentUserId = getCurrentUserId();
        String finalReason = (changeReason == null || changeReason.isBlank()) ? "更新元数据" : changeReason;
        AssetAppMetadataHistory history = Objects.requireNonNull(AssetAppMetadataHistory.builder()
                .metadataId(existing.getId())
                .version(existing.getVersion())
                .systemArchitecture(existing.getSystemArchitecture())
                .installPath(existing.getInstallPath())
                .startupScriptPath(existing.getStartupScriptPath())
                .configFilePath(existing.getConfigFilePath())
                .startupCommand(existing.getStartupCommand())
                .programmingLanguage(existing.getProgrammingLanguage())
                .framework(existing.getFramework())
                .databaseType(existing.getDatabaseType())
                .databaseConnection(existing.getDatabaseConnection())
                .adminUsername(existing.getAdminUsername())
                .adminPasswordEncrypted(existing.getAdminPasswordEncrypted())
                .servicePorts(existing.getServicePorts())
                .apiEndpoints(existing.getApiEndpoints())
                .specialNotes(existing.getSpecialNotes())
                .changeReason(finalReason)
                .changedBy(currentUserId)
                .build(), "history must not be null");
        historyRepository.save(history);

        if (metadata.getSystemArchitecture() != null) {
            existing.setSystemArchitecture(metadata.getSystemArchitecture());
        }
        if (metadata.getInstallPath() != null) {
            existing.setInstallPath(metadata.getInstallPath());
        }
        if (metadata.getStartupScriptPath() != null) {
            existing.setStartupScriptPath(metadata.getStartupScriptPath());
        }
        if (metadata.getConfigFilePath() != null) {
            existing.setConfigFilePath(metadata.getConfigFilePath());
        }
        if (metadata.getStartupCommand() != null) {
            existing.setStartupCommand(metadata.getStartupCommand());
        }
        if (metadata.getProgrammingLanguage() != null) {
            existing.setProgrammingLanguage(metadata.getProgrammingLanguage());
        }
        if (metadata.getFramework() != null) {
            existing.setFramework(metadata.getFramework());
        }
        if (metadata.getDatabaseType() != null) {
            existing.setDatabaseType(metadata.getDatabaseType());
        }
        if (metadata.getDatabaseConnection() != null) {
            existing.setDatabaseConnection(metadata.getDatabaseConnection());
        }
        if (metadata.getAdminUsername() != null) {
            existing.setAdminUsername(metadata.getAdminUsername());
        }
        if (metadata.getAdminPasswordEncrypted() != null && !metadata.getAdminPasswordEncrypted().isBlank()) {
            existing.setAdminPasswordEncrypted(passwordEncoder.encode(metadata.getAdminPasswordEncrypted()));
        }
        if (metadata.getServicePorts() != null) {
            existing.setServicePorts(metadata.getServicePorts());
        }
        if (metadata.getApiEndpoints() != null) {
            existing.setApiEndpoints(metadata.getApiEndpoints());
        }
        if (metadata.getSpecialNotes() != null) {
            existing.setSpecialNotes(metadata.getSpecialNotes());
        }
        existing.setClaimedBy(currentUserId);
        existing.setVersion(existing.getVersion() + 1);
        AssetAppMetadata updated = metadataRepository.save(existing);
        return ResponseEntity.ok(ApiResponse.success(updated));
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
