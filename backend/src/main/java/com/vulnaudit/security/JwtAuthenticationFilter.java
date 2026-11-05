package com.vulnaudit.security;

import com.vulnaudit.config.JwtProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.lang.NonNull;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT认证过滤器
 * 
 * 功能说明：
 * - 拦截所有HTTP请求
 * - 从请求头中提取JWT令牌
 * - 验证JWT令牌有效性
 * - 设置Spring Security认证上下文
 * 
 * 继承OncePerRequestFilter确保每个请求只过滤一次
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UserDetailsService userDetailsService;
    private final JwtProperties jwtProperties;
    private final com.vulnaudit.repository.UserRepository userRepository;

    /**
     * 过滤器核心逻辑
     * 
     * @param request HTTP请求
     * @param response HTTP响应
     * @param filterChain 过滤器链
     */
    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            // 从请求头获取JWT令牌
            String jwt = resolveToken(request);
            
            // 验证令牌并设置认证信息
            if (StringUtils.hasText(jwt) && jwtUtils.validateToken(jwt)) {
                // 从令牌中获取用户名
                String username = jwtUtils.getUsernameFromToken(jwt);

                // t103：忘记密码重置后，该用户「签发时间早于 token_invalid_before」的 token 一律失效。
                // 直接返回（不设置认证上下文）→ 受保护端点由 AuthenticationEntryPoint 统一返回 401；
                // 注意：过滤器链在 finally 中统一放行，这里不能自己再调 doFilter，否则会重复执行。
                if (isInvalidatedByPasswordReset(jwt, username)) {
                    log.warn("token 已被密码重置作废，拒绝认证: username={}", username);
                    return;
                }

                // 加载用户详情
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                
                // 创建认证令牌
                UsernamePasswordAuthenticationToken authentication = 
                        new UsernamePasswordAuthenticationToken(
                                userDetails, 
                                null, 
                                userDetails.getAuthorities()
                        );
                
                // 设置认证详情
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                
                // 设置到Security上下文
                SecurityContextHolder.getContext().setAuthentication(authentication);

                // t90：模拟 token（imp=true + act）→ 记录真实操作者，供守卫过滤器与审计归属使用
                if (jwtUtils.isImpersonationToken(jwt)) {
                    String actorId = jwtUtils.getClaim(jwt, "act");
                    request.setAttribute(IMPERSONATION_ACTOR_ATTR, actorId);
                    try {
                        ImpersonationContext.set(new ImpersonationContext.Snapshot(
                                actorId == null ? null : java.util.UUID.fromString(actorId),
                                actorId,
                                username));
                    } catch (IllegalArgumentException ignored) {
                        // act 不是合法 UUID → 按「无法归属」处理，不放行任何额外能力
                        log.warn("模拟 token 的 act claim 非 UUID: {}", actorId);
                    }
                    log.info("模拟视角请求: target={}, actor={}", username, actorId);
                }
                
                log.debug("设置用户认证: {}", username);
            }
        } catch (Exception e) {
            log.error("无法设置用户认证: {}", e.getMessage());
        } finally {
            // 必须先继续过滤器链，再清理上下文（清理放在最外层 finally）
            try {
                filterChain.doFilter(request, response);
            } finally {
                ImpersonationContext.clear();
            }
        }
    }

    /** 模拟 token 的真实操作者ID（请求属性，便于日志/调试与下游读取） */
    public static final String IMPERSONATION_ACTOR_ATTR = "impersonation.actorId";

    /**
     * token 是否已被「忘记密码重置」作废（t103）
     *
     * <p>判定：{@code users.token_invalid_before} 非空且 token 的 {@code iat} 早于该时间戳。
     * iat 为秒级精度，重置写入的是当前时间（秒级），因此「与重置同一秒签发」的 token 仍可用
     * —— 这是可接受的 ≤1 秒窗口，换取「无需维护 token 黑名单」。
     *
     * @param jwt      JWT 原文
     * @param username 令牌 subject（用户名）
     * @return true 表示该 token 已失效
     */
    private boolean isInvalidatedByPasswordReset(String jwt, String username) {
        if (username == null) {
            return false;
        }
        var user = userRepository.findByUsername(username).orElse(null);
        if (user == null || user.getTokenInvalidBefore() == null) {
            return false;
        }
        java.util.Date issuedAt = jwtUtils.getIssuedAt(jwt);
        if (issuedAt == null) {
            return false;
        }
        java.time.LocalDateTime iat = java.time.LocalDateTime.ofInstant(
                issuedAt.toInstant(), java.time.ZoneId.systemDefault());
        return iat.isBefore(user.getTokenInvalidBefore());
    }

    /**
     * 从请求中解析JWT令牌
     * 
     * @param request HTTP请求
     * @return JWT令牌，如果不存在则返回null
     */
    private String resolveToken(HttpServletRequest request) {
        String bearerToken = request.getHeader(jwtProperties.getHeader());
        return jwtUtils.resolveToken(bearerToken);
    }

}
