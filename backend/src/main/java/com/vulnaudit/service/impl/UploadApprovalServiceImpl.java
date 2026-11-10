package com.vulnaudit.service.impl;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.dto.UploadApprovalItem;
import com.vulnaudit.dto.UploadPendingResponse;
import com.vulnaudit.entity.Tool;
import com.vulnaudit.entity.UploadApprovalRequest;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.ToolRepository;
import com.vulnaudit.repository.UploadApprovalRequestRepository;
import com.vulnaudit.repository.UserRepository;
import com.vulnaudit.service.AuditLogService;
import com.vulnaudit.service.UploadApprovalService;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
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
 * 大文件上传审批实现（t160）
 *
 * <p>目录：
 * <ul>
 *   <li>临时区 `app.upload.pending-dir`（默认 `./uploads/pending`）—— 只放 PENDING 文件；</li>
 *   <li>正式区 `app.upload.approved-dir`（默认 `./uploads/approved`）—— 批准后才移入。</li>
 * </ul>
 *
 * <p>安全：文件名净化（去路径分隔符/`..`/控制字符）+ 存储名用 UUID 前缀（原名只作为展示）；
 * 临时文件**只能**通过 approve 流转，没有任何按路径下载的端点，避免路径穿越/任意读。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadApprovalServiceImpl implements UploadApprovalService {

    private static final String AUDIT_SUBMIT = "SUBMIT_UPLOAD_APPROVAL";
    private static final String AUDIT_APPROVE = "APPROVE_UPLOAD";
    private static final String AUDIT_REJECT = "REJECT_UPLOAD";

    private final UploadApprovalRequestRepository repository;
    private final ToolRepository toolRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    @Value("${app.upload.approval-threshold-bytes:20971520}")
    private long approvalThresholdBytes = 20L * 1024 * 1024;

    @Value("${app.upload.max-bytes:524288000}")
    private long maxBytes = 500L * 1024 * 1024;

    @Value("${app.upload.pending-dir:./uploads/pending}")
    private String pendingDir = "./uploads/pending";

    @Value("${app.upload.approved-dir:./uploads/approved}")
    private String approvedDir = "./uploads/approved";

    @Value("${app.upload.purge-days:7}")
    private int purgeDays = 7;

    @Override
    public long approvalThresholdBytes() {
        return approvalThresholdBytes;
    }

    // ------------------------------------------------------------------ 提交

    @Override
    @Transactional
    public UploadPendingResponse submit(MultipartFile file, String targetType, @Nullable String name,
                                        @Nullable String description, @Nullable UUID operatorId) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "上传文件不能为空");
        }
        long size = file.getSize();
        if (size > maxBytes) {
            // 413：超过单文件上限（不受审批保护，直接拒）
            throw new BusinessException(ErrorCode.PAYLOAD_TOO_LARGE,
                    "文件超过上限 " + maxBytes + " 字节（当前 " + size + "）");
        }
        String type = normalizeTargetType(targetType);
        String safeName = sanitizeFileName(file.getOriginalFilename());

        if (size <= approvalThresholdBytes) {
            // 未超门限：保持既有直传体验，直接落业务表
            UUID targetId = createBusinessRecord(type, safeName, size, file, name, description, operatorId);
            log.info("上传直传（未超门限）: type={}, name={}, size={}, targetId={}", type, safeName, size, targetId);
            return new UploadPendingResponse(null, "DIRECT", type, targetId, safeName, size, null);
        }

        // 超门限：只写临时区 + PENDING，业务表保持干净
        String key = "pending/" + UUID.randomUUID() + "_" + safeName;
        Path target = Paths.get(pendingDir).toAbsolutePath().normalize()
                .resolve(Objects.requireNonNull(Paths.get(key).getFileName())).normalize();
        try {
            Files.createDirectories(target.getParent());
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "写入临时区失败: " + ex.getMessage());
        }

        UploadApprovalRequest request = repository.save(Objects.requireNonNull(UploadApprovalRequest.builder()
                .targetType(type)
                .fileName(safeName)
                .fileSize(size)
                .contentType(file.getContentType())
                .storageKey(key)
                .status(UploadApprovalRequest.STATUS_PENDING)
                .requestedBy(operatorId)
                .requestedAt(LocalDateTime.now())
                .purgeAfter(LocalDateTime.now().plusDays(purgeDays))
                .build()));
        auditLogService.log(operatorId, AUDIT_SUBMIT, "upload_approval", request.getId(),
                "type=" + type + ", file=" + safeName + ", size=" + size);
        log.info("大文件进入审批: requestId={}, file={}, size={}", request.getId(), safeName, size);
        return new UploadPendingResponse(request.getId(), UploadApprovalRequest.STATUS_PENDING, type, null,
                safeName, size, request.getPurgeAfter());
    }

    // ------------------------------------------------------------------ 查询

    @Override
    public UploadApprovalItem findOwn(UUID id, UUID operatorId) {
        UploadApprovalRequest request = repository.findByIdAndRequestedBy(id, operatorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "上传请求不存在"));
        return toItem(request);
    }

    @Override
    public PageResponse<UploadApprovalItem> list(@Nullable String status, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : Math.min(size, 100);
        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "requestedAt"));
        Page<UploadApprovalRequest> result = StringUtils.hasText(status)
                ? repository.findByStatus(status.trim().toUpperCase(Locale.ROOT), pageable)
                : repository.findAll(pageable);
        return PageResponse.<UploadApprovalItem>builder()
                .content(result.getContent().stream().map(this::toItem).toList())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .currentPage(safePage)
                .pageSize(safeSize)
                .build();
    }

    // ------------------------------------------------------------------ 审批

    @Override
    @Transactional
    public UploadApprovalItem approve(UUID id, @Nullable UUID reviewerId) {
        UploadApprovalRequest request = requirePending(id);
        Path pendingFile = resolvePendingFile(request.getStorageKey());
        if (!Files.exists(pendingFile)) {
            throw new BusinessException(ErrorCode.CONFLICT, "临时文件已不存在（可能已被清理），请让申请人重新上传");
        }
        // 移入正式区
        String approvedKey = "approved/" + UUID.randomUUID() + "_" + request.getFileName();
        Path approvedFile = Paths.get(approvedDir).toAbsolutePath().normalize()
                .resolve(Objects.requireNonNull(Paths.get(approvedKey).getFileName())).normalize();
        try {
            Files.createDirectories(approvedFile.getParent());
            Files.move(pendingFile, approvedFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "移入正式存储失败: " + ex.getMessage());
        }

        UUID targetId = createApprovedRecord(request, approvedFile, approvedKey);
        request.setStatus(UploadApprovalRequest.STATUS_APPROVED);
        request.setTargetId(targetId);
        request.setReviewedBy(reviewerId);
        request.setReviewedAt(LocalDateTime.now());
        request.setStorageKey(approvedKey);
        repository.save(request);

        auditLogService.log(reviewerId, AUDIT_APPROVE, "upload_approval", request.getId(),
                "file=" + request.getFileName() + ", targetId=" + targetId);
        log.info("上传审批通过: requestId={}, targetId={}", request.getId(), targetId);
        return toItem(request);
    }

    @Override
    @Transactional
    public UploadApprovalItem reject(UUID id, @Nullable String note, @Nullable UUID reviewerId) {
        UploadApprovalRequest request = requirePending(id);
        // 拒绝即删除临时文件（不留残余）
        deleteQuietly(resolvePendingFile(request.getStorageKey()));
        request.setStatus(UploadApprovalRequest.STATUS_REJECTED);
        request.setReviewedBy(reviewerId);
        request.setReviewedAt(LocalDateTime.now());
        request.setReviewNote(note);
        repository.save(request);
        auditLogService.log(reviewerId, AUDIT_REJECT, "upload_approval", request.getId(),
                "file=" + request.getFileName() + ", note=" + (note == null ? "" : note));
        log.info("上传审批拒绝: requestId={}, file 已删除", request.getId());
        return toItem(request);
    }

    // ------------------------------------------------------------------ 过期清理

    @Override
    @Transactional
    public int purgeExpired() {
        List<UploadApprovalRequest> expired = repository.findExpiredPending(LocalDateTime.now());
        for (UploadApprovalRequest request : expired) {
            deleteQuietly(resolvePendingFile(request.getStorageKey()));
            request.setStatus(UploadApprovalRequest.STATUS_EXPIRED);
            request.setReviewNote("超过 " + purgeDays + " 天未审批，自动清理");
            repository.save(request);
        }
        if (!expired.isEmpty()) {
            log.info("上传审批过期清理: count={}", expired.size());
        }
        return expired.size();
    }

    // ------------------------------------------------------------------ 内部

    private UploadApprovalRequest requirePending(UUID id) {
        UploadApprovalRequest request = repository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "上传请求不存在"));
        if (!UploadApprovalRequest.STATUS_PENDING.equals(request.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "该请求已是 " + request.getStatus() + " 状态，不能重复处理");
        }
        return request;
    }

    /** 临时区路径解析：只取 storage_key 的文件名部分，杜绝 `../` 穿越 */
    private Path resolvePendingFile(@Nullable String storageKey) {
        String safe = sanitizeFileName(storageKey == null ? "missing" : Paths.get(storageKey).getFileName().toString());
        return Paths.get(pendingDir).toAbsolutePath().normalize().resolve(safe).normalize();
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            log.warn("删除临时文件失败（不阻断）: path={}, err={}", path, ex.getMessage());
        }
    }

    /** 文件名净化：去掉目录部分、`..`、控制字符，长度裁剪；防空 */
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

    private String normalizeTargetType(@Nullable String targetType) {
        String type = StringUtils.hasText(targetType) ? targetType.trim().toUpperCase(Locale.ROOT) : "TOOL";
        if (!List.of("TOOL", "COMPONENT", "KNOWLEDGE", "DOCUMENT").contains(type)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "targetType 不合法: " + targetType + "（可选：TOOL/COMPONENT/KNOWLEDGE/DOCUMENT）");
        }
        return type;
    }

    /** 未超门限：直接落业务表（当前只实现 TOOL，其余类型返回 null 表示「无需业务记录」） */
    private UUID createBusinessRecord(String type, String fileName, long size, MultipartFile file,
                                      @Nullable String name, @Nullable String description,
                                      @Nullable UUID operatorId) {
        if (!UploadApprovalRequest.TARGET_TOOL.equals(type)) {
            return null;
        }
        String beanName = stripExtension(fileName);
        Tool tool = toolRepository.save(Objects.requireNonNull(Tool.builder()
                .name(StringUtils.hasText(name) ? name.trim() : beanName)
                .category("upload")
                .description(description)
                .fileName(fileName)
                .filePath("approved/" + UUID.randomUUID() + "_" + fileName)
                .fileSize(size)
                .createdBy(operatorId)
                .build()));
        return tool.getId();
    }

    /** 批准后创建业务记录（当前：TOOL） */
    private UUID createApprovedRecord(UploadApprovalRequest request, Path approvedFile, String approvedKey) {
        if (!UploadApprovalRequest.TARGET_TOOL.equals(request.getTargetType())) {
            return null;
        }
        Tool tool = toolRepository.save(Objects.requireNonNull(Tool.builder()
                .name(stripExtension(request.getFileName()))
                .category("upload")
                .description("经管理员批准的大文件上传")
                .fileName(request.getFileName())
                .filePath(approvedKey)
                .fileSize(request.getFileSize())
                .createdBy(request.getRequestedBy())
                .build()));
        return tool.getId();
    }

    private String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private UploadApprovalItem toItem(UploadApprovalRequest request) {
        Map<UUID, String> usernames = new HashMap<>();
        List<UUID> ids = java.util.stream.Stream.of(request.getRequestedBy(), request.getReviewedBy())
                .filter(Objects::nonNull).distinct().toList();
        if (!ids.isEmpty()) {
            userRepository.findAllById(ids).forEach(user -> {
                if (user.getId() != null) {
                    usernames.put(user.getId(), user.getUsername());
                }
            });
        }
        boolean pendingFilePresent = UploadApprovalRequest.STATUS_PENDING.equals(request.getStatus())
                && Files.exists(resolvePendingFile(request.getStorageKey()));
        return new UploadApprovalItem(
                request.getId(),
                request.getTargetType(),
                request.getTargetId(),
                request.getFileName(),
                request.getFileSize(),
                request.getContentType(),
                request.getStatus(),
                new UploadApprovalItem.UploadUserBrief(request.getRequestedBy(),
                        usernames.get(request.getRequestedBy())),
                request.getRequestedAt(),
                new UploadApprovalItem.UploadUserBrief(request.getReviewedBy(),
                        usernames.get(request.getReviewedBy())),
                request.getReviewedAt(),
                request.getReviewNote(),
                request.getPurgeAfter(),
                pendingFilePresent);
    }
}
