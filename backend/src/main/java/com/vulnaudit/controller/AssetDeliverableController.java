package com.vulnaudit.controller;

import com.vulnaudit.dto.ApiResponse;
import com.vulnaudit.entity.AssetDeliverable;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.AssetDeliverableRepository;
import com.vulnaudit.repository.AssetVersionRepository;
import com.vulnaudit.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "资产交付物管理", description = "资产交付物上传、下载、删除接口")
@SecurityRequirement(name = "bearerAuth")
public class AssetDeliverableController {

    private final AssetDeliverableRepository assetDeliverableRepository;
    private final AssetVersionRepository assetVersionRepository;
    private final UserService userService;

    private static final String UPLOAD_DIR = "./uploads/deliverables/";

    @GetMapping("/assets/{assetId}/deliverables")
    @Operation(summary = "获取资产交付物列表", description = "获取指定资产版本的交付物列表")
    public ResponseEntity<ApiResponse<List<AssetDeliverable>>> listDeliverables(
            @PathVariable UUID assetId,
            @RequestParam(required = false) UUID assetVersionId,
            @RequestParam(defaultValue = "false") boolean allVersions) {
        if (allVersions) {
            List<AssetDeliverable> deliverables = assetDeliverableRepository
                    .findByAssetIdAndStatusOrderByCreatedAtDesc(assetId, "active");
            return ResponseEntity.ok(ApiResponse.success(deliverables));
        }
        UUID resolvedVersionId = resolveAssetVersionId(assetId, assetVersionId);
        List<AssetDeliverable> deliverables = assetDeliverableRepository
                .findByAssetIdAndAssetVersionIdAndStatusOrderByCreatedAtDesc(assetId, resolvedVersionId, "active");
        return ResponseEntity.ok(ApiResponse.success(deliverables));
    }

    @PostMapping("/assets/{assetId}/deliverables")
    @Operation(summary = "上传资产交付物", description = "上传漏洞利用代码、POC、EXP、说明文档等交付物")
    public ResponseEntity<ApiResponse<AssetDeliverable>> uploadDeliverable(
            @PathVariable UUID assetId,
            @RequestParam(required = false) UUID assetVersionId,
            @Parameter(description = "文件") @RequestParam("file") MultipartFile file,
            @Parameter(description = "交付物类型") @RequestParam String deliverableType,
            @Parameter(description = "说明") @RequestParam(required = false) String description) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "上传文件不能为空");
        }
        UUID resolvedVersionId = resolveAssetVersionId(assetId, assetVersionId);

        Path uploadPath = Paths.get(UPLOAD_DIR + assetId);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String originalFilename = resolveUploadFileName(file);
        String storedFilename = UUID.randomUUID() + "_" + originalFilename;
        Path filePath = uploadPath.resolve(storedFilename);
        Files.copy(file.getInputStream(), filePath);

        AssetDeliverable deliverable = AssetDeliverable.builder()
                .assetId(assetId)
                .assetVersionId(resolvedVersionId)
                .name(originalFilename)
                .deliverableType(deliverableType)
                .description(description)
                .fileSize(file.getSize())
                .filePath(filePath.toString())
                .checksum(calculateSha256(filePath))
                .status("active")
                .uploadedBy(getCurrentUserId())
                .build();

        AssetDeliverable saved = assetDeliverableRepository.save(Objects.requireNonNull(deliverable));
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(saved));
    }

    @GetMapping("/asset-deliverables/{id}/download")
    @Operation(summary = "下载交付物", description = "下载指定交付物")
    public ResponseEntity<Resource> downloadDeliverable(@PathVariable UUID id) {
        AssetDeliverable deliverable = assetDeliverableRepository.findById(Objects.requireNonNull(id, "id must not be null")).orElse(null);
        if (deliverable == null) {
            return ResponseEntity.notFound().build();
        }
        try {
            Path filePath = Paths.get(deliverable.getFilePath());
            Resource resource = new UrlResource(Objects.requireNonNull(filePath.toUri()));
            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok()
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_OCTET_STREAM))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + deliverable.getName() + "\"")
                    .body(resource);
        } catch (MalformedURLException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/asset-deliverables/{id}")
    @Operation(summary = "删除交付物", description = "删除指定交付物（软删除，归档）")
    public ResponseEntity<ApiResponse<Void>> deleteDeliverable(@PathVariable UUID id) {
        AssetDeliverable deliverable = assetDeliverableRepository.findById(Objects.requireNonNull(id, "id must not be null")).orElse(null);
        if (deliverable == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(404, "交付物不存在"));
        }
        deliverable.setStatus("archived");
        assetDeliverableRepository.save(deliverable);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private UUID resolveAssetVersionId(UUID assetId, UUID assetVersionId) {
        if (assetVersionId != null) {
            boolean exists = assetVersionRepository.findByIdAndAssetId(assetVersionId, assetId).isPresent();
            if (!exists) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "资产版本不存在或不属于当前资产");
            }
            return assetVersionId;
        }
        return assetVersionRepository.findByAssetIdAndIsDefaultTrue(assetId)
                .map(version -> version.getId())
                .orElse(null);
    }

    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || "anonymousUser".equals(authentication.getName())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        String principal = authentication.getName();
        Optional<UUID> userIdByUsername = userService.findByUsername(principal).map(user -> user.getId());
        if (userIdByUsername.isPresent()) {
            return userIdByUsername.get();
        }
        try {
            UUID userId = UUID.fromString(principal);
            return userService.findById(userId)
                    .map(user -> user.getId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
    }

    private String resolveUploadFileName(MultipartFile file) {
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            return "unnamed-file";
        }
        return originalFilename;
    }

    private String calculateSha256(Path path) {
        try (InputStream inputStream = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            byte[] hashBytes = digest.digest();
            StringBuilder builder = new StringBuilder(hashBytes.length * 2);
            for (byte b : hashBytes) {
                builder.append(String.format("%02x", b));
            }
            return "sha256:" + builder;
        } catch (IOException | NoSuchAlgorithmException e) {
            log.warn("计算文件校验值失败: path={}, reason={}", path, e.getMessage());
            return null;
        }
    }
}
