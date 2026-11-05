package com.vulnaudit.controller;

import com.vulnaudit.dto.ApiResponse;
import com.vulnaudit.dto.PasswordResetConfirmRequest;
import com.vulnaudit.dto.PasswordResetSendCodeRequest;
import com.vulnaudit.dto.PasswordResetSendCodeResponse;
import com.vulnaudit.service.PasswordResetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 忘记密码（公开接口，t103）
 *
 * <p>两个端点都在 {@code SecurityConfig} 中 {@code permitAll}，防线全部在服务层：
 * <ul>
 *   <li>{@code POST /auth/password-reset/send-code}：只对「已绑定并验证」的手机号/已验证的邮箱发码；
 *       不存在的 target 也返回 200（防账号枚举），内部不发码；限流沿用 t102（冷却 + 每小时上限 + IP 上限）；</li>
 *   <li>{@code POST /auth/password-reset/confirm}：验证码哈希比对 + 过期 + 尝试上限 + 一次性消费；
 *       密码强度由 {@code PasswordPolicy} 统一校验；成功后写 {@code RESET_PASSWORD} 审计并让该用户所有旧 token 失效。</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth/password-reset")
@RequiredArgsConstructor
@Tag(name = "忘记密码", description = "验证码重置密码（公开，需限流）")
public class AuthPasswordResetController {

    private final PasswordResetService passwordResetService;

    @PostMapping("/send-code")
    @Operation(summary = "发送重置密码验证码",
            description = "公开接口。channel=sms|email，target 为已绑定并验证的手机号或已验证的邮箱。"
                    + "target 不存在时同样返回 200（不泄露账号是否存在），但不会发送验证码")
    public ApiResponse<PasswordResetSendCodeResponse> sendCode(
            @Valid @RequestBody PasswordResetSendCodeRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        return ApiResponse.success(passwordResetService.sendCode(
                request.channel(), request.target(), resolveClientIp(httpRequest)));
    }

    @PostMapping("/confirm")
    @Operation(summary = "校验验证码并重置密码",
            description = "公开接口。验证码一次性消费；新密码需 ≥8 位且含大小写/数字/符号至少三类；"
                    + "成功后更新密码并使该用户所有已签发 token 失效")
    public ApiResponse<Void> confirm(@Valid @RequestBody PasswordResetConfirmRequest request,
                                     jakarta.servlet.http.HttpServletRequest httpRequest) {
        passwordResetService.confirm(request.target(), request.code(), request.newPassword(),
                resolveClientIp(httpRequest));
        return ApiResponse.success(null, "密码已重置，请使用新密码登录");
    }

    /** 取真实客户端 IP：反向代理场景优先取 X-Forwarded-For 首个地址 */
    private String resolveClientIp(jakarta.servlet.http.HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
