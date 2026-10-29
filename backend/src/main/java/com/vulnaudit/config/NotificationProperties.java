package com.vulnaudit.config;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 通知通道配置属性
 *
 * <p>功能说明：
 * <ul>
 *     <li>映射 application.yml 中 {@code app.notify.*} 配置</li>
 *     <li>邮件（SMTP）与 Webhook 通道默认均关闭，未配置时系统退化为仅站内通知</li>
 *     <li>所有参数均可通过环境变量覆盖，便于容器化部署</li>
 * </ul>
 */
@Slf4j
@Data
@Component
@ConfigurationProperties(prefix = "app.notify")
public class NotificationProperties {

    /**
     * 邮件通道配置
     */
    private Mail mail = new Mail();

    /**
     * Webhook 通道配置
     */
    private Webhook webhook = new Webhook();

    /**
     * SMTP 邮件配置
     */
    @Data
    public static class Mail {

        /**
         * 是否启用邮件通道（默认关闭）
         */
        private boolean enabled = false;

        /**
         * SMTP 服务器地址
         */
        private String host;

        /**
         * SMTP 端口
         */
        private int port = 25;

        /**
         * SMTP 用户名，为空表示匿名投递
         */
        private String username;

        /**
         * SMTP 密码
         */
        private String password;

        /**
         * 发件人地址
         */
        private String from;

        /**
         * 发件人显示名称
         */
        private String fromName = "漏洞审计平台";

        /**
         * 是否使用 SSL（通常为 465 端口）
         */
        private boolean ssl = false;

        /**
         * 是否使用 STARTTLS（通常为 587 端口）
         */
        private boolean starttls = false;

        /**
         * 连接/读写超时（毫秒）
         */
        private long timeoutMs = 5000;
    }

    /**
     * Webhook 推送配置
     */
    @Data
    public static class Webhook {

        /**
         * 是否启用 Webhook 通道（默认关闭）
         */
        private boolean enabled = false;

        /**
         * 目标地址（HTTP/HTTPS）
         */
        private String url;

        /**
         * 签名密钥，为空时不附加签名头
         */
        private String secret;

        /**
         * 单次请求超时（毫秒）
         */
        private long timeoutMs = 3000;

        /**
         * 最大投递次数（含首次投递），默认 3 次
         */
        private int maxRetries = 3;

        /**
         * 重试退避基数（毫秒），第 n 次重试等待 n * retryBackoffMs
         */
        private long retryBackoffMs = 200;
    }
}
