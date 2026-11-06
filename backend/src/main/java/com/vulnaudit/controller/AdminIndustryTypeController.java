package com.vulnaudit.controller;

import com.vulnaudit.dto.ApiResponse;
import com.vulnaudit.dto.IndustryTypeAdminVO;
import com.vulnaudit.dto.IndustryTypeUpsertRequest;
import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.security.CurrentUserProvider;
import com.vulnaudit.service.IndustryTypeService;
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

import java.util.UUID;

/**
 * 行业类型管理（t130，仅 ADMIN）
 *
 * <p>规则：
 * - 新建项 {@code builtin=false}；code 全局唯一（冲突 409）；
 * - 内置项 code/name 不可改（403「内置行业不可修改」），可调整 sortOrder / enabled；
 * - 仅自定义项可删；被客户引用（{@code customers.industry_code}）时 409；
 * - 删除为软删（{@code deleted_at}），历史客户引用仍可解释；
 * - 写操作各审计 1 条 {@code UPDATE_INDUSTRY_TYPE}（端点已加入
 *   {@code AuditTrailInterceptor.SKIP_ENDPOINTS}，避免拦截器再写一条）。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/industry-types")
@RequiredArgsConstructor
@Tag(name = "行业类型管理", description = "管理员维护行业分类（内置项不可改删）")
@SecurityRequirement(name = "bearerAuth")
public class AdminIndustryTypeController {

    private final IndustryTypeService industryTypeService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "行业类型列表（分页）", description = "含停用项（includeDisabled=true）；keyword 匹配 code/name")
    public ApiResponse<PageResponse<IndustryTypeAdminVO>> list(
            @Parameter(description = "关键字（code 或名称）") @RequestParam(required = false) String keyword,
            @Parameter(description = "是否包含停用项") @RequestParam(defaultValue = "false") boolean includeDisabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(industryTypeService.adminList(keyword, includeDisabled, page, size));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "新建行业类型", description = "builtin 恒为 false；code 重复 409")
    public ResponseEntity<ApiResponse<IndustryTypeAdminVO>> create(@Valid @RequestBody IndustryTypeUpsertRequest request) {
        IndustryTypeAdminVO created = industryTypeService.create(request, operatorId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "更新行业类型", description = "内置项改 code/name → 403；可改 sortOrder/enabled")
    public ResponseEntity<ApiResponse<IndustryTypeAdminVO>> update(@PathVariable UUID id,
                                                                 @Valid @RequestBody IndustryTypeUpsertRequest request) {
        return ResponseEntity.ok(ApiResponse.success(industryTypeService.update(id, request, operatorId())));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "删除行业类型（软删）", description = "内置项 403；被客户引用 409")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        industryTypeService.delete(id, operatorId());
        return ApiResponse.success(null, "已删除");
    }

    private UUID operatorId() {
        return currentUserProvider.currentUserId().orElse(null);
    }
}
