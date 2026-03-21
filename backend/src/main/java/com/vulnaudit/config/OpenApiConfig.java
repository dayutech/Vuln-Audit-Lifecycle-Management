package com.vulnaudit.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 配置类
 * 
 * 功能说明：
 * - 配置 Swagger UI 和 OpenAPI 3.0 文档
 * - 配置 JWT Bearer Token 认证
 * - 配置 API 基本信息
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    /**
     * 配置 OpenAPI 基本信息
     * 
     * @return OpenAPI 配置对象
     */
    @Bean
    public OpenAPI customOpenAPI() {
        // JWT Bearer Token 认证方案
        final String securitySchemeName = "bearerAuth";
        
        return new OpenAPI()
                // API 基本信息
                .info(new Info()
                        .title("漏洞审计生命周期管理系统 API")
                        .description("""
                                ## 系统简介
                                
                                漏洞审计生命周期管理系统是一个专业的漏洞挖掘和管理平台，支持：
                                
                                - **用户管理**：用户注册、登录、权限管理
                                - **资产管理**：目标资产的管理、认领和访问控制
                                - **漏洞管理**：漏洞的全生命周期管理，包括发现、验证、分析、利用开发、交付等
                                - **文档管理**：漏洞相关文档的版本管理
                                - **环境管理**：测试环境的创建和快照管理
                                - **工具资源库**：渗透测试工具的管理
                                - **知识库**：漏洞挖掘知识的积累和分享
                                - **消息通知**：系统通知和消息推送
                                
                                ## 认证方式
                                
                                系统使用 JWT Token 进行认证，请在登录接口获取 Token 后，
                                点击右上角 "Authorize" 按钮输入 Token 进行认证。
                                
                                Token 格式：`Bearer <your_token>`
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("技术支持")
                                .email("support@vulnaudit.com"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                // 服务器配置
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("开发服务器"),
                        new Server()
                                .url("https://api.vulnaudit.com")
                                .description("生产服务器")))
                // 安全认证配置
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("JWT Token 认证，格式：Bearer <token>")));
    }

}
