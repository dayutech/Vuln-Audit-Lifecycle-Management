package com.vulnaudit.controller;

import com.vulnaudit.dto.ApiResponse;
import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.dto.ProjectTypeAdminVO;
import com.vulnaudit.dto.ProjectTypeOption;
import com.vulnaudit.dto.ProjectTypeUpsertRequest;
import com.vulnaudit.security.CurrentUserProvider;
import com.vulnaudit.service.ProjectTypeService;
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
import java.util.UUID;

/**
 * 项目类型（t130）
 *
 * <p>两个入口：
 * - `GET /api/v1/project-types`：**登录即可**，仅返回启用的类型，供建项目时下拉；
 * - `GET/POST/PUT/DELETE /api/v1/admin/project-types`：仅 ADMIN，管理内置 + 自定义项。
 *
 * <p>规则与行业类型一致：内置项（PENTEST/CODE_AUDIT/EMERGENCY/COMPLIANCE/TRAINING）
 * code/name 不可改、不可删（403）；被项目引用时删除 409；删除为软删；写操作各审计 1 条
 * {@code UPDATE_PROJECT_TYPE}（端点已加入 {@code AuditTrailInterceptor.SKIP_ENDPOINTS}）。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "项目类型", description = "项目类型下拉与管理员维护")
@SecurityRequirement(name = "bearerAuth")
public class ProjectTypeController {

    private final ProjectTypeService projectTypeService;
    private final CurrentUserProvider currentUserProvider;

    // ------------------------------------------------------------------ 下拉（登录即可）

    @GetMapping("/api/v1/project-types")
    @Operation(summary = "项目类型下拉", description = "仅返回启用的类型，内置优先 + sort_order")
    public ApiResponse<List<ProjectTypeOption>> options() {
        return ApiResponse.success(projectTypeService.options());
    }

    // ------------------------------------------------------------------ 管理员

    @GetMapping("/api/v1/admin/project-types")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "项目类型列表（分页）", description = "含停用项（includeDisabled=true）；keyword 匹配 code/name")
    public ApiResponse<PageResponse<ProjectTypeAdminVO>> list(
            @Parameter(description = "关键字（code 或名称）") @RequestParam(required = false) String keyword,
            @Parameter(description = "是否包含停用项") @RequestParam(defaultValue = "false") boolean includeDisabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(projectTypeService.adminList(keyword, includeDisabled, page, size));
    }

    @PostMapping("/api/v1/admin/project-types")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "新建项目类型", description = "builtin 恒为 false；code 重复 409")
    public ResponseEntity<ApiResponse<ProjectTypeAdminVO>> create(@Valid @RequestBody ProjectTypeUpsertRequest request) {
        ProjectTypeAdminVO created = projectTypeService.create(request, operatorId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @PutMapping("/api/v1/admin/project-types/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "更新项目类型", description = "内置项改 code/name → 403；可改 sortOrder/enabled")
    public ResponseEntity<ApiResponse<ProjectTypeAdminVO>> update(@PathVariable UUID id,
                                                                 @Valid @RequestBody ProjectTypeUpsertRequest request) {
        return ResponseEntity.ok(ApiResponse.success(projectTypeService.update(id, request, operatorId())));
    }

    @DeleteMapping("/api/v1/admin/project-types/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "删除项目类型（软删）", description = "内置项 403；被项目引用 409")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        projectTypeService.delete(id, operatorId());
        return ApiResponse.success(null, "已删除");
    }

    private UUID operatorId() {
        return currentUserProvider.currentUserId().orElse(null);
    }
}
