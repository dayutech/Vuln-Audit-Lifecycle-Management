package com.vulnaudit.controller;

import com.vulnaudit.dto.ApiResponse;
import com.vulnaudit.dto.UnlockSendCodeResponse;
import com.vulnaudit.service.AccountUnlockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 账户锁定后的 OTP 解锁（公开接口，t123）
 *
 * <p>两个端点都在 {@code SecurityConfig} 中 {@code permitAll}：用户被锁定时还没拿到 token。
 * 防线在服务层：只对绑定并已验证的手机/邮箱发码、限流（username 冷却 + IP 每小时上限）、
 * 验证码哈希比对/过期/一次性消费（复用 t102 设施）。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth/unlock")
@RequiredArgsConstructor
@Tag(name = "账户解锁", description = "账户被锁后通过手机/邮箱验证码解锁（公开，需限流）")
public class AuthUnlockController {

    private final AccountUnlockService accountUnlockService;

    @PostMapping("/send-code")
    @Operation(summary = "发送解锁验证码",
            description = "向该用户绑定并已验证的手机/邮箱发送 6 位验证码（purpose=UNLOCK，5 分钟有效）。"
                    + "用户名不存在或无已验证联系方式时同样返回成功结构但不发码（防枚举）")
    public ApiResponse<UnlockSendCodeResponse> sendCode(
            @Valid @RequestBody UnlockSendCodeRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        return ApiResponse.success(accountUnlockService.sendCode(request.username(), resolveClientIp(httpRequest)),
                "如果信息匹配，验证码已发送");
    }

    @PostMapping("/confirm")
    @Operation(summary = "校验验证码并解锁",
            description = "验证码一次性消费；成功后解除账户锁定并写审计 ACCOUNT_UNLOCKED。不直接签发 token，请重新登录")
    public ApiResponse<Void> confirm(@Valid @RequestBody UnlockConfirmRequest request) {
        accountUnlockService.confirm(request.username(), request.target(), request.code());
        return ApiResponse.success(null, "账户已解锁，请使用新密码或原密码重新登录");
    }

    private String resolveClientIp(jakarta.servlet.http.HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /** 发送解锁验证码请求体 */
    public record UnlockSendCodeRequest(
            @NotBlank(message = "validation.user.username.required") String username) {
    }

    /** 校验解锁验证码请求体 */
    public record UnlockConfirmRequest(
            @NotBlank(message = "validation.user.username.required") String username,
            @NotBlank(message = "validation.verification.target.required") String target,
            @NotBlank(message = "validation.verification.code.required") String code) {
    }
}
