package com.vulnaudit.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * SSO 回调结果（t104）
 *
 * <p>两种形态：
 * <ul>
 *   <li>已绑定且账号可用 → 200，{@code login} 为本地 JWT 登录响应；</li>
 *   <li>陌生身份 → **202**，{@code status=PENDING_APPROVAL}，**不签发 token**，等管理员审批。</li>
 * </ul>
 *
 * @param status  LOGIN（已签发 token）/ PENDING_APPROVAL（已提交审批）
 * @param login   登录响应（仅 status=LOGIN 时非空）
 * @param provider 提供商代码
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "SSO 回调结果")
public record SsoCallbackResponse(String status, LoginResponse login, String provider) {
}
