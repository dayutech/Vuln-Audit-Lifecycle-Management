package com.vulnaudit.controller;

import com.vulnaudit.dto.*;
import com.vulnaudit.entity.Document;
import com.vulnaudit.entity.DocumentTag;
import com.vulnaudit.entity.DocumentVersion;
import com.vulnaudit.repository.DocumentTagRepository;
import com.vulnaudit.repository.DocumentVersionRepository;
import com.vulnaudit.service.DocumentService;
import com.vulnaudit.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 文档管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Tag(name = "文档管理", description = "文档CRUD、版本管理、搜索等接口")
@SecurityRequirement(name = "bearerAuth")
public class DocumentController {

    private final DocumentService documentService;
    private final DocumentVersionRepository documentVersionRepository;
    private final DocumentTagRepository documentTagRepository;
    private final UserService userService;

    @PostMapping
    @Operation(summary = "创建文档", description = "创建新文档")
    public ResponseEntity<ApiResponse<Document>> createDocument(@Valid @RequestBody DocumentCreateRequest request) {
        log.info("创建文档: title={}", request.getTitle());
        
        Document document = Document.builder()
                .title(request.getTitle())
                .category(request.getCategory())
                .summary(request.getSummary())
                .content(request.getContent())
                .assetId(request.getAssetId())
                .build();
        
        Document createdDocument = documentService.createDocument(document);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(createdDocument));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取文档详情", description = "根据ID获取文档详细信息")
    public ResponseEntity<ApiResponse<Document>> getDocument(@PathVariable UUID id) {
        log.info("获取文档详情: id={}", id);
        
        return documentService.findById(id)
                .map(doc -> ResponseEntity.ok(ApiResponse.success(doc)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(404, "文档不存在")));
    }

    @GetMapping
    @Operation(summary = "分页查询文档列表", description = "支持按关键词、分类、资产筛选")
    public ResponseEntity<ApiResponse<PageResponse<Document>>> listDocuments(
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "搜索关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "分类") @RequestParam(required = false) String category,
            @Parameter(description = "资产ID") @RequestParam(required = false) UUID assetId) {
        
        log.info("分页查询文档: page={}, size={}, keyword={}, category={}", page, size, keyword, category);
        
        PageResponse<Document> result = documentService.findAll(page, size, keyword, category, assetId);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/asset/{assetId}")
    @Operation(summary = "获取资产相关文档", description = "获取指定资产的所有文档")
    public ResponseEntity<ApiResponse<List<Document>>> listDocumentsByAsset(@PathVariable UUID assetId) {
        log.info("获取资产相关文档: assetId={}", assetId);
        
        List<Document> documents = documentService.findByAssetId(assetId);
        return ResponseEntity.ok(ApiResponse.success(documents));
    }

    @GetMapping("/category/{category}")
    @Operation(summary = "按分类获取文档", description = "获取指定分类的所有文档")
    public ResponseEntity<ApiResponse<List<Document>>> listDocumentsByCategory(@PathVariable String category) {
        log.info("按分类获取文档: category={}", category);
        
        List<Document> documents = documentService.findByCategory(category);
        return ResponseEntity.ok(ApiResponse.success(documents));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新文档", description = "更新文档信息")
    public ResponseEntity<ApiResponse<Document>> updateDocument(
            @PathVariable UUID id,
            @Valid @RequestBody DocumentUpdateRequest request) {
        
        log.info("更新文档: id={}", id);
        
        Document document = Document.builder()
                .title(request.getTitle())
                .category(request.getCategory())
                .summary(request.getSummary())
                .content(request.getContent())
                .build();
        
        Document updatedDocument = documentService.updateDocument(id, document);
        return ResponseEntity.ok(ApiResponse.success(updatedDocument));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除文档", description = "删除文档")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(@PathVariable UUID id) {
        log.info("删除文档: id={}", id);
        
        documentService.deleteDocument(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/search")
    @Operation(summary = "搜索文档", description = "全文搜索文档")
    public ResponseEntity<ApiResponse<List<Document>>> searchDocuments(
            @Parameter(description = "搜索关键词") @RequestParam String keyword) {
        
        log.info("搜索文档: keyword={}", keyword);
        
        List<Document> documents = documentService.search(keyword);
        return ResponseEntity.ok(ApiResponse.success(documents));
    }

    @PostMapping("/{id}/access")
    @Operation(summary = "授予文档访问权限", description = "授予用户文档访问权限")
    public ResponseEntity<ApiResponse<Void>> grantAccess(
            @PathVariable UUID id,
            @Parameter(description = "用户ID") @RequestParam UUID userId,
            @Parameter(description = "访问类型") @RequestParam String accessType) {
        
        log.info("授予文档访问权限: documentId={}, userId={}, accessType={}", id, userId, accessType);
        
        documentService.grantAccess(id, userId, accessType);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/{id}/access")
    @Operation(summary = "撤销文档访问权限", description = "撤销用户文档访问权限")
    public ResponseEntity<ApiResponse<Void>> revokeAccess(
            @PathVariable UUID id,
            @Parameter(description = "用户ID") @RequestParam UUID userId) {
        
        log.info("撤销文档访问权限: documentId={}, userId={}", id, userId);
        
        documentService.revokeAccess(id, userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ==================== 发布管理 ====================

    /**
     * 发布文档
     */
    @PostMapping("/{id}/publish")
    @Operation(summary = "发布文档", description = "将文档状态设置为已发布")
    public ResponseEntity<ApiResponse<Document>> publishDocument(@PathVariable UUID id) {
        log.info("发布文档: id={}", id);
        
        Document document = documentService.findById(id)
                .orElseThrow(() -> new RuntimeException("文档不存在"));
        
        document.setIsPublished(true);
        document.setPublishedAt(LocalDateTime.now());
        
        Document updatedDocument = documentService.updateDocument(id, document);
        log.info("文档发布成功: id={}", id);
        
        return ResponseEntity.ok(ApiResponse.success(updatedDocument));
    }

    /**
     * 取消发布文档
     */
    @PostMapping("/{id}/unpublish")
    @Operation(summary = "取消发布文档", description = "将文档状态设置为未发布")
    public ResponseEntity<ApiResponse<Document>> unpublishDocument(@PathVariable UUID id) {
        log.info("取消发布文档: id={}", id);
        
        Document document = documentService.findById(id)
                .orElseThrow(() -> new RuntimeException("文档不存在"));
        
        document.setIsPublished(false);
        
        Document updatedDocument = documentService.updateDocument(id, document);
        log.info("文档取消发布成功: id={}", id);
        
        return ResponseEntity.ok(ApiResponse.success(updatedDocument));
    }

    // ==================== 版本管理 ====================

    /**
     * 获取文档版本历史
     */
    @GetMapping("/{id}/versions")
    @Operation(summary = "获取文档版本历史", description = "获取文档的所有历史版本")
    public ResponseEntity<ApiResponse<List<DocumentVersion>>> getDocumentVersions(@PathVariable UUID id) {
        log.info("获取文档版本历史: documentId={}", id);
        
        List<DocumentVersion> versions = documentVersionRepository.findByDocumentIdOrderByVersionDesc(id);
        return ResponseEntity.ok(ApiResponse.success(versions));
    }

    /**
     * 获取指定版本详情
     */
    @GetMapping("/{id}/versions/{version}")
    @Operation(summary = "获取指定版本详情", description = "获取文档的指定版本内容")
    public ResponseEntity<ApiResponse<DocumentVersion>> getDocumentVersion(
            @PathVariable UUID id,
            @PathVariable Integer version) {
        log.info("获取文档指定版本: documentId={}, version={}", id, version);
        
        return documentVersionRepository.findByDocumentIdAndVersion(id, version)
                .map(v -> ResponseEntity.ok(ApiResponse.success(v)))
                .orElse(ResponseEntity.ok(ApiResponse.error(404, "版本不存在")));
    }

    /**
     * 回滚到指定版本
     */
    @PostMapping("/{id}/versions/{version}/rollback")
    @Operation(summary = "回滚到指定版本", description = "将文档内容回滚到指定版本")
    public ResponseEntity<ApiResponse<Document>> rollbackDocumentVersion(
            @PathVariable UUID id,
            @PathVariable Integer version) {
        log.info("回滚文档版本: documentId={}, version={}", id, version);
        
        DocumentVersion docVersion = documentVersionRepository.findByDocumentIdAndVersion(id, version)
                .orElseThrow(() -> new RuntimeException("版本不存在"));
        
        Document document = documentService.findById(id)
                .orElseThrow(() -> new RuntimeException("文档不存在"));
        
        document.setTitle(docVersion.getTitle());
        document.setContent(docVersion.getContent());
        document.setSummary(docVersion.getSummary());
        
        Document updatedDocument = documentService.updateDocument(id, document);
        log.info("文档版本回滚成功: documentId={}, version={}", id, version);
        
        return ResponseEntity.ok(ApiResponse.success(updatedDocument));
    }

    // ==================== 标签管理 ====================

    /**
     * 获取文档的标签列表
     */
    @GetMapping("/{id}/tags")
    @Operation(summary = "获取文档的标签列表", description = "获取指定文档的所有标签")
    public ResponseEntity<ApiResponse<List<DocumentTag>>> getDocumentTags(@PathVariable UUID id) {
        log.info("获取文档标签列表: documentId={}", id);
        
        List<DocumentTag> tags = documentTagRepository.findByDocumentId(id);
        return ResponseEntity.ok(ApiResponse.success(tags));
    }

    /**
     * 添加标签到文档
     */
    @PostMapping("/{id}/tags")
    @Operation(summary = "添加标签到文档", description = "为文档添加标签")
    public ResponseEntity<ApiResponse<DocumentTag>> addDocumentTag(
            @PathVariable UUID id,
            @Parameter(description = "标签名称") @RequestParam String tag) {
        log.info("添加文档标签: documentId={}, tag={}", id, tag);
        
        UUID currentUserId = getCurrentUserId();
        
        DocumentTag documentTag = DocumentTag.builder()
                .documentId(id)
                .tag(tag)
                .createdBy(currentUserId)
                .createdAt(LocalDateTime.now())
                .build();
        
        DocumentTag savedTag = documentTagRepository.save(documentTag);
        log.info("文档标签添加成功: documentId={}, tag={}", id, tag);
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(savedTag));
    }

    /**
     * 移除文档的标签
     */
    @DeleteMapping("/{documentId}/tags/{tagId}")
    @Operation(summary = "移除文档的标签", description = "移除文档的指定标签")
    public ResponseEntity<ApiResponse<Void>> removeDocumentTag(
            @PathVariable UUID documentId,
            @PathVariable UUID tagId) {
        log.info("移除文档标签: documentId={}, tagId={}", documentId, tagId);
        
        documentTagRepository.deleteById(tagId);
        log.info("文档标签移除成功: documentId={}, tagId={}", documentId, tagId);
        
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ==================== "我的"文档 ====================

    /**
     * 获取我的文档列表
     */
    @GetMapping("/my/documents")
    @Operation(summary = "获取我的文档列表", description = "获取当前用户创建的所有文档")
    public ResponseEntity<ApiResponse<List<Document>>> getMyDocuments() {
        log.info("获取我的文档列表");
        
        UUID currentUserId = getCurrentUserId();
        List<Document> documents = documentService.findByCreatedBy(currentUserId);
        return ResponseEntity.ok(ApiResponse.success(documents));
    }

    /**
     * 获取当前用户ID的辅助方法
     */
    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        String username = authentication.getName();
        return userService.findByUsername(username)
                .map(user -> user.getId())
                .orElse(null);
    }

}
