package com.vulnaudit.controller;

import com.vulnaudit.dto.ApiResponse;
import com.vulnaudit.dto.SsoAuthorizeResponse;
import com.vulnaudit.dto.SsoBindingVO;
import com.vulnaudit.dto.UnbindSsoRequest;
import com.vulnaudit.security.CurrentUserProvider;
import com.vulnaudit.service.SsoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 当前用户的 SSO 绑定管理（t104）
 *
 * <p>只能操作自己（固定 {@code /users/me}，用户ID 取自 {@link CurrentUserProvider}）；
 * 解绑必须带当前密码（防「拿到已登录会话的人直接解绑」）。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/users/me/sso-bindings")
@RequiredArgsConstructor
@Tag(name = "我的 SSO 绑定", description = "查看/发起绑定/解绑第三方登录")
@SecurityRequirement(name = "bearerAuth")
public class UserSsoBindingController {

    private final SsoService ssoService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    @Operation(summary = "我的绑定列表")
    public ApiResponse<List<SsoBindingVO>> list() {
        return ApiResponse.success(ssoService.bindings(currentUserId()));
    }

    @PostMapping("/{code}/start")
    @Operation(summary = "发起绑定",
            description = "返回带「绑定态」的授权地址；已绑定同一 provider → 409")
    public ApiResponse<SsoAuthorizeResponse> start(
            @Parameter(description = "提供商代码") @PathVariable String code,
            @Parameter(description = "回调地址") @RequestParam(required = false) String redirectUri) {
        return ApiResponse.success(ssoService.startBind(currentUserId(), code, redirectUri));
    }

    @DeleteMapping("/{code}")
    @Operation(summary = "解绑（需当前密码）",
            description = "密码错误 → 403；解绑后该 provider 不能再直接登录该账号（需重新走审批）")
    public ApiResponse<Void> unbind(@PathVariable String code,
                                    @Valid @RequestBody UnbindSsoRequest request) {
        ssoService.unbind(currentUserId(), code, request.password());
        return ApiResponse.success(null, "已解绑");
    }

    private UUID currentUserId() {
        return currentUserProvider.currentUserId().orElse(null);
    }
}
