package com.vulnaudit.controller;

import com.vulnaudit.dto.ApiResponse;
import com.vulnaudit.dto.UpdateSsoProviderRequest;
import com.vulnaudit.entity.SsoProvider;
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
import java.util.Map;

/**
 * 管理员开关 SSO 提供商（t104）
 *
 * <p>权限：仅 ADMIN。审计：服务层写 {@code UPDATE_SSO_PROVIDER}（含 provider、enabled、configured），
 * 端点已加入 {@code AuditTrailInterceptor.SKIP_ENDPOINTS}，一次开关只有 1 条审计。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/sso/providers")
@RequiredArgsConstructor
@Tag(name = "SSO 提供商管理", description = "查看与开关第三方登录方式")
@SecurityRequirement(name = "bearerAuth")
public class AdminSsoController {

    private final SsoService ssoService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "全部提供商（含未开启/未配置）")
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.success(ssoService.allProviders().stream().map(this::toView).toList());
    }

    @PatchMapping("/{code}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "开关第三方登录方式",
            description = "enabled=true 但未配置 client_id/secret 时仍不对外展示，authorize-url 返回 503")
    public ApiResponse<Map<String, Object>> toggle(@PathVariable String code,
                                                   @Valid @RequestBody UpdateSsoProviderRequest request) {
        SsoProvider provider = ssoService.toggle(code, Boolean.TRUE.equals(request.enabled()),
                currentUserProvider.currentUserId().orElse(null));
        return ApiResponse.success(toView(provider), "已更新");
    }

    private Map<String, Object> toView(SsoProvider provider) {
        return Map.of(
                "code", provider.getCode(),
                "name", provider.getName(),
                "icon", provider.getIcon() == null ? "" : provider.getIcon(),
                "enabled", provider.isEnabled(),
                "configured", provider.isConfigured(),
                "usable", provider.isUsable(),
                // 凭据不回传，只回传「是否已配置」
                "scopes", provider.getScopes() == null ? "" : provider.getScopes());
    }
}
