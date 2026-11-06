package com.vulnaudit.security;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.vulnaudit.service.TwoFactorService;
import com.vulnaudit.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * 强制双因子拦截器（t125）
 *
 * <p>管理员开启 {@code security.forceTwoFactor} 后，未启用 2FA 的用户除白名单外一律
 * **403 {@code TWO_FACTOR_SETUP_REQUIRED}**。用**单一拦截点**实现，避免逐个控制器改：
 * 拦截器在 Spring Security 之后执行，因此能拿到已认证的 SecurityContext。
 *
 * <p>白名单：认证/2FA 设置/登出/健康检查/公开端点（交给 SecurityConfig 判定匿名访问的请求直接放行）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TwoFactorEnforcementInterceptor implements HandlerInterceptor {

    /** 错误码：前端据此跳「设置双因子」 */
    public static final String ERROR_CODE = "TWO_FACTOR_SETUP_REQUIRED";

    private static final List<String> ALLOW_PREFIXES = List.of(
            "/api/v1/auth/",
            "/api/v1/users/me/2fa",
            "/api/v1/users/me/preferences",
            "/actuator/",
            "/v3/api-docs",
            "/swagger-ui"
    );

    private final TwoFactorService twoFactorService;
    private final UserService userService;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        if (!twoFactorService.isAvailable() || !twoFactorService.isForceTwoFactor()) {
            return true;
        }
        String path = request.getRequestURI();
        if (ALLOW_PREFIXES.stream().anyMatch(path::startsWith)) {
            return true;
        }
        String username = currentUserProvider.currentUsername().orElse(null);
        if (username == null) {
            // 未认证请求交给后续的认证失败处理（401），这里不拦截
            return true;
        }
        var user = userService.findByUsername(username).orElse(null);
        if (user == null || user.isTwoFactorEnabled()) {
            return true;
        }

        log.info("强制开启双因子，拒绝未设置 2FA 用户访问业务接口: username={}, path={}", username, path);
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        java.util.Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("success", false);
        body.put("code", ERROR_CODE);
        body.put("message", "管理员已要求开启双因子认证，请先完成 2FA 设置");
        response.getWriter().write(objectMapper.writeValueAsString(body));
        return false;
    }
}
