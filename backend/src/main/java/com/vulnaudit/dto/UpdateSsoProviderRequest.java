package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

/**
 * 管理员更新 SSO 提供商配置（t104 开关 / t116 凭据 Web 配置）
 *
 * <p><b>部分更新</b>：所有字段可选，{@code null} / 省略 = 该字段保持不变。
 *
 * <p><b>secret 语义（重点）</b>：
 * <ul>
 *   <li>{@code clientSecret} 省略或传空字符串 → <b>保持不变</b>（避免「只改 URL 却把 secret 清空」这类事故）；</li>
 *   <li>要清空必须显式 {@code clearClientSecret=true}；</li>
 *   <li>{@code clearClientSecret=true} 与 {@code clientSecret} 同时给 → 以清空为准（并忽略新值）。</li>
 * </ul>
 *
 * @param enabled           是否开放该登录方式
 * @param clientId          OAuth Client ID（传空字符串表示清空）
 * @param clientSecret      OAuth Client Secret（省略/空串 = 不修改）
 * @param clearClientSecret 显式清空 secret
 * @param authorizeUrl      授权端点（必须 https；本地联调允许 http://localhost 与 http://127.0.0.1；空串清空）
 * @param tokenUrl          换 token 端点（规则同上）
 * @param userinfoUrl       用户信息端点（规则同上）
 * @param scopes            scopes，逗号分隔，自动去重（空串清空）
 */
@Schema(description = "更新 SSO 提供商配置（部分更新）")
public record UpdateSsoProviderRequest(
        @Schema(description = "是否开放该登录方式", example = "true")
        Boolean enabled,

        @Schema(description = "OAuth Client ID", example = "Ov23li...")
        @Size(max = 255, message = "validation.sso.client_id.too_long")
        String clientId,

        @Schema(description = "OAuth Client Secret（省略/空串=保持原值）", example = "8f2c...")
        @Size(max = 512, message = "validation.sso.client_secret.too_long")
        String clientSecret,

        @Schema(description = "显式清空 secret", example = "false")
        Boolean clearClientSecret,

        @Schema(description = "授权端点（https；本地可 http://localhost / http://127.0.0.1）",
                example = "https://github.com/login/oauth/authorize")
        @Size(max = 512, message = "validation.sso.url.too_long")
        String authorizeUrl,

        @Schema(description = "换 token 端点", example = "https://github.com/login/oauth/access_token")
        @Size(max = 512, message = "validation.sso.url.too_long")
        String tokenUrl,

        @Schema(description = "用户信息端点", example = "https://api.github.com/user")
        @Size(max = 512, message = "validation.sso.url.too_long")
        String userinfoUrl,

        @Schema(description = "scopes（逗号分隔，自动去重）", example = "read:user,user:email")
        @Size(max = 255, message = "validation.sso.scopes.too_long")
        String scopes) {
}
