package com.vulnaudit.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 登录页可见的 SSO 提供商（t104 / t116）
 *
 * <p><b>t116 语义变更</b>：只要管理员 {@code enabled=true} 就返回，**不再按 configured 过滤**。
 * 未配置的按钮也展示（{@code configured=false}），由前端提示「未配置」——因为「开启即展示」才是管理员预期，
 * 真正点进去调 authorize-url 时才会 503（该方式尚未完成配置）。
 *
 * @param code       提供商代码（github/microsoft/google/wechat）
 * @param name       展示名
 * @param icon       图标标识
 * @param configured 是否已填齐 5 个必填项（client_id/client_secret/authorize_url/token_url/userinfo_url）；
 *                   为 false 时前端应标注「未配置」，不要禁用按钮（点击后可提示去管理端配置）
 */
@Schema(description = "SSO 提供商选项")
public record SsoProviderOption(String code, String name, String icon, boolean configured) {
}
