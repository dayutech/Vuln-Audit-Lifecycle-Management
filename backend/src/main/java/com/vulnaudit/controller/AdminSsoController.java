package com.vulnaudit.controller;

import com.vulnaudit.dto.ApiResponse;
import com.vulnaudit.dto.SsoProviderConfigView;
import com.vulnaudit.dto.UpdateSsoProviderRequest;
import com.vulnaudit.security.CurrentUserProvider;
import com.vulnaudit.service.SsoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员配置 SSO 提供商（t104 开关 / t116 凭据 Web 配置）
 *
 * <p>权限：仅 ADMIN。审计：服务层写 {@code UPDATE_SSO_PROVIDER}（details 只记变更字段名，secret 记 {@code ***}），
 * 端点已加入 {@code AuditTrailInterceptor.SKIP_ENDPOINTS}，一次保存只有 1 条审计。
 *
 * <p><b>安全</b>：secret 只以掩码返回（{@code gh_****abcd}），接口/日志/审计都不出现明文。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/sso/providers")
@RequiredArgsConstructor
@Tag(name = "SSO 提供商管理", description = "查看与配置第三方登录方式（凭据 Web 化，无需上服务器改库）")
@SecurityRequirement(name = "bearerAuth")
public class AdminSsoController {

    private final SsoService ssoService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "全部提供商配置（含未开启/未配置；secret 仅掩码）")
    public ApiResponse<List<SsoProviderConfigView>> list() {
        return ApiResponse.success(ssoService.providerConfigs());
    }

    /**
     * 保存配置（t116 正式语义，部分更新）
     *
     * <p>{@code clientSecret} 省略/空串 = 保持不变；要清空传 {@code clearClientSecret=true}。
     */
    @PutMapping("/{code}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "保存第三方登录配置（部分更新）",
            description = "支持 enabled/clientId/clientSecret/authorizeUrl/tokenUrl/userinfoUrl/scopes；"
                    + "secret 省略或空串保持不变，clearClientSecret=true 才清空；URL 必须 https"
                    + "（本地允许 http://localhost 与 http://127.0.0.1）；保存即时生效")
    public ApiResponse<SsoProviderConfigView> update(@PathVariable String code,
                                                     @Valid @RequestBody UpdateSsoProviderRequest request) {
        return ApiResponse.success(ssoService.updateProvider(code, request,
                currentUserProvider.currentUserId().orElse(null)), "已保存");
    }

    /**
     * 兼容 t104 的开关端点：等价于 {@code PUT} 且只传 enabled。
     */
    @PatchMapping("/{code}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "开关第三方登录方式（等价于 PUT 只传 enabled）",
            description = "enabled=true 但未配置时仍会出现在登录页（configured=false），authorize-url 返回 503")
    public ApiResponse<SsoProviderConfigView> toggle(@PathVariable String code,
                                                     @Valid @RequestBody UpdateSsoProviderRequest request) {
        return ApiResponse.success(ssoService.updateProvider(code, request,
                currentUserProvider.currentUserId().orElse(null)), "已更新");
    }
}
