package com.vulnaudit.service;

import com.vulnaudit.dto.LoginThemeSettingResponse;
import com.vulnaudit.dto.PublicLoginThemeResponse;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;

import java.util.UUID;

/**
 * 系统设置服务（t109）
 *
 * <p>当前只承载「登录页缺省主题」{@code login.defaultTheme}，表结构为通用 key/value，
 * 后续加全局开关只需增常量键与读写方法。
 *
 * <p>三条硬约束：
 * <ol>
 *   <li><b>读取永不抛异常</b>：行缺失 / 值为空 / 值非法 → 回落 {@code pencil} 并记 WARN，
 *       登录页（认证前）不会因为配置脏了而挂掉；</li>
 *   <li><b>启动自愈</b>：应用就绪时若缺该行则幂等插入默认值 {@code pencil}；</li>
 *   <li><b>不污染用户偏好</b>：管理员改缺省值只写 {@code system_settings}，
 *       绝不写任何用户的 {@code users.login_theme}（t96 的「我的偏好」语义保持不变）。</li>
 * </ol>
 */
public interface SystemSettingService {

    /** 登录页缺省主题的设置键 */
    String LOGIN_DEFAULT_THEME_KEY = "login.defaultTheme";

    /** 审计动作：修改系统设置 */
    String ACTION_UPDATE_SYSTEM_SETTING = "UPDATE_SYSTEM_SETTING";

    /** 审计资源类型 */
    String RESOURCE_TYPE_SYSTEM_SETTING = "system_setting";

    /**
     * 幂等补齐缺省设置行（启动时与读取缺失时调用）
     *
     * @return 是否发生了插入
     */
    boolean ensureDefaults();

    /**
     * 当前登录页缺省主题（永不抛异常，非法/缺失回落 {@code pencil}）
     */
    @Nonnull
    String getDefaultLoginTheme();

    /**
     * 登录页认证前所需的公开配置
     */
    @Nonnull
    PublicLoginThemeResponse getPublicLoginTheme();

    /**
     * 管理员视角的设置详情
     */
    @Nonnull
    LoginThemeSettingResponse getLoginThemeSetting();

    /**
     * 管理员修改缺省主题（白名单校验 + 审计 from→to）
     *
     * @param actorId    操作者（管理员）用户ID，可空（系统调用）
     * @param rawTheme   目标主题代码
     * @throws com.vulnaudit.exception.BusinessException 非法主题 → 400（中英文案由 MessageSource 决定）
     */
    @Nonnull
    LoginThemeSettingResponse updateDefaultLoginTheme(@Nullable UUID actorId, @Nullable String rawTheme);
}
