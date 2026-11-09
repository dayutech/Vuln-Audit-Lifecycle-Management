package com.vulnaudit.controller;

import com.vulnaudit.dto.ApiResponse;
import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.dto.TagDefinitionUpsertRequest;
import com.vulnaudit.dto.TagDefinitionVO;
import com.vulnaudit.dto.TagOptionVO;
import com.vulnaudit.security.CurrentUserProvider;
import com.vulnaudit.service.TagDefinitionService;
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

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * 标签体系（t141）
 *
 * <p>两个入口：
 * <ul>
 *   <li>{@code GET /api/v1/tags}：登录即可，返回 {@code [{id,name,color,builtin,assetCount}]}，
 *       {@code assetCount} 按调用者可见范围收敛（与 {@code GET /assets} 同一口径）；</li>
 *   <li>{@code GET/POST/PUT/DELETE /api/v1/admin/tag-definitions}：仅 ADMIN，内置标签只能改颜色/排序。</li>
 * </ul>
 *
 * <p>删除为软删（{@code deleted_at}）且**名称唯一含软删行**（软删后同名不可复用）；被资产引用时 409。
 * 写操作由服务层写 1 条 {@code UPDATE_TAG_DEFINITION}，端点已进 {@code AuditTrailInterceptor.SKIP_ENDPOINTS}。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "标签体系", description = "标签下拉与管理员维护（内置不可改名/删除）")
@SecurityRequirement(name = "bearerAuth")
public class TagDefinitionController {

    private final TagDefinitionService tagDefinitionService;
    private final com.vulnaudit.service.AccessGuard accessGuard;
    private final CurrentUserProvider currentUserProvider;

    // ------------------------------------------------------------------ 下拉（登录即可）

    @GetMapping("/api/v1/tags")
    @Operation(summary = "标签下拉", description = "未软删的标签 + assetCount（按可见范围收敛）")
    public ApiResponse<List<TagOptionVO>> options(org.springframework.security.core.Authentication authentication) {
        Set<UUID> visible = accessGuard.visibleAssetIds(authentication);
        return ApiResponse.success(tagDefinitionService.options(visible));
    }

    // ------------------------------------------------------------------ 管理员

    @GetMapping("/api/v1/admin/tag-definitions")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "标签定义列表（分页）", description = "keyword 匹配 name；includeDeleted=true 含软删行")
    public ApiResponse<PageResponse<TagDefinitionVO>> list(
            @Parameter(description = "关键字（名称）") @RequestParam(required = false) String keyword,
            @Parameter(description = "是否包含已软删") @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(tagDefinitionService.adminList(keyword, includeDeleted, page, size));
    }

    @PostMapping("/api/v1/admin/tag-definitions")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "新建标签", description = "builtin 恒为 false；name 重复（含软删行）409")
    public ResponseEntity<ApiResponse<TagDefinitionVO>> create(
            @Valid @RequestBody TagDefinitionUpsertRequest request) {
        TagDefinitionVO created = tagDefinitionService.create(request, operatorId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @PutMapping("/api/v1/admin/tag-definitions/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "更新标签", description = "内置标签改 name → 403；可改 color/sortOrder")
    public ApiResponse<TagDefinitionVO> update(@PathVariable UUID id,
                                               @Valid @RequestBody TagDefinitionUpsertRequest request) {
        return ApiResponse.success(tagDefinitionService.update(id, request, operatorId()));
    }

    @DeleteMapping("/api/v1/admin/tag-definitions/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "删除标签（软删）", description = "内置 403；被资产引用 → 409（带引用数）")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        tagDefinitionService.delete(id, operatorId());
        return ApiResponse.success(null, "已删除");
    }

    private UUID operatorId() {
        return currentUserProvider.currentUserId().orElse(null);
    }
}
