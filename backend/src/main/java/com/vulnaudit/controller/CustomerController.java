package com.vulnaudit.controller;

import com.vulnaudit.dto.ApiResponse;
import com.vulnaudit.dto.CustomerDetailResponse;
import com.vulnaudit.dto.CustomerRequest;
import com.vulnaudit.dto.CustomerResponse;
import com.vulnaudit.dto.CustomerSummaryResponse;
import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.security.CurrentUserProvider;
import com.vulnaudit.service.CustomerService;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 客户管理控制器（t68）
 *
 * <p>权限口径：<b>读</b>对所有登录用户开放（客户/项目是组织维度，不作为权限边界）；
 * <b>写</b>限 {@code ADMIN / PROJECT_LEADER}。删除有项目归属保护（409）。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@Tag(name = "客户管理", description = "客户 CRUD、状态变更与聚合统计")
@SecurityRequirement(name = "bearerAuth")
public class CustomerController {

    private final CustomerService customerService;
    private final CurrentUserProvider currentUserProvider;
    private final com.vulnaudit.service.AccessGuard accessGuard;
    private final com.vulnaudit.service.impl.CustomerProjectStatsService statsService;

    @GetMapping
    @Operation(summary = "分页查询客户", description = "支持 keyword / status / level 筛选，分页 size 上限 500")
    public ResponseEntity<ApiResponse<PageResponse<CustomerResponse>>> listCustomers(
            @Parameter(description = "页码（从0开始）") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "关键词（编码/名称/行业）") @RequestParam(required = false) String keyword,
            @Parameter(description = "状态 active/inactive") @RequestParam(required = false) String status,
            @Parameter(description = "级别") @RequestParam(required = false) String level) {

        log.debug("查询客户列表: page={}, size={}, keyword={}, status={}, level={}", page, size, keyword, status, level);
        return ResponseEntity.ok(ApiResponse.success(customerService.findAll(page, size, keyword, status, level)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "客户详情", description = "含 projectCount / activeProjectCount / assetCount 聚合")
    public ResponseEntity<ApiResponse<CustomerDetailResponse>> getCustomer(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(customerService.findDetail(id)));
    }

    /**
     * 客户详情聚合（t76）：一次请求拿全「客户 + 统计 + 项目列表（含期次数/资产数）+ 最近 10 个资产」。
     *
     * <p>读权限与 {@code GET /customers/{id}} 一致（登录即可）。
     *
     * <p><b>数据边界</b>：{@code stats.assetCount} / {@code stats.vulnerabilityCount} /
     * {@code recentAssets} 按 {@code AccessGuard.visibleAssetIds(auth)} 收敛（资产是权限边界，
     * 与 {@code GET /assets} 列表口径一致）；{@code projects} 与项目级 {@code phaseCount/assetCount}
     * 保持全量口径（客户/项目属组织维度，读对全员开放），否则「客户下有几个项目、每个项目几期」
     * 对非管理层会失真。
     */
    @GetMapping("/{id}/summary")
    @Operation(summary = "客户详情聚合（项目 + 最近资产）",
            description = "一次返回 customer / stats / projects（含 phaseCount、assetCount）/ recentAssets（最近 10 条）；"
                    + "资产相关数字按可见范围收敛，项目列表为全量组织口径")
    public ResponseEntity<ApiResponse<CustomerSummaryResponse>> getCustomerSummary(@PathVariable UUID id) {
        java.util.Set<UUID> visibleAssetIds = accessGuard.visibleAssetIds(
                SecurityContextHolder.getContext().getAuthentication());
        log.debug("客户详情聚合: id={}, visibilityRestricted={}", id, visibleAssetIds != null);
        return ResponseEntity.ok(ApiResponse.success(customerService.findSummary(id, visibleAssetIds)));
    }

    @GetMapping("/{id}/stats")
    @Operation(summary = "客户统计（t70）",
            description = "项目类计数不随可见性变化；assetCount / distinctAssetCount / vulnerabilityCount "
                    + "按调用者可见资产范围收敛（管理层全量）")
    public ResponseEntity<ApiResponse<com.vulnaudit.dto.CustomerStatsResponse>> getCustomerStats(
            @PathVariable UUID id) {
        java.util.Set<UUID> visibleAssetIds = accessGuard.visibleAssetIds(
                SecurityContextHolder.getContext().getAuthentication());
        log.debug("客户统计: id={}, visibilityRestricted={}", id, visibleAssetIds != null);
        return ResponseEntity.ok(ApiResponse.success(statsService.customerStats(id, visibleAssetIds)));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_LEADER')")
    @Operation(summary = "创建客户", description = "仅 ADMIN / PROJECT_LEADER；code 全局唯一（重复 409）")
    public ResponseEntity<ApiResponse<CustomerResponse>> createCustomer(@Valid @RequestBody CustomerRequest request) {
        UUID operatorId = currentUserProvider.currentUserId().orElse(null);
        CustomerResponse created = customerService.create(request, operatorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_LEADER')")
    @Operation(summary = "更新客户", description = "仅 ADMIN / PROJECT_LEADER；code 冲突 409")
    public ResponseEntity<ApiResponse<CustomerResponse>> updateCustomer(
            @PathVariable UUID id,
            @Valid @RequestBody CustomerRequest request) {

        return ResponseEntity.ok(ApiResponse.success(customerService.update(id, request)));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_LEADER')")
    @Operation(summary = "变更客户状态", description = "active ↔ inactive")
    public ResponseEntity<ApiResponse<CustomerResponse>> changeCustomerStatus(
            @PathVariable UUID id,
            @Parameter(description = "目标状态 active/inactive") @RequestParam String status) {

        return ResponseEntity.ok(ApiResponse.success(customerService.changeStatus(id, status)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_LEADER')")
    @Operation(summary = "删除客户", description = "该客户下仍有项目时返回 409")
    public ResponseEntity<ApiResponse<Void>> deleteCustomer(@PathVariable UUID id) {
        customerService.delete(id);
        // t140：软删，统一 204（对象仍在库中，只是默认查询不可见）
        return ResponseEntity.noContent().build();
    }
}
