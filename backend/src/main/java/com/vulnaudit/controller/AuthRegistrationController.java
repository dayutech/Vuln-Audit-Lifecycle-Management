package com.vulnaudit.controller;

import com.vulnaudit.dto.ApiResponse;
import com.vulnaudit.dto.RegisterRequest;
import com.vulnaudit.dto.RegistrationRequestVO;
import com.vulnaudit.dto.SendCodeRequest;
import com.vulnaudit.dto.SendCodeResponse;
import com.vulnaudit.service.RegistrationService;
import com.vulnaudit.service.VerificationCodeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 自助注册（公开接口，t102）
 *
 * <p>两个端点都在 {@code SecurityConfig} 中 {@code permitAll}，但都有服务层防线：
 * <ul>
 *   <li>{@code POST /auth/register/send-code}：手机号/邮箱格式校验 + target 冷却 + target/IP 小时上限（429）；</li>
 *   <li>{@code POST /auth/register}：手机验证码必验、邮箱填了必验、用户名/手机号/邮箱唯一（409）；
 *       成功后只落 PENDING 申请 + pending_approval 占位用户（<b>不可登录</b>），返回 201。</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "自助注册", description = "验证码发送与注册申请（需管理员审批后生效）")
public class AuthRegistrationController {

    private final VerificationCodeService verificationCodeService;
    private final RegistrationService registrationService;

    @PostMapping("/register/send-code")
    @Operation(summary = "发送注册验证码",
            description = "公开接口。channel=sms|email，target 为手机号或邮箱，purpose=REGISTER。"
                    + "同一 target 5 分钟内只能发一次（429）；dev-mode 下响应含 devCode")
    public ApiResponse<SendCodeResponse> sendCode(
            @Valid @RequestBody SendCodeRequest request,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        String clientIp = resolveClientIp(httpRequest);
        return ApiResponse.success(verificationCodeService.sendCode(request, clientIp));
    }

    @PostMapping("/register")
    @Operation(summary = "提交注册申请",
            description = "公开接口。手机号必须通过验证码；填了邮箱必须通过邮箱验证码。"
                    + "成功返回 201，但**不会立即产生可登录账号**，需管理员审批")
    public ResponseEntity<ApiResponse<RegistrationRequestVO>> register(
            @Valid @RequestBody RegisterRequest request) {
        RegistrationRequestVO vo = registrationService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(vo, "注册申请已提交，请等待管理员审批"));
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
