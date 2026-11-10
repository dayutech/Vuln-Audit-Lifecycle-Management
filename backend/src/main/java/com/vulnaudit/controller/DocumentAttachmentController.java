package com.vulnaudit.controller;

import com.vulnaudit.dto.ApiResponse;
import com.vulnaudit.dto.DocumentAttachmentItem;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * 知识库 / 文档附件（t162）
 *
 * <p>端点对两类归属各一套（`/knowledge/{id}/attachments` 与 `/documents/{id}/attachments`），
 * 权限沿用实体自身的读写权限（知识库用 `canWriteKnowledgeTags`，文档用 `canWriteDocument`/`canReadDocument`）。
 *
 * <p>**没有按 storage_path 直读的端点**：下载必须带 `{ownerId}/{attachmentId}` 并做归属校验
 * （附件不属于该实体 → 404）。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "知识库/文档附件", description = "独立附件模型（替代正文 base64），≤50MB，归属校验后下载")
@SecurityRequirement(name = "bearerAuth")
public class DocumentAttachmentController {

    private final DocumentAttachmentService attachmentService;
    private final CurrentUserProvider currentUserProvider;

    // ------------------------------------------------------------------ 知识库

    @PostMapping("/api/v1/knowledge/{id}/attachments")
    @PreAuthorize("@accessGuard.canWriteKnowledgeTags(#id, authentication)")
    @Operation(summary = "上传知识库附件", description = "multipart；≤50MB（超限 413）")
    public ResponseEntity<ApiResponse<DocumentAttachmentItem>> uploadKnowledgeAttachment(
            @PathVariable UUID id,
            @Parameter(description = "文件") @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                attachmentService.upload(com.vulnaudit.entity.DocumentAttachment.OWNER_KNOWLEDGE, id, file,
                        currentUserProvider.currentUserId().orElse(null))));
    }

    @GetMapping("/api/v1/knowledge/{id}/attachments")
    @Operation(summary = "知识库附件列表", description = "含 fileName/fileSize/contentType/uploadedAt/uploaderName")
    public ApiResponse<List<DocumentAttachmentItem>> listKnowledgeAttachments(@PathVariable UUID id) {
        return ApiResponse.success(attachmentService.list(
                com.vulnaudit.entity.DocumentAttachment.OWNER_KNOWLEDGE, id));
    }

    @GetMapping("/api/v1/knowledge/{id}/attachments/{attachmentId}/download")
    @Operation(summary = "下载知识库附件", description = "Content-Disposition 支持中文（RFC 5987）；归属不符 → 404")
    public ResponseEntity<Resource> downloadKnowledgeAttachment(@PathVariable UUID id,
                                                                @PathVariable UUID attachmentId) {
        return buildDownload(com.vulnaudit.entity.DocumentAttachment.OWNER_KNOWLEDGE, id, attachmentId);
    }

    @DeleteMapping("/api/v1/knowledge/{id}/attachments/{attachmentId}")
    @PreAuthorize("@accessGuard.canWriteKnowledgeTags(#id, authentication)")
    @Operation(summary = "删除知识库附件", description = "软删 + 删物理文件；列表不再出现、下载 404")
    public ApiResponse<Void> deleteKnowledgeAttachment(@PathVariable UUID id, @PathVariable UUID attachmentId) {
        attachmentService.delete(com.vulnaudit.entity.DocumentAttachment.OWNER_KNOWLEDGE, id, attachmentId,
                currentUserProvider.currentUserId().orElse(null));
        return ApiResponse.success(null, "已删除");
    }

    // ------------------------------------------------------------------ 文档

    @PostMapping("/api/v1/documents/{id}/attachments")
    @PreAuthorize("@accessGuard.canWriteDocument(#id, authentication)")
    @Operation(summary = "上传文档附件", description = "multipart；≤50MB（超限 413）")
    public ResponseEntity<ApiResponse<DocumentAttachmentItem>> uploadDocumentAttachment(
            @PathVariable UUID id,
            @Parameter(description = "文件") @RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                attachmentService.upload(com.vulnaudit.entity.DocumentAttachment.OWNER_DOCUMENT, id, file,
                        currentUserProvider.currentUserId().orElse(null))));
    }

    @GetMapping("/api/v1/documents/{id}/attachments")
    @Operation(summary = "文档附件列表")
    public ApiResponse<List<DocumentAttachmentItem>> listDocumentAttachments(@PathVariable UUID id) {
        return ApiResponse.success(attachmentService.list(
                com.vulnaudit.entity.DocumentAttachment.OWNER_DOCUMENT, id));
    }

    @GetMapping("/api/v1/documents/{id}/attachments/{attachmentId}/download")
    @Operation(summary = "下载文档附件", description = "Content-Disposition 支持中文（RFC 5987）；归属不符 → 404")
    public ResponseEntity<Resource> downloadDocumentAttachment(@PathVariable UUID id,
                                                               @PathVariable UUID attachmentId) {
        return buildDownload(com.vulnaudit.entity.DocumentAttachment.OWNER_DOCUMENT, id, attachmentId);
    }

    @DeleteMapping("/api/v1/documents/{id}/attachments/{attachmentId}")
    @PreAuthorize("@accessGuard.canWriteDocument(#id, authentication)")
    @Operation(summary = "删除文档附件")
    public ApiResponse<Void> deleteDocumentAttachment(@PathVariable UUID id, @PathVariable UUID attachmentId) {
        attachmentService.delete(com.vulnaudit.entity.DocumentAttachment.OWNER_DOCUMENT, id, attachmentId,
                currentUserProvider.currentUserId().orElse(null));
        return ApiResponse.success(null, "已删除");
    }

    // ------------------------------------------------------------------ 公共

    private ResponseEntity<Resource> buildDownload(String ownerType, UUID ownerId, UUID attachmentId) {
        DocumentAttachmentService.AttachmentDownload payload =
                attachmentService.download(ownerType, ownerId, attachmentId);
        Resource resource = new FileSystemResource(payload.path());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(payload.fileName()))
                .header(HttpHeaders.CONTENT_TYPE, payload.contentType())
                .contentLength(payload.size())
                .body(resource);
    }

    /**
     * Content-Disposition：同时给 ASCII 回退与 RFC 5987 UTF-8 文件名
     *
     * <p>例：`attachment; filename="report.pdf"; filename*=UTF-8''%E6%8A%A5%E5%91%8A.pdf`
     */
    static String contentDisposition(String fileName) {
        String fallback = fileName.replaceAll("[^\\x20-\\x7E]", "_").replace("\"", "'");
        String encoded;
        try {
            encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (UnsupportedEncodingException ex) {
            encoded = fallback;
        }
        return "attachment; filename=\"" + fallback + "\"; filename*=UTF-8''" + encoded;
    }

    /** 供测试直接校验编码逻辑 */
    static String encodeFileName(String fileName) {
        try {
            return URLEncoder.encode(fileName, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (UnsupportedEncodingException ex) {
            return fileName;
        }
    }

    /** 便于前端/文档确认媒体类型默认值 */
    static final MediaType DEFAULT_MEDIA_TYPE = MediaType.APPLICATION_OCTET_STREAM;
}
