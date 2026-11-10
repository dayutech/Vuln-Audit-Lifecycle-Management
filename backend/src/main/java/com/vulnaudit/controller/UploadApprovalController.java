package com.vulnaudit.controller;

import com.vulnaudit.dto.ApiResponse;
import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.dto.RejectUploadRequest;
import com.vulnaudit.dto.UploadApprovalItem;
import com.vulnaudit.dto.UploadPendingResponse;
import com.vulnaudit.security.CurrentUserProvider;
import com.vulnaudit.service.UploadApprovalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * 大文件上传审批（t160）
 *
 * <p>申请人：`POST /api/v1/uploads/pending`（超门限 → PENDING）、`GET /api/v1/uploads/pending/{id}`（只看自己的）。
 * 管理员：`GET /api/v1/admin/upload-approvals`、`POST .../{id}/approve`、`POST .../{id}/reject`。
 *
 * <p>为什么不是「上传即入库」：要求「未获批准前不持久化」——超门限文件只写临时区，
 * 业务表没有任何记录，因此不可能出现在列表/下载/统计里（拒绝则立即删临时文件）。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "大文件上传审批", description = "超门限上传需管理员批准；未批准不持久化")
@SecurityRequirement(name = "bearerAuth")
public class UploadApprovalController {

    private final UploadApprovalService uploadApprovalService;
    private final CurrentUserProvider currentUserProvider;

    // ------------------------------------------------------------------ 申请人

    @PostMapping("/api/v1/uploads/pending")
    @Operation(summary = "提交上传（超门限走审批）",
            description = "≤ app.upload.approval-threshold-bytes 直接落业务表（status=DIRECT）；"
                    + "超过则只写临时区并返回 {requestId, status:PENDING}；超过 app.upload.max-bytes → 413")
    public ResponseEntity<ApiResponse<UploadPendingResponse>> submit(
            @Parameter(description = "文件") @RequestParam("file") MultipartFile file,
            @Parameter(description = "目标类型：TOOL/COMPONENT/KNOWLEDGE/DOCUMENT")
            @RequestParam(defaultValue = "TOOL") String targetType,
            @Parameter(description = "显示名（t165；≤200，超长 400；为空则批准时回落文件名去扩展名）")
            @RequestParam(required = false) String displayName,
            @Parameter(description = "描述（t165；≤1000，超长 400）") @RequestParam(required = false) String description,
            @Parameter(hidden = true) @RequestParam(required = false) String name) {

        // t165：displayName 优先；兼容老的 name 表单字段（同名语义）
        String resolvedDisplayName = (displayName == null || displayName.isBlank()) ? name : displayName;
        UploadPendingResponse result = uploadApprovalService.submit(
                file, targetType, resolvedDisplayName, description,
                currentUserProvider.currentUserId().orElse(null));
        log.info("提交上传: status={}, requestId={}, targetId={}", result.status(), result.requestId(),
                result.targetId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(result));
    }

    @GetMapping("/api/v1/uploads/pending/{id}")
    @Operation(summary = "查询自己的上传请求", description = "只能查到自己提交的请求（他人请求 → 404，不泄露存在性）")
    public ApiResponse<UploadApprovalItem> findOwn(@PathVariable UUID id) {
        return ApiResponse.success(uploadApprovalService.findOwn(
                id, currentUserProvider.currentUserId().orElse(null)));
    }

    /**
     * t166：我的上传（只含自己提交的），替换前端 localStorage 的 requestId 登记
     */
    @GetMapping("/api/v1/uploads/my")
    @Operation(summary = "我的上传列表",
            description = "只返回 requested_by = 当前用户的请求；status=PENDING|APPROVED|REJECTED|EXPIRED|CANCELLED|all"
                    + "（默认 all，非法 400）；按 requestedAt 倒序；keyword 匹配 fileName/displayName")
    public ApiResponse<PageResponse<UploadApprovalItem>> findMine(
            @Parameter(description = "状态（默认 all）") @RequestParam(required = false) String status,
            @Parameter(description = "关键字：fileName/displayName") @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(uploadApprovalService.findMine(
                currentUserProvider.currentUserId().orElse(null), status, keyword, page, size));
    }

    /**
     * t166：撤回自己的 PENDING（删临时文件 + 标 CANCELLED）
     */
    @DeleteMapping("/api/v1/uploads/my/{id}")
    @Operation(summary = "撤回上传请求", description = "仅本人 PENDING：删临时文件并标记 CANCELLED；他人 → 403，非 PENDING → 409")
    public ApiResponse<UploadApprovalItem> cancelMine(@PathVariable UUID id) {
        return ApiResponse.success(uploadApprovalService.cancelMine(
                id, currentUserProvider.currentUserId().orElse(null)));
    }

    // ------------------------------------------------------------------ 管理员

    @GetMapping("/api/v1/admin/upload-approvals")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "上传审批列表（分页）", description = "status 可空（全部）/PENDING/APPROVED/REJECTED/EXPIRED")
    public ApiResponse<PageResponse<UploadApprovalItem>> list(
            @Parameter(description = "状态：PENDING/APPROVED/REJECTED/EXPIRED/CANCELLED/all")
            @RequestParam(required = false) String status,
            @Parameter(description = "关键字：匹配 fileName 或 displayName") @RequestParam(required = false) String keyword,
            @Parameter(description = "申请人ID（按人查）") @RequestParam(required = false) UUID requestedBy,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(uploadApprovalService.list(status, keyword, requestedBy, page, size));
    }

    @PostMapping("/api/v1/admin/upload-approvals/{id}/approve")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "批准上传", description = "临时文件移入正式存储并创建业务记录，返回目标实体 id")
    public ApiResponse<UploadApprovalItem> approve(@PathVariable UUID id) {
        return ApiResponse.success(uploadApprovalService.approve(
                id, currentUserProvider.currentUserId().orElse(null)));
    }

    @PostMapping("/api/v1/admin/upload-approvals/{id}/reject")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "拒绝上传", description = "**立即删除临时文件**，记录标记 REJECTED")
    public ApiResponse<UploadApprovalItem> reject(@PathVariable UUID id,
                                                  @Valid @RequestBody(required = false) RejectUploadRequest request) {
        String note = request == null ? null : request.note();
        return ApiResponse.success(uploadApprovalService.reject(
                id, note, currentUserProvider.currentUserId().orElse(null)));
    }
}
