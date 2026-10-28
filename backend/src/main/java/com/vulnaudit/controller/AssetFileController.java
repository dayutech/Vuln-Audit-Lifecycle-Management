package com.vulnaudit.controller;

import com.vulnaudit.dto.ApiResponse;
import com.vulnaudit.entity.AssetFile;
import com.vulnaudit.entity.AssetFileVersion;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.AssetFileRepository;
import com.vulnaudit.repository.AssetFileVersionRepository;
import com.vulnaudit.repository.AssetVersionRepository;
import com.vulnaudit.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.IOException;
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

/**
 * 资产文件管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "资产文件管理", description = "资产文件上传、下载、版本管理接口")
@SecurityRequirement(name = "bearerAuth")
public class AssetFileController {

    private final AssetFileRepository assetFileRepository;
    private final AssetFileVersionRepository assetFileVersionRepository;
    private final AssetVersionRepository assetVersionRepository;
    private final UserService userService;
    
    private static final String UPLOAD_DIR = "./uploads/assets/";

    @GetMapping("/assets/{assetId}/files")
    @PreAuthorize("@accessGuard.canReadAsset(#assetId, authentication)")
    @Operation(summary = "获取资产文件列表", description = "获取指定资产的所有文件")
    public ResponseEntity<ApiResponse<List<AssetFile>>> listAssetFiles(
            @PathVariable UUID assetId,
            @RequestParam(required = false) UUID assetVersionId,
            @RequestParam(defaultValue = "false") boolean allVersions) {
        if (allVersions) {
            log.info("获取资产文件列表(所有版本): assetId={}", assetId);
            List<AssetFile> files = assetFileRepository.findByAssetIdAndStatusOrderByCreatedAtDesc(assetId, "active");
            return ResponseEntity.ok(ApiResponse.success(files));
        }
        UUID resolvedVersionId = resolveAssetVersionId(assetId, assetVersionId);
        log.info("获取资产文件列表: assetId={}, assetVersionId={}", assetId, resolvedVersionId);

        List<AssetFile> files = findFilesByAssetAndVersion(assetId, resolvedVersionId);
        return ResponseEntity.ok(ApiResponse.success(files));
    }

    @GetMapping("/asset-files/{id}")
    @PreAuthorize("@accessGuard.canReadAssetFile(#id, authentication)")
    @Operation(summary = "获取文件详情", description = "根据ID获取文件详细信息")
    public ResponseEntity<ApiResponse<AssetFile>> getFile(@PathVariable UUID id) {
        log.info("获取文件详情: id={}", id);
        
        return assetFileRepository.findById(Objects.requireNonNull(id, "id must not be null"))
                .map(file -> ResponseEntity.ok(ApiResponse.success(file)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(404, "文件不存在")));
    }

    @PostMapping("/assets/{assetId}/files")
    @PreAuthorize("@accessGuard.canWriteAsset(#assetId, authentication)")
    @Operation(summary = "上传资产文件", description = "上传文件到指定资产")
    public ResponseEntity<ApiResponse<AssetFile>> uploadFile(
            @PathVariable UUID assetId,
            @RequestParam(required = false) UUID assetVersionId,
            @Parameter(description = "文件") @RequestParam("file") MultipartFile file,
            @Parameter(description = "文件分类") @RequestParam String category,
            @Parameter(description = "描述") @RequestParam(required = false) String description) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "上传文件不能为空");
        }
        UUID resolvedVersionId = resolveAssetVersionId(assetId, assetVersionId);
        String originalFilename = resolveUploadFileName(file);
        log.info("上传资产文件: assetId={}, assetVersionId={}, fileName={}", assetId, resolvedVersionId, originalFilename);
        
        // 创建上传目录
        Path uploadPath = Paths.get(UPLOAD_DIR + assetId);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        
        // 生成唯一文件名
        String storedFilename = UUID.randomUUID() + "_" + originalFilename;
        Path filePath = uploadPath.resolve(storedFilename);
        
        // 保存文件
        Files.copy(file.getInputStream(), filePath);
        
        // 计算文件大小
        long fileSize = file.getSize();
        
        UUID currentUserId = getCurrentUserId();
        String checksum = calculateSha256(filePath);

        AssetFile assetFile = AssetFile.builder()
                .assetId(assetId)
                .assetVersionId(resolvedVersionId)
                .name(originalFilename)
                .category(category)
                .description(description)
                .fileSize(fileSize)
                .filePath(filePath.toString())
                .checksum(checksum)
                .version(1)
                .status("active")
                .uploadedBy(currentUserId)
                .build();
        
        AssetFile savedFile = assetFileRepository.save(Objects.requireNonNull(assetFile, "assetFile must not be null"));
        
        return ResponseEntity.status(201)
                .body(ApiResponse.success(savedFile));
    }

    @GetMapping("/asset-files/{id}/download")
    @PreAuthorize("@accessGuard.canReadAssetFile(#id, authentication)")
    @Operation(summary = "下载文件", description = "下载指定文件")
    public ResponseEntity<Resource> downloadFile(@PathVariable UUID id) {
        log.info("下载文件: id={}", id);
        
        AssetFile assetFile = assetFileRepository.findById(Objects.requireNonNull(id, "id must not be null"))
                .orElse(null);
        
        if (assetFile == null) {
            return ResponseEntity.notFound().build();
        }
        
        try {
            Path filePath = Paths.get(assetFile.getFilePath());
            Resource resource = new UrlResource(Objects.requireNonNull(filePath.toUri(), "uri must not be null"));
            
            if (resource.exists()) {
                return ResponseEntity.ok()
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_OCTET_STREAM, "mediaType must not be null"))
                        .header(HttpHeaders.CONTENT_DISPOSITION, 
                                "attachment; filename=\"" + assetFile.getName() + "\"")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException e) {
            log.error("文件路径错误: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    @DeleteMapping("/asset-files/{id}")
    @PreAuthorize("@accessGuard.canWriteAssetFile(#id, authentication)")
    @Operation(summary = "删除文件", description = "删除指定文件（软删除，归档）")
    public ResponseEntity<ApiResponse<Void>> deleteFile(@PathVariable UUID id) {
        log.info("删除文件: id={}", id);
        
        AssetFile file = assetFileRepository.findById(Objects.requireNonNull(id, "id must not be null")).orElse(null);
        if (file == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(404, "文件不存在"));
        }
        
        file.setStatus("archived");
        assetFileRepository.save(file);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ==================== 版本管理 ====================

    @GetMapping("/asset-files/{id}/versions")
    @PreAuthorize("@accessGuard.canReadAssetFile(#id, authentication)")
    @Operation(summary = "获取文件版本列表", description = "获取文件的所有版本")
    public ResponseEntity<ApiResponse<List<AssetFileVersion>>> listFileVersions(@PathVariable UUID id) {
        log.info("获取文件版本列表: fileId={}", id);
        
        List<AssetFileVersion> versions = assetFileVersionRepository.findByFileIdOrderByCreatedAtDesc(id);
        return ResponseEntity.ok(ApiResponse.success(versions));
    }

    @PostMapping("/asset-files/{id}/versions")
    @PreAuthorize("@accessGuard.canWriteAssetFile(#id, authentication)")
    @Operation(summary = "上传新版本", description = "上传文件的新版本")
    public ResponseEntity<ApiResponse<AssetFile>> uploadNewVersion(
            @PathVariable UUID id,
            @Parameter(description = "文件") @RequestParam("file") MultipartFile file,
            @Parameter(description = "变更说明") @RequestParam(required = false) String changelog) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "上传文件不能为空");
        }
        
        log.info("上传文件新版本: fileId={}", id);
        
        AssetFile existingFile = assetFileRepository.findById(Objects.requireNonNull(id, "id must not be null"))
                .orElse(null);
        
        if (existingFile == null) {
            return ResponseEntity.ok(ApiResponse.error(404, "文件不存在"));
        }
        
        // 保存旧版本记录
        AssetFileVersion version = AssetFileVersion.builder()
                .fileId(id)
                .version(existingFile.getVersion())
                .filePath(existingFile.getFilePath())
                .fileSize(existingFile.getFileSize())
                .checksum(existingFile.getChecksum())
                .changelog(changelog)
                .uploadedBy(getCurrentUserId())
                .build();
        assetFileVersionRepository.save(Objects.requireNonNull(version, "version must not be null"));
        
        // 更新文件
        Path uploadPath = Paths.get(UPLOAD_DIR + existingFile.getAssetId());
        String originalFilename = resolveUploadFileName(file);
        String storedFilename = UUID.randomUUID() + "_" + originalFilename;
        Path filePath = uploadPath.resolve(storedFilename);
        Files.copy(file.getInputStream(), filePath);
        
        existingFile.setName(originalFilename);
        existingFile.setFilePath(filePath.toString());
        existingFile.setFileSize(file.getSize());
        existingFile.setChecksum(calculateSha256(filePath));
        existingFile.setVersion(existingFile.getVersion() + 1);
        
        AssetFile updatedFile = assetFileRepository.save(existingFile);
        
        return ResponseEntity.ok(ApiResponse.success(updatedFile));
    }

    @GetMapping("/asset-files/{fileId}/versions/{version}")
    @PreAuthorize("@accessGuard.canReadAssetFile(#fileId, authentication)")
    @Operation(summary = "下载指定版本", description = "下载文件的指定版本")
    public ResponseEntity<Resource> downloadVersion(
            @PathVariable UUID fileId,
            @PathVariable Integer version) {
        
        log.info("下载文件版本: fileId={}, version={}", fileId, version);
        
        AssetFileVersion fileVersion = assetFileVersionRepository
                .findByFileIdAndVersion(fileId, version)
                .orElse(null);
        
        if (fileVersion == null) {
            return ResponseEntity.notFound().build();
        }
        
        try {
            Path filePath = Paths.get(fileVersion.getFilePath());
            Resource resource = new UrlResource(Objects.requireNonNull(filePath.toUri(), "uri must not be null"));
            
            if (resource.exists()) {
                return ResponseEntity.ok()
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_OCTET_STREAM, "mediaType must not be null"))
                        .header(HttpHeaders.CONTENT_DISPOSITION, 
                                "attachment; filename=\"v" + version + "\"")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException e) {
            log.error("文件路径错误: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
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

    private List<AssetFile> findFilesByAssetAndVersion(UUID assetId, UUID assetVersionId) {
        if (assetVersionId != null) {
            List<AssetFile> versionedFiles = assetFileRepository.findByAssetIdAndAssetVersionIdAndStatusOrderByCreatedAtDesc(assetId, assetVersionId, "active");
            if (!versionedFiles.isEmpty()) {
                return versionedFiles;
            }
        }
        return assetFileRepository.findByAssetIdAndAssetVersionIdAndStatusOrderByCreatedAtDesc(assetId, null, "active");
    }
}
