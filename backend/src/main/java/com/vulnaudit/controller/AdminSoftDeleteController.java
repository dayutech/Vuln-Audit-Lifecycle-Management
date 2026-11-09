package com.vulnaudit.controller;

import com.vulnaudit.dto.ApiResponse;
import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.dto.SoftDeleteAdminItem;
import com.vulnaudit.security.CurrentUserProvider;
import com.vulnaudit.service.SoftDeleteAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 软删管理（t140，仅 ADMIN）
 *
 * <p>普通接口看不到已删数据（实体上有 {@code @SQLRestriction("deleted_at IS NULL")}）；
 * 这里提供两件事：
 * <ul>
 *   <li>{@code GET /api/v1/admin/{customers|projects|assets}?includeDeleted=true} —— 含已删的分页列表；</li>
 *   <li>{@code POST /api/v1/admin/{customers|projects|assets}/{id}/restore} —— 恢复（清 deleted_at）。</li>
 * </ul>
 *
 * <p><b>恢复端点为什么进 SKIP_ENDPOINTS</b>：恢复动作由 {@code SoftDeleteAdminServiceImpl} 手工写
 * {@code RESTORE_CUSTOMER / RESTORE_PROJECT / RESTORE_ASSET}（带 kind 与 id），拦截器再记一条
 * 「POST AdminSoftDeleteController.restoreCustomer」会让一次恢复出现两条审计。
 *
 * <p><b>删除动作本身</b>仍由拦截器记录（DELETE 端点未跳过），action 形如
 * {@code DELETE ProjectController.deleteProject}，与既有口径一致。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Tag(name = "软删管理", description = "含已删列表与恢复（仅 ADMIN）")
@SecurityRequirement(name = "bearerAuth")
public class AdminSoftDeleteController {

    private final SoftDeleteAdminService softDeleteAdminService;
    private final CurrentUserProvider currentUserProvider;

    // ------------------------------------------------------------------ 客户

    @GetMapping("/customers")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "客户列表（含已删）", description = "includeDeleted=true 时包含软删行")
    public ApiResponse<PageResponse<SoftDeleteAdminItem>> customers(
            @Parameter(description = "code/名称关键字") @RequestParam(required = false) String keyword,
            @Parameter(description = "是否包含已删行") @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(softDeleteAdminService.page(
                SoftDeleteAdminService.Kind.CUSTOMER, keyword, includeDeleted, page, size));
    }

    @PostMapping("/customers/{id}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "恢复客户", description = "幂等：未删时同样返回 204；不存在 404")
    public ResponseEntity<ApiResponse<Void>> restoreCustomer(@PathVariable UUID id) {
        softDeleteAdminService.restore(SoftDeleteAdminService.Kind.CUSTOMER, id,
                currentUserProvider.currentUserId().orElse(null));
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ 项目

    @GetMapping("/projects")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "项目列表（含已删）")
    public ApiResponse<PageResponse<SoftDeleteAdminItem>> projects(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(softDeleteAdminService.page(
                SoftDeleteAdminService.Kind.PROJECT, keyword, includeDeleted, page, size));
    }

    @PostMapping("/projects/{id}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "恢复项目")
    public ResponseEntity<ApiResponse<Void>> restoreProject(@PathVariable UUID id) {
        softDeleteAdminService.restore(SoftDeleteAdminService.Kind.PROJECT, id,
                currentUserProvider.currentUserId().orElse(null));
        return ResponseEntity.noContent().build();
    }

    // ------------------------------------------------------------------ 资产

    @GetMapping("/assets")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "资产列表（含已删）")
    public ApiResponse<PageResponse<SoftDeleteAdminItem>> assets(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean includeDeleted,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(softDeleteAdminService.page(
                SoftDeleteAdminService.Kind.ASSET, keyword, includeDeleted, page, size));
    }

    @PostMapping("/assets/{id}/restore")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "恢复资产")
    public ResponseEntity<ApiResponse<Void>> restoreAsset(@PathVariable UUID id) {
        softDeleteAdminService.restore(SoftDeleteAdminService.Kind.ASSET, id,
                currentUserProvider.currentUserId().orElse(null));
        return ResponseEntity.noContent().build();
    }
}
