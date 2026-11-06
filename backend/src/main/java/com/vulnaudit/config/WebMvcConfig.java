package com.vulnaudit.config;

import com.vulnaudit.security.TwoFactorEnforcementInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置：注册审计拦截器与强制双因子拦截器（t125）。
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuditTrailInterceptor auditTrailInterceptor;
    private final TwoFactorEnforcementInterceptor twoFactorEnforcementInterceptor;

    @Override
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(auditTrailInterceptor)
                .addPathPatterns("/api/v1/**");
        // t125：强制双因子的单一拦触点（Spring Security 之后执行，能拿到已认证用户）
        registry.addInterceptor(twoFactorEnforcementInterceptor)
                .addPathPatterns("/api/v1/**")
                .excludePathPatterns(
                        "/api/v1/auth/**",
                        "/api/v1/users/me/2fa/**",
                        "/api/v1/users/me/preferences",
                        "/api/v1/admin/settings/**");
    }
}
