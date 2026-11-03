package com.vulnaudit.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 管理员模拟视角（impersonation）配置（t90）
 *
 * <pre>
 * app:
 *   security:
 *     impersonation:
 *       enabled: true        # 生产可整体关闭（关闭后 /admin/impersonate* 返回 403）
 *       ttl-seconds: 7200    # 模拟 token 有效期，默认 2 小时；显著短于常规 token
 * </pre>
 *
 * <p><b>风险提示</b>：模拟会产生「以他人身份执行」的行为，因此默认：
 * 只能由 ADMIN 发起、不得模拟 ADMIN、模拟态禁止所有用户/角色/管理员端点写操作、
 * 全部模拟行为落审计（START / STOP + 业务审计附 {@code impersonatedBy}）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.security.impersonation")
public class ImpersonationProperties {

    /** 是否启用模拟视角能力（默认开启，生产可关） */
    private boolean enabled = true;

    /** 模拟 token 有效期（秒），默认 7200 = 2 小时 */
    private long ttlSeconds = 7200L;
}
