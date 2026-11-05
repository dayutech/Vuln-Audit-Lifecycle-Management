package com.vulnaudit.controller;

import com.vulnaudit.dto.ApiResponse;
import com.vulnaudit.dto.SsoAuthorizeResponse;
import com.vulnaudit.dto.SsoCallbackRequest;
import com.vulnaudit.dto.SsoCallbackResponse;
import com.vulnaudit.dto.SsoProviderOption;
import com.vulnaudit.service.SsoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 第三方 SSO 登录（公开接口，t104）
 *
 * <p>三个端点都在 {@code SecurityConfig} 中 permitAll：登录页在认证前就要拿到按钮列表，
 * 回调也发生在未登录状态。防线在服务层（enabled + configured + state 一次性 + 审批后才有绑定）。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth/sso")
@RequiredArgsConstructor
@Tag(name = "SSO 登录", description = "第三方登录：提供商列表 / 授权地址 / 回调")
public class AuthSsoController {

    private final SsoService ssoService;

    @GetMapping("/providers")
    @Operation(summary = "可用的第三方登录方式",
            description = "仅返回「管理员已开启且已配置凭据」的提供商；未配置的不返回（登录页据此渲染按钮）")
    public ApiResponse<List<SsoProviderOption>> providers() {
        return ApiResponse.success(ssoService.publicProviders());
    }

    @GetMapping("/{code}/authorize-url")
    @Operation(summary = "获取第三方授权地址",
            description = "返回 {url, state}；state 5 分钟有效、一次性。未开启 → 400，未配置凭据 → 503")
    public ApiResponse<SsoAuthorizeResponse> authorizeUrl(
            @Parameter(description = "提供商代码：github/microsoft/google/wechat") @PathVariable String code,
            @Parameter(description = "回调地址") @RequestParam(required = false) String redirectUri) {
        return ApiResponse.success(ssoService.authorizeUrl(code, redirectUri));
    }

    @PostMapping("/{code}/callback")
    @Operation(summary = "第三方回调",
            description = "已绑定且账号可用 → 200 并签发 token；陌生身份 → **202**（已提交管理员审批，不发 token）；"
                    + "账号非 ACTIVE → 403；state 无效/过期 → 400")
    public ResponseEntity<ApiResponse<SsoCallbackResponse>> callback(
            @PathVariable String code,
            @Valid @RequestBody SsoCallbackRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        String clientIp = resolveClientIp(httpRequest);
        SsoCallbackResponse result = ssoService.callback(code, request.code(), request.state(), clientIp);
        if ("PENDING_APPROVAL".equals(result.status())) {
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(ApiResponse.success(result, "已提交管理员审批，请耐心等待"));
        }
        return ResponseEntity.ok(ApiResponse.success(result,
                "LOGIN".equals(result.status()) ? "登录成功" : "绑定成功"));
    }

    private String resolveClientIp(jakarta.servlet.http.HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
