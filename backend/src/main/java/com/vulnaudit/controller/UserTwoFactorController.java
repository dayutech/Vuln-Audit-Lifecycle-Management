package com.vulnaudit.controller;

import com.vulnaudit.dto.ApiResponse;
import com.vulnaudit.dto.TwoFactorInitResponse;
import com.vulnaudit.dto.TwoFactorStatusResponse;
import com.vulnaudit.entity.User;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.security.CurrentUserProvider;
import com.vulnaudit.service.TwoFactorService;
import com.vulnaudit.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 我的双因子认证（t125）
 *
 * <p>流程：init（拿密钥与 otpauth URI，扫码导入 App）→ confirm（输入动态码确认开启，返回 10 个恢复码，仅此一次）→
 * 之后登录走两阶段；关闭需「密码 + 动态码/恢复码」双确认，管理员强制时不允许关闭。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/users/me/2fa")
@RequiredArgsConstructor
@Tag(name = "我的双因子认证", description = "TOTP 初始化 / 确认 / 关闭 / 状态")
public class UserTwoFactorController {

    private final TwoFactorService twoFactorService;
    private final UserService userService;
    private final CurrentUserProvider currentUserProvider;

    @GetMapping
    @Operation(summary = "查询双因子状态", description = "不返回密钥；含恢复码剩余数量、是否被管理员强制、功能是否可用")
    public ApiResponse<TwoFactorStatusResponse> status() {
        return ApiResponse.success(twoFactorService.status(currentUserId()));
    }

    @PostMapping("/init")
    @Operation(summary = "初始化双因子", description = "生成随机 TOTP 密钥（加密存储）并返回 otpauth URI；**不会直接开启**")
    public ApiResponse<TwoFactorInitResponse> init() {
        return ApiResponse.success(twoFactorService.init(currentUserId()), "请用验证器 App 扫码后输入动态码确认");
    }

    @PostMapping("/confirm")
    @Operation(summary = "确认开启双因子",
            description = "校验动态码（±1 时间窗）通过后开启，并返回 10 个恢复码（仅此一次，请妥善保存）")
    public ApiResponse<List<String>> confirm(@Valid @RequestBody TwoFactorConfirmRequest request) {
        UUID userId = currentUserId();
        List<String> recoveryCodes = twoFactorService.confirm(userId, request.code());
        return ApiResponse.success(recoveryCodes, "双因子已开启，请保存恢复码（仅显示一次）");
    }

    @PostMapping("/disable")
    @Operation(summary = "关闭双因子", description = "需当前密码 + 动态码（或恢复码）双确认；管理员强制开启时返回 403")
    public ApiResponse<Void> disable(@Valid @RequestBody TwoFactorDisableRequest request) {
        UUID userId = currentUserId();
        twoFactorService.disable(userId, request.password(), request.code());
        return ApiResponse.success(null, "双因子已关闭");
    }

    private UUID currentUserId() {
        UUID userId = currentUserProvider.currentUserId().orElse(null);
        if (userId == null) {
            // 兜底：按用户名解析（保持与其它 me 接口一致的错误语义）
            String username = currentUserProvider.currentUsername().orElse(null);
            User user = username == null ? null : userService.findByUsername(username).orElse(null);
            if (user == null) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录或登录已失效");
            }
            userId = user.getId();
        }
        return userId;
    }

    /** 确认开启请求 */
    public record TwoFactorConfirmRequest(@NotBlank(message = "validation.twoFactor.code.required") String code) {
    }

    /** 关闭请求：密码 + 动态码/恢复码 */
    public record TwoFactorDisableRequest(
            @NotBlank(message = "validation.user.password.required") String password,
            @NotBlank(message = "validation.twoFactor.code.required") String code) {
    }
}
