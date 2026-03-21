package com.vulnaudit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * 漏洞审计生命周期管理系统主启动类
 * 
 * 功能说明：
 * - 启动Spring Boot应用
 * - 启用JPA审计功能，自动填充创建时间和修改时间
 * - 扫描所有组件并自动配置
 */
@SpringBootApplication
@EnableJpaAuditing
public class VulnAuditApplication {

    public static void main(String[] args) {
        SpringApplication.run(VulnAuditApplication.class, args);
    }

}
