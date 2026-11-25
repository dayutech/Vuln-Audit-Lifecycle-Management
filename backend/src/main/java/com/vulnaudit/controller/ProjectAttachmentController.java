package com.vulnaudit.controller;

import com.vulnaudit.dto.ApiResponse;
import com.vulnaudit.dto.DocumentAttachmentItem;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.security.CurrentUserProvider;
import com.vulnaudit.service.DocumentAttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * 项目附件（t193）：项目合同 / 补充协议等文件。
 *
 * <p>复用 t162 的独立附件模型（`document_attachments` 的 owner_type/owner_id，
 * 新归属类型 {@code PROJECT}），因此**不需要新表/新迁移**，直接继承既有的：
 * 文件名净化、路径穿越防护（storagePath 必须落在附件根目录内）、软删 + 物理删除、
 * 下载的 RFC 5987 中文名、50MB 上限（`app.attachments.max-bytes`）。
 *
 * <p>权限：
 * <ul>
 *   <li>上传 / 删除：与项目写操作同口径 —— ADMIN / PROJECT_LEADER，或该项目的项目经理；</li>
 *   <li>列表 / 下载：登录即可（项目属组织维度，读对全员开放，与 {@code GET /projects/{id}} 一致）；</li>
 *   <li>附件不属于路径中的项目 → 404（由服务层按 ownerType+ownerId 校验）。</li>
 * </ul>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "项目附件", description = "项目合同/文档附件（≤50MB，归属校验后下载）")
@SecurityRequirement(name = "bearerAuth")
public class ProjectAttachmentController {

    /** 与 {@code ProjectController#WRITE_GUARD} 同口径 */
    private static final String WRITE_GUARD =
            "hasAnyRole('ADMIN','PROJECT_LEADER') or @accessGuard.canManageProject(#id, authentication)";

    private final DocumentAttachmentService attachmentService;
    private final CurrentUserProvider currentUserProvider;
    private final com.vulnaudit.repository.ProjectRepository projectRepository;

    @PostMapping("/api/v1/projects/{id}/attachments")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "上传项目附件", description = "multipart；≤50MB（超限 413）；项目不存在 → 404")
    public ResponseEntity<ApiResponse<DocumentAttachmentItem>> uploadProjectAttachment(
            @PathVariable UUID id,
            @Parameter(description = "文件") @RequestParam("file") MultipartFile file) {
        requireProject(id);
        DocumentAttachmentItem created = attachmentService.upload(
                com.vulnaudit.entity.DocumentAttachment.OWNER_PROJECT, id, file,
                currentUserProvider.currentUserId().orElse(null));
        log.info("项目附件上传: projectId={}, attachmentId={}, name={}", id, created.id(), created.fileName());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @GetMapping("/api/v1/projects/{id}/attachments")
    @Operation(summary = "项目附件列表", description = "含 fileName/fileSize/contentType/uploadedAt/uploaderName")
    public ApiResponse<List<DocumentAttachmentItem>> listProjectAttachments(@PathVariable UUID id) {
        requireProject(id);
        return ApiResponse.success(attachmentService.list(
                com.vulnaudit.entity.DocumentAttachment.OWNER_PROJECT, id));
    }

    @GetMapping("/api/v1/projects/{id}/attachments/{attachmentId}/download")
    @Operation(summary = "下载项目附件", description = "Content-Disposition 支持中文（RFC 5987）；归属不符 → 404")
    public ResponseEntity<Resource> downloadProjectAttachment(@PathVariable UUID id,
                                                              @PathVariable UUID attachmentId) {
        requireProject(id);
        DocumentAttachmentService.AttachmentDownload payload =
                attachmentService.download(com.vulnaudit.entity.DocumentAttachment.OWNER_PROJECT, id, attachmentId);
        Resource resource = new FileSystemResource(payload.path());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        DocumentAttachmentController.contentDisposition(payload.fileName()))
                .header(HttpHeaders.CONTENT_TYPE, payload.contentType())
                .contentLength(payload.size())
                .body(resource);
    }

    @DeleteMapping("/api/v1/projects/{id}/attachments/{attachmentId}")
    @PreAuthorize(WRITE_GUARD)
    @Operation(summary = "删除项目附件", description = "软删 + 删物理文件")
    public ApiResponse<Void> deleteProjectAttachment(@PathVariable UUID id, @PathVariable UUID attachmentId) {
        requireProject(id);
        attachmentService.delete(com.vulnaudit.entity.DocumentAttachment.OWNER_PROJECT, id, attachmentId,
                currentUserProvider.currentUserId().orElse(null));
        return ApiResponse.success(null, "已删除");
    }

    private void requireProject(UUID projectId) {
        if (projectId == null || !projectRepository.existsById(projectId)) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "项目不存在: " + projectId);
        }
    }
}