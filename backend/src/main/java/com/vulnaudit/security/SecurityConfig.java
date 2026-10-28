package com.vulnaudit.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
/**
 * Spring Security安全配置类
 * 
 * 功能说明：
 * - 配置安全过滤器链
 * - 配置JWT认证过滤器
 * - 配置密码加密器
 * - 配置认证提供者
 * - 配置CORS跨域
 * - 配置无状态会话
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final UserDetailsService userDetailsService;

    /** API 文档是否匿名可访问（默认关闭，避免接口面暴露给未登录者） */
    @Value("${app.security.public-api-docs:false}")
    private boolean publicApiDocs;

    /** 允许跨域的来源白名单，逗号分隔；为空表示不启用跨域（同源部署） */
    @Value("${app.cors.allowed-origins:}")
    private String allowedOrigins;

    /**
     * 配置安全过滤器链
     * 
     * @param http HttpSecurity对象
     * @return 安全过滤器链
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 禁用CSRF（使用JWT不需要CSRF保护）
                .csrf(AbstractHttpConfigurer::disable)
                
                // 配置CORS
                // 未配置来源白名单时（同源部署 / 反向代理场景）不安装 CORS 过滤器。
                // 否则 Spring 的 CorsFilter 会把任何携带 Origin 的请求当作跨域并直接拒绝，
                // 表现为 “Invalid CORS request” 403 —— 反向代理下 Host 丢失端口时尤为常见。
                .cors(cors -> {
                    if (StringUtils.hasText(allowedOrigins)) {
                        cors.configurationSource(corsConfigurationSource());
                    } else {
                        cors.disable();
                    }
                })
                
                // 配置请求授权
                .authorizeHttpRequests(auth -> {
                    // 登录相关端点始终公开
                    auth.requestMatchers(
                            "/api/v1/auth/login",
                            "/api/v1/auth/logout",
                            "/api/v1/auth/refresh",
                            "/api/v1/auth/register",
                            "/actuator/health"
                    ).permitAll();

                    // API 文档默认需要认证，仅在显式开启时公开（见 app.security.public-api-docs）
                    if (publicApiDocs) {
                        auth.requestMatchers(
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/v3/api-docs/**"
                        ).permitAll();
                    }

                    // 其他所有请求需要认证
                    auth.anyRequest().authenticated();
                })
                
                // 配置会话管理：无状态会话
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )
                
                // 配置认证提供者
                .authenticationProvider(authenticationProvider())
                
                // 添加JWT过滤器
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }

    /**
     * 配置认证提供者
     * 使用DAO认证提供者，从数据库加载用户信息
     * 
     * @return 认证提供者
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    /**
     * 配置认证管理器
     * 
     * @param config 认证配置
     * @return 认证管理器
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * 配置密码加密器
     * 使用BCrypt加密算法
     * 
     * @return 密码加密器
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 配置CORS
     *
     * <p>仅对显式配置的来源开放（{@code app.cors.allowed-origins}）。
     * 默认生产形态是「前端与 API 同源，经 nginx 反向代理」，无需跨域；
     * 因此默认为空，不再无条件放开 {@code *} + credentials。
     *
     * @return CORS配置源
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        List<String> origins = StringUtils.hasText(allowedOrigins)
                ? Arrays.stream(allowedOrigins.split(","))
                        .map(String::trim)
                        .filter(StringUtils::hasText)
                        .toList()
                : List.of();

        // 允许的来源：白名单为空时 registerCorsConfiguration 仍注册，但不会放行任何跨域来源
        configuration.setAllowedOriginPatterns(origins);

        // 允许的方法
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));

        // 允许的头
        configuration.setAllowedHeaders(List.of("*"));

        // 允许携带凭证：仅在显式配置来源时开启
        configuration.setAllowCredentials(!origins.isEmpty());

        // 暴露的响应头
        configuration.setExposedHeaders(Arrays.asList("Authorization", "Content-Disposition"));

        // 预检请求缓存时间（秒）
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        return source;
    }

}
