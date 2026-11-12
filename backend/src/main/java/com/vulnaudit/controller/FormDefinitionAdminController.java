package com.vulnaudit.controller;

import com.vulnaudit.dto.ApiResponse;
import com.vulnaudit.dto.FormDefinitionRequests;
import com.vulnaudit.dto.FormDefinitionVO;
import com.vulnaudit.dto.FormFieldVO;
import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.security.CurrentUserProvider;
import com.vulnaudit.service.FormDefinitionService;
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
 * 自定义填表系统 · 模板与字段 CRUD（t178，仅 ADMIN）
 *
 * <p>本块（1/5）只做模板/字段维护；表单实例生成（快照）、填写、确认、导出在后续块。
 * 写操作各写 1 条审计（`UPDATE_FORM_DEFINITION` / `UPDATE_FORM_FIELD`），端点已进 `SKIP_ENDPOINTS`。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "填表系统-模板管理", description = "表单模板与字段 CRUD（ADMIN）")
@SecurityRequirement(name = "bearerAuth")
public class FormDefinitionAdminController {

    private final FormDefinitionService formDefinitionService;
    private final CurrentUserProvider currentUserProvider;

    // ------------------------------------------------------------------ 模板

    @GetMapping("/api/v1/admin/form-definitions")
    @Operation(summary = "模板列表（分页）", description = "keyword 匹配 code/name；enabled 可空 = 不限；含 fieldCount")
    public ApiResponse<PageResponse<FormDefinitionVO>> list(
            @Parameter(description = "关键字") @RequestParam(required = false) String keyword,
            @Parameter(description = "是否启用（空 = 不限）") @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(formDefinitionService.list(keyword, enabled, page, size));
    }

    @PostMapping("/api/v1/admin/form-definitions")
    @Operation(summary = "新建模板", description = "code 唯一（含软删行）→ 409")
    public ResponseEntity<ApiResponse<FormDefinitionVO>> create(
            @Valid @RequestBody FormDefinitionRequests.CreateDefinition request) {
        FormDefinitionVO created = formDefinitionService.create(request, operatorId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @GetMapping("/api/v1/admin/form-definitions/{id}")
    @Operation(summary = "模板详情", description = "含 fields[]（按 sort_order）")
    public ApiResponse<FormDefinitionVO> detail(@PathVariable UUID id) {
        return ApiResponse.success(formDefinitionService.detail(id));
    }

    @PutMapping("/api/v1/admin/form-definitions/{id}")
    @Operation(summary = "更新模板", description = "可改 name/description/enabled；**code 不可改**（改 → 403）")
    public ApiResponse<FormDefinitionVO> update(@PathVariable UUID id,
                                                @Valid @RequestBody FormDefinitionRequests.UpdateDefinition request) {
        return ApiResponse.success(formDefinitionService.update(id, request, operatorId()));
    }

    @DeleteMapping("/api/v1/admin/form-definitions/{id}")
    @Operation(summary = "删除模板（软删）", description = "已被表单实例引用 → 409（带引用数）")
    public ApiResponse<Void> delete(@PathVariable UUID id) {
        formDefinitionService.delete(id, operatorId());
        return ApiResponse.success(null, "已删除");
    }

    // ------------------------------------------------------------------ 字段

    @PostMapping("/api/v1/admin/form-definitions/{id}/fields")
    @Operation(summary = "新增字段", description = "field_key 同模板唯一（409）；field_type/source_type 白名单（400）；SELECT 必须带 options")
    public ResponseEntity<ApiResponse<FormFieldVO>> addField(
            @PathVariable UUID id, @Valid @RequestBody FormDefinitionRequests.FieldUpsert request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(formDefinitionService.addField(id, request, operatorId())));
    }

    @PutMapping("/api/v1/admin/form-definitions/{id}/fields/{fieldId}")
    @Operation(summary = "更新字段", description = "已有表单数据时禁止改 field_type/field_key（409），可改 label/required/sort_order")
    public ApiResponse<FormFieldVO> updateField(@PathVariable UUID id, @PathVariable UUID fieldId,
                                                @Valid @RequestBody FormDefinitionRequests.FieldUpsert request) {
        return ApiResponse.success(formDefinitionService.updateField(id, fieldId, request, operatorId()));
    }

    @DeleteMapping("/api/v1/admin/form-definitions/{id}/fields/{fieldId}")
    @Operation(summary = "删除字段（软删）", description = "已有表单数据 → 409")
    public ApiResponse<Void> deleteField(@PathVariable UUID id, @PathVariable UUID fieldId) {
        formDefinitionService.deleteField(id, fieldId, operatorId());
        return ApiResponse.success(null, "已删除");
    }

    @PutMapping("/api/v1/admin/form-definitions/{id}/fields/reorder")
    @Operation(summary = "字段重排序", description = "fieldIds 必须正好覆盖该模板全部字段；重排后 sort_order 连续（0..n-1）")
    public ApiResponse<FormDefinitionVO> reorder(@PathVariable UUID id,
                                                 @Valid @RequestBody FormDefinitionRequests.Reorder request) {
        return ApiResponse.success(formDefinitionService.reorderFields(id, request.fieldIds(), operatorId()));
    }

    private UUID operatorId() {
        return currentUserProvider.currentUserId().orElse(null);
    }
}
