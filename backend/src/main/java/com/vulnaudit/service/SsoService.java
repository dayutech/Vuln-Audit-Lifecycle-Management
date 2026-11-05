package com.vulnaudit.service;

import com.vulnaudit.dto.SsoAuthorizeResponse;
import com.vulnaudit.dto.SsoBindingVO;
import com.vulnaudit.dto.SsoCallbackResponse;
import com.vulnaudit.dto.SsoProviderConfigView;
import com.vulnaudit.dto.SsoProviderOption;
import com.vulnaudit.dto.UpdateSsoProviderRequest;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.List;
import java.util.UUID;

/**
 * 第三方 SSO 登录、绑定与**提供商 Web 配置**（t104 / t116）
 *
 * <p>核心语义：
 * <ol>
 *   <li><b>展示看 enabled</b>：{@link #publicProviders()} 返回全部 {@code enabled=true} 的提供商，
 *       每项带 {@code configured} 标记（5 个必填项是否齐全），不再按 configured 过滤；</li>
 *   <li><b>可用看 configured</b>：未配置的 authorize-url 返回 503；</li>
 *   <li>已绑定且账号 active → 直接签发本地 JWT；</li>
 *   <li>陌生身份 → 建「注册申请（source=SSO）+ 待审批绑定」，返回 202 且**不发 token**；</li>
 *   <li>绑定账号非 active → 403；已登录用户可发起绑定 / 解绑（解绑需当前密码确认）；</li>
 *   <li><b>凭据 Web 配置（t116）</b>：管理员可读配置（secret 掩码）、部分更新（secret 省略/空串=不变，
 *       {@code clearClientSecret=true} 才清），保存即生效，写审计 {@code UPDATE_SSO_PROVIDER}。</li>
 * </ol>
 */
public interface SsoService {

    /** 登录页可见的提供商（enabled=true 全部返回；带 configured 标记） */
    @Nonnull
    List<SsoProviderOption> publicProviders();

    /** 管理端：全部提供商配置（secret 只回掩码） */
    @Nonnull
    List<SsoProviderConfigView> providerConfigs();

    /** 管理端：部分更新配置（含开关），返回更新后的视图 */
    @Nonnull
    SsoProviderConfigView updateProvider(@Nullable String providerCode,
                                        @Nonnull UpdateSsoProviderRequest request,
                                        @Nullable UUID adminId);

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
}
