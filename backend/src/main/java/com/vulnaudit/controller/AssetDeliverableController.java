package com.vulnaudit.controller;

import com.vulnaudit.dto.ApiResponse;
import com.vulnaudit.dto.PageResponse;
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
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;
import org.springframework.security.access.prepost.PreAuthorize;
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
    /** t148：排序白名单 + 上传人批量解析 */
    private final com.vulnaudit.service.AttachmentListSupport attachmentListSupport;

    private static final String UPLOAD_DIR = "./uploads/deliverables/";

    @GetMapping("/assets/{assetId}/deliverables")
    @PreAuthorize("@accessGuard.canReadAsset(#assetId, authentication)")
    @Operation(summary = "获取资产交付物列表（分页 + 筛选 + 排序，t148）",
            description = "默认 page=0&size=20（size ≤100）；sort 白名单 uploadedAt/createdAt、name、size/fileSize、type；"
                    + "筛选 keyword（文件名）、category（=交付物类型）、uploaderId、dateFrom/dateTo；"
                    + "返回 {content,totalElements,totalPages,currentPage,pageSize}；每项含 uploadedBy + uploaderName 与 remark")
    public ResponseEntity<ApiResponse<PageResponse<com.vulnaudit.dto.AssetDeliverableItem>>> listDeliverables(
            @PathVariable UUID assetId,
            @RequestParam(required = false) UUID assetVersionId,
            @RequestParam(defaultValue = "false") boolean allVersions,
            @Parameter(description = "页码（0-based）") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页条数（≤100）") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "排序，如 uploadedAt,desc / name,asc / size,desc / type,asc")
            @RequestParam(required = false) String sort,
            @Parameter(description = "文件名模糊") @RequestParam(required = false) String keyword,
            @Parameter(description = "交付物类型（等价 category）") @RequestParam(required = false) String category,
            @Parameter(description = "上传人ID") @RequestParam(required = false) UUID uploaderId,
            @Parameter(description = "上传时间起（ISO 日期，含当日）")
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso =
                    org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate dateFrom,
            @Parameter(description = "上传时间止（ISO 日期，含当日）")
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso =
                    org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate dateTo) {

        int safePage = attachmentListSupport.resolvePage(page);
        int safeSize = attachmentListSupport.resolveSize(size);
        org.springframework.data.domain.Sort sortSpec = attachmentListSupport.resolveSort(sort,
                com.vulnaudit.service.AttachmentListSupport.deliverableSortWhitelist(), "uploadedAt");

        org.springframework.data.jpa.domain.Specification<AssetDeliverable> spec = (root, query, cb) -> {
            java.util.List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            predicates.add(cb.equal(root.get("assetId"), assetId));
            predicates.add(cb.equal(root.get("status"), "active"));
            if (StringUtils.hasText(keyword)) {
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + keyword.trim().toLowerCase() + "%"));
            }
            if (StringUtils.hasText(category)) {
                predicates.add(cb.equal(root.get("deliverableType"), category.trim()));
            }
            if (uploaderId != null) {
                predicates.add(cb.equal(root.get("uploadedBy"), uploaderId));
            }
            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), dateFrom.atStartOfDay()));
            }
            if (dateTo != null) {
                predicates.add(cb.lessThan(root.get("createdAt"), dateTo.plusDays(1).atStartOfDay()));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        org.springframework.data.domain.Page<AssetDeliverable> result;
        if (allVersions) {
            result = assetDeliverableRepository.findAll(spec,
                    org.springframework.data.domain.PageRequest.of(safePage, safeSize, sortSpec));
        } else {
            UUID resolvedVersionId = resolveAssetVersionId(assetId, assetVersionId);
            result = assetDeliverableRepository.findAll(
                    spec.and((root, query, cb) -> cb.equal(root.get("assetVersionId"), resolvedVersionId)),
                    org.springframework.data.domain.PageRequest.of(safePage, safeSize, sortSpec));
            if (result.getTotalElements() == 0) {
                // 旧语义回退：该版本没有交付物时，返回「无版本」的交付物
                result = assetDeliverableRepository.findAll(
                        spec.and((root, query, cb) -> cb.isNull(root.get("assetVersionId"))),
                        org.springframework.data.domain.PageRequest.of(safePage, safeSize, sortSpec));
            }
        }

        java.util.Map<UUID, String> uploaderNames = attachmentListSupport.uploaderNames(
                result.getContent().stream().map(AssetDeliverable::getUploadedBy).toList());
        java.util.List<com.vulnaudit.dto.AssetDeliverableItem> items = result.getContent().stream()
                .map(item -> new com.vulnaudit.dto.AssetDeliverableItem(
                        item.getId(), item.getAssetId(), item.getAssetVersionId(), item.getName(),
                        item.getDeliverableType(), item.getDescription(), item.getRemark(), item.getFileSize(),
                        item.getFilePath(), item.getChecksum(), item.getStatus(), item.getCreatedAt(),
                        item.getUploadedBy(),
                        item.getUploadedBy() == null ? null : uploaderNames.get(item.getUploadedBy())))
                .toList();

        return ResponseEntity.ok(ApiResponse.success(PageResponse.<com.vulnaudit.dto.AssetDeliverableItem>builder()
                .content(items)
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .currentPage(safePage)
                .pageSize(safeSize)
                .build()));
    }

    @PostMapping("/assets/{assetId}/deliverables")
    @PreAuthorize("@accessGuard.canWriteAsset(#assetId, authentication)")
    @Operation(summary = "上传资产交付物", description = "上传漏洞利用代码、POC、EXP、说明文档等交付物；remark 为独立备注（≤500）")
    public ResponseEntity<ApiResponse<AssetDeliverable>> uploadDeliverable(
            @PathVariable UUID assetId,
            @RequestParam(required = false) UUID assetVersionId,
            @Parameter(description = "文件") @RequestParam("file") MultipartFile file,
            @Parameter(description = "交付物类型") @RequestParam String deliverableType,
            @Parameter(description = "说明") @RequestParam(required = false) String description,
            @Parameter(description = "备注（≤500，与 description 分离）") @RequestParam(required = false) String remark)
            throws IOException {
        com.vulnaudit.service.AttachmentListSupport.validateRemark(remark);
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
                .remark(remark)
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
    @PreAuthorize("@accessGuard.canReadDeliverable(#id, authentication)")
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
    @PreAuthorize("@accessGuard.canWriteDeliverable(#id, authentication)")
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
