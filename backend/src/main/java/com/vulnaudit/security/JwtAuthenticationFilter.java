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
