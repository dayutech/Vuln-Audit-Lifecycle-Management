package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * 管理端看到的 SSO 提供商配置（t116）
 *
 * <p><b>安全口径</b>：{@code clientSecret} 只以掩码形式返回（{@code gh_****abcd}），
 * 接口、日志、审计任何一处都不出现明文；需要更换时整体覆盖，传空字符串或省略表示保持不变。
 *
 * @param code               提供商代码
 * @param name               展示名
 * @param icon               图标标识
 * @param enabled            是否开放该登录方式
 * @param configured         5 个必填项是否齐全（scopes 可选）
 * @param clientId           OAuth Client ID（可明文返回，本来就是公开值）
 * @param clientSecretMasked secret 掩码（未配置为 null）
 * @param authorizeUrl       授权端点
 * @param tokenUrl           换 token 端点
 * @param userinfoUrl        用户信息端点
 * @param scopes             scopes（逗号分隔）
 * @param updatedAt          最后修改时间
 * @param updatedBy          最后修改人用户名（无记录为 null）
 */
@Schema(description = "SSO 提供商配置（管理端，secret 已掩码）")
public record SsoProviderConfigView(
        String code,
        String name,
        String icon,
        boolean enabled,
        boolean configured,
        String clientId,
        String clientSecretMasked,
        String authorizeUrl,
        String tokenUrl,
        String userinfoUrl,
        String scopes,
        LocalDateTime updatedAt,
        String updatedBy) {
}
