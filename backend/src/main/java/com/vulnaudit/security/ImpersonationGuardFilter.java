package com.vulnaudit.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * 模拟态权限收敛过滤器（t90）
 *
 * <p><b>策略</b>：模拟态保留目标用户本身的业务权限（目标是团队长就能做团队长能做的事），
 * 但**身份与权限管理类端点一律禁止**，防止「借别人的身份改权限」：
 *
 * <pre>
 * 模拟态禁止（写方法 POST/PUT/PATCH/DELETE）：
 *   /api/v1/users/**      用户增删改（含改密码、改角色）
 *   /api/v1/roles/**      角色与权限点增删改
 *   /api/v1/admin/**      管理员端点（含再次发起模拟 → 防链式模拟）
 *
 * 显式放行（模拟态必须可用，否则无法"停止模拟"）：
 *   POST /api/v1/admin/impersonate/stop
 * </pre>
 *
 * <p>只拦截写方法：模拟态读取 {@code /users}、{@code /roles} 等列表是审阅权限分配的正当需求。
 */
@Slf4j
@Component
public class ImpersonationGuardFilter extends OncePerRequestFilter {

    /** 模拟态禁止的路径前缀（写方法） */
    static final List<String> BLOCKED_PREFIXES = List.of(
            "/api/v1/users",
            "/api/v1/roles",
            "/api/v1/admin"
    );

    /** 模拟态显式放行的端点（停止模拟） */
    static final List<String> ALLOWED_PATHS = List.of(
            "/api/v1/admin/impersonate/stop"
    );

    private static final List<String> WRITE_METHODS = List.of("POST", "PUT", "PATCH", "DELETE");

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        if (ImpersonationContext.isActive() && isBlocked(request)) {
            log.warn("模拟态禁止的写操作被拦截: {} {}", request.getMethod(), request.getRequestURI());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(
                    "{\"success\":false,\"code\":403,\"message\":\"模拟视角下禁止修改用户、角色或管理员配置\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean isBlocked(HttpServletRequest request) {
        String method = request.getMethod();
        if (!WRITE_METHODS.contains(method)) {
            return false;
        }
        String uri = request.getRequestURI();
        if (ALLOWED_PATHS.stream().anyMatch(uri::startsWith)) {
            return false;
        }
        return BLOCKED_PREFIXES.stream().anyMatch(uri::startsWith);
    }
}
