package com.vulnaudit.service.impl;

import com.vulnaudit.dto.DocumentAttachmentItem;
import com.vulnaudit.entity.DocumentAttachment;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.DocumentAttachmentRepository;
import com.vulnaudit.repository.UserRepository;
import com.vulnaudit.service.DocumentAttachmentService;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 知识库 / 文档附件实现（t162）
 *
 * <p>存储：`app.attachments.dir`（默认 `./uploads/attachments`）下的 `{ownerType}/{ownerId}/{uuid}_{原文件名}`。
 * 磁盘名与展示名分离；下载只走带归属校验的接口，**不存在**按路径直读的端点。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentAttachmentServiceImpl implements DocumentAttachmentService {

    private final DocumentAttachmentRepository attachmentRepository;
    private final UserRepository userRepository;

    @Value("${app.attachments.dir:./uploads/attachments}")
    private String attachmentsDir = "./uploads/attachments";

    @Value("${app.attachments.max-bytes:52428800}")
    private long maxAttachmentBytes = 50L * 1024 * 1024;

    @Override
    public long maxAttachmentBytes() {
        return maxAttachmentBytes;
    }

    @Override
    @Transactional
    public DocumentAttachmentItem upload(String ownerType, UUID ownerId, MultipartFile file, UUID operatorId) {
        String type = normalizeOwnerType(ownerType);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "附件不能为空");
        }
        if (file.getSize() > maxAttachmentBytes) {
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE,
                    "附件超过上限 " + maxAttachmentBytes + " 字节（当前 " + file.getSize() + "）");
        }
        String fileName = sanitizeFileName(file.getOriginalFilename());
        String storedName = UUID.randomUUID() + "_" + fileName;
        String relativePath = type.toLowerCase(Locale.ROOT) + "/" + ownerId + "/" + storedName;
        Path target = resolveStoragePath(relativePath);
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "写入附件目录失败: " + ex.getMessage());
        }

        DocumentAttachment saved = attachmentRepository.save(Objects.requireNonNull(DocumentAttachment.builder()
                .ownerType(type)
                .ownerId(ownerId)
                .fileName(fileName)
                .storedName(storedName)
                .contentType(file.getContentType())
                .fileSize(file.getSize())
                .storagePath(relativePath)
                .uploadedBy(operatorId)
                .uploadedAt(LocalDateTime.now())
                .build()));
        log.info("附件上传: ownerType={}, ownerId={}, file={}, size={}, attachmentId={}",
                type, ownerId, fileName, file.getSize(), saved.getId());
        return new DocumentAttachmentItem(saved.getId(), saved.getOwnerType(), saved.getOwnerId(),
                saved.getFileName(), saved.getFileSize(), saved.getContentType(), saved.getUploadedAt(),
                saved.getUploadedBy(), usernameOf(operatorId));
    }

    @Override
    public List<DocumentAttachmentItem> list(String ownerType, UUID ownerId) {
        String type = normalizeOwnerType(ownerType);
        List<DocumentAttachment> rows = attachmentRepository
                .findByOwnerTypeAndOwnerIdAndDeletedAtIsNullOrderByUploadedAtDesc(type, ownerId);
        if (rows.isEmpty()) {
            return List.of();
        }
        // uploaderName：一次批量查询（与条数无关）
        List<UUID> uploaderIds = rows.stream().map(DocumentAttachment::getUploadedBy)
                .filter(Objects::nonNull).distinct().toList();
        Map<UUID, String> names = new HashMap<>();
        if (!uploaderIds.isEmpty()) {
            userRepository.findAllById(uploaderIds).forEach(user -> {
                if (user.getId() != null) {
                    names.put(user.getId(), user.getUsername());
                }
            });
        }
        return rows.stream()
                .map(row -> new DocumentAttachmentItem(row.getId(), row.getOwnerType(), row.getOwnerId(),
                        row.getFileName(), row.getFileSize(), row.getContentType(), row.getUploadedAt(),
                        row.getUploadedBy(),
                        row.getUploadedBy() == null ? null : names.get(row.getUploadedBy())))
                .toList();
    }

    @Override
    public AttachmentDownload download(String ownerType, UUID ownerId, UUID attachmentId) {
        String type = normalizeOwnerType(ownerType);
        // 归属校验：附件必须属于该实体且未软删，否则 404（不泄露存在性）
        DocumentAttachment row = attachmentRepository
                .findByIdAndOwnerTypeAndOwnerIdAndDeletedAtIsNull(attachmentId, type, ownerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "附件不存在"));
        Path path = resolveStoragePath(row.getStoragePath());
        if (!Files.exists(path)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "附件文件已不存在");
        }
        return new AttachmentDownload(path, row.getFileName(),
                row.getContentType() == null ? "application/octet-stream" : row.getContentType(),
                row.getFileSize() == null ? 0L : row.getFileSize());
    }

    @Override
    @Transactional
    public void delete(String ownerType, UUID ownerId, UUID attachmentId, UUID operatorId) {
        String type = normalizeOwnerType(ownerType);
        DocumentAttachment row = attachmentRepository
                .findByIdAndOwnerTypeAndOwnerIdAndDeletedAtIsNull(attachmentId, type, ownerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "附件不存在"));
        Path path = resolveStoragePath(row.getStoragePath());
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("删除附件文件失败（DB 仍标删）: path={}, err={}", path, ex.getMessage());
        }
        row.setDeletedAt(LocalDateTime.now());
        attachmentRepository.save(row);
        log.info("附件删除: attachmentId={}, ownerType={}, ownerId={}, by={}", attachmentId, type, ownerId, operatorId);
    }

    // ------------------------------------------------------------------ 内部

    private String normalizeOwnerType(String ownerType) {
        String type = ownerType == null ? "" : ownerType.trim().toUpperCase(Locale.ROOT);
        if (!DocumentAttachment.OWNER_KNOWLEDGE.equals(type) && !DocumentAttachment.OWNER_DOCUMENT.equals(type)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "ownerType 不合法: " + ownerType);
        }
        return type;
    }

    /** 存储路径解析：拼接后 normalize，并断言仍在附件根目录内（防穿越） */
    private Path resolveStoragePath(String relativePath) {
        Path root = Paths.get(attachmentsDir).toAbsolutePath().normalize();
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "非法存储路径");
        }
        return resolved;
    }

    /** 文件名净化：去目录部分、`..`、控制字符，裁剪长度（与 t160 同口径） */
    static String sanitizeFileName(@Nullable String raw) {
        if (raw == null) {
            return "file";
        }
        String name = raw.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replace("..", "").replaceAll("[\\p{Cntrl}]", "").trim();
        if (name.isEmpty() || ".".equals(name)) {
            name = "file";
        }
        return name.length() > 200 ? name.substring(name.length() - 200) : name;
    }

    private String usernameOf(@Nullable UUID userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId).map(com.vulnaudit.entity.User::getUsername).orElse(null);
    }
}
