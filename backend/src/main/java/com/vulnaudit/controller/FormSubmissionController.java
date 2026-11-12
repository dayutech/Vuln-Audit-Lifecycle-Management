package com.vulnaudit.controller;

import com.vulnaudit.dto.ApiResponse;
import com.vulnaudit.dto.FormSubmissionCreatedVO;
import com.vulnaudit.dto.FormSubmissionRequests;
import com.vulnaudit.dto.FormSubmissionSummaryVO;
import com.vulnaudit.dto.FormSubmissionVO;
import com.vulnaudit.dto.FormValueVO;
import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.service.FormSubmissionService;
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
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * 自定义填表系统 · 表单实例（t179，第 2/5 块）
 *
 * <p>登录即可用（不是 ADMIN 专属）：发起人对自己可见的客户/项目/资产生成表单；
 * 读权限由服务层判定（发起人 / 被指派人 / 确认人 / ADMIN，其他人 404）。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@Tag(name = "填表系统-表单实例", description = "生成（库快照）/ 指派 / 实例级字段 / 我的表单")
@SecurityRequirement(name = "bearerAuth")
public class FormSubmissionController {

    private final FormSubmissionService formSubmissionService;

    @PostMapping("/api/v1/forms/submissions")
    @Operation(summary = "生成表单实例",
            description = "对 CUSTOMER/PROJECT/ASSET 生成；source_value 是**生成时快照**，之后业务数据变化不影响它。"
                    + "返回 snapshotFilled/snapshotNull/skippedSources 便于前端提示")
    public ResponseEntity<ApiResponse<FormSubmissionCreatedVO>> create(
            @Valid @RequestBody FormSubmissionRequests.Create request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(formSubmissionService.create(request, authentication)));
    }

    @PostMapping("/api/v1/forms/submissions/{id}/assign")
    @Operation(summary = "指派表单", description = "仅发起人或 ADMIN；DRAFT/ASSIGNED → ASSIGNED；已提交/已确认 → 409")
    public ApiResponse<FormSubmissionVO> assign(@PathVariable UUID id,
                                                @Valid @RequestBody FormSubmissionRequests.Assign request,
                                                Authentication authentication) {
        return ApiResponse.success(formSubmissionService.assign(id, request, authentication));
    }

    @PostMapping("/api/v1/forms/submissions/{id}/fields")
    @Operation(summary = "发起人加字段（实例级）",
            description = "只允许手填类型（不能指定 sourceType）；field_key 自动生成 instance_N；sort_order 接在模板字段之后")
    public ResponseEntity<ApiResponse<FormValueVO>> addField(
            @PathVariable UUID id, @Valid @RequestBody FormSubmissionRequests.InstanceField request,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(formSubmissionService.addInstanceField(id, request, authentication)));
    }

    @DeleteMapping("/api/v1/forms/submissions/{id}/fields/{fieldId}")
    @Operation(summary = "删除实例级字段", description = "仅发起人；已填写 → 409；模板字段不可经此删除")
    public ApiResponse<Void> deleteField(@PathVariable UUID id, @PathVariable UUID fieldId,
                                         Authentication authentication) {
        formSubmissionService.deleteInstanceField(id, fieldId, authentication);
        return ApiResponse.success(null, "已删除");
    }

    @GetMapping("/api/v1/forms/submissions/mine")
    @Operation(summary = "我发起的表单", description = "分页；status 可空 = 全部；含进度 N/M 与指派对象")
    public ApiResponse<PageResponse<FormSubmissionSummaryVO>> mine(
            @Parameter(description = "状态：DRAFT/ASSIGNED/SUBMITTED/CONFIRMED/RETURNED/all")
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        return ApiResponse.success(formSubmissionService.findMine(authentication, status, page, size));
    }

    @GetMapping("/api/v1/forms/submissions/{id}")
    @Operation(summary = "表单详情", description = "发起人/被指派人/确认人/ADMIN 可读；其他人 404（不泄露存在性）")
    public ApiResponse<FormSubmissionVO> detail(@PathVariable UUID id, Authentication authentication) {
        return ApiResponse.success(formSubmissionService.detail(id, authentication));
    }
}
