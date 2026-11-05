package com.vulnaudit.service;

import com.vulnaudit.dto.SsoAuthorizeResponse;
import com.vulnaudit.dto.SsoBindingVO;
import com.vulnaudit.dto.SsoCallbackResponse;
import com.vulnaudit.dto.SsoProviderOption;
import com.vulnaudit.entity.SsoProvider;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 第三方 SSO 登录与绑定（t104）
 *
 * <p>核心语义：
 * <ol>
 *   <li>只开放「管理员开启 **且** 已配置凭据」的提供商；未配置的 authorize-url 返回 503；</li>
 *   <li>已绑定且账号 active → 直接签发本地 JWT；</li>
 *   <li>陌生身份 → 建「注册申请（source=SSO）+ 待审批绑定」，返回 202 且**不发 token**；</li>
 *   <li>绑定账号非 active → 403（沿用审批拦截语义）；</li>
 *   <li>已登录用户可发起绑定 / 解绑（解绑需当前密码确认）。</li>
 * </ol>
 */
public interface SsoService {

    /** 登录页可见的提供商（enabled && configured） */
    @Nonnull
    List<SsoProviderOption> publicProviders();

    /** 全部提供商（管理端） */
    @Nonnull
    List<SsoProvider> allProviders();

    /** 登录流程：生成第三方授权地址 */
    @Nonnull
    SsoAuthorizeResponse authorizeUrl(@Nullable String providerCode, @Nullable String redirectUri);

    /** 回调：登录 / 待审批 / 绑定完成 三态 */
    @Nonnull
    SsoCallbackResponse callback(@Nullable String providerCode, @Nullable String code,
                                 @Nullable String state, @Nullable String clientIp);

    /** 当前用户的绑定列表 */
    @Nonnull
    List<SsoBindingVO> bindings(@Nullable UUID userId);

    /** 已登录用户发起绑定：返回带「绑定态」的授权地址 */
    @Nonnull
    SsoAuthorizeResponse startBind(@Nullable UUID userId, @Nullable String providerCode, @Nullable String redirectUri);

    /** 解绑（需当前密码确认） */
    void unbind(@Nullable UUID userId, @Nullable String providerCode, @Nullable String password);

    /** 管理员开关 */
    @Nonnull
    SsoProvider toggle(@Nullable String providerCode, boolean enabled, @Nullable UUID adminId);
}
