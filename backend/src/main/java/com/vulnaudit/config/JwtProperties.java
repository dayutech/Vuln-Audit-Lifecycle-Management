package com.vulnaudit.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT配置属性类
 * 
 * 功能说明：
 * - 从application.yml读取JWT相关配置
 * - 包含密钥、过期时间等配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * JWT签名密钥
     * 建议：生产环境使用256位以上的随机字符串
     */
    private String secret;

    /**
     * JWT过期时间（毫秒）
     * 默认：24小时 = 86400000毫秒
     */
    private Long expiration = 86400000L;

    /**
     * JWT请求头名称
     */
    private String header = "Authorization";

    /**
     * JWT令牌前缀
     */
    private String prefix = "Bearer ";

}
