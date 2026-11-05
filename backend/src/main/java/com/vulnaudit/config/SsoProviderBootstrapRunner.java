package com.vulnaudit.config;

import com.vulnaudit.entity.SsoProvider;
import com.vulnaudit.repository.SsoProviderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.ApplicationArguments;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * SSO 提供商初始化（t104）
 *
 * <p>表为空时插入 4 条默认记录（github / microsoft / google / wechat），全部 {@code enabled=false}：
 * 登录页不会展示任何按钮，直到管理员显式开启**并且**运维在表里填好该平台的 OAuth 凭据。
 *
 * <p>已存在记录时不改动任何字段（避免覆盖管理员/运维的配置）。
 */
@Slf4j
@Order(20)
@Component
@RequiredArgsConstructor
public class SsoProviderBootstrapRunner implements ApplicationRunner {

    private final SsoProviderRepository ssoProviderRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (ssoProviderRepository.count() > 0) {
            long usable = ssoProviderRepository.findAll().stream().filter(SsoProvider::isUsable).count();
            log.info("SSO 提供商已存在，跳过初始化: 总数={}, 可用（开启且已配置）={}",
                    ssoProviderRepository.count(), usable);
            return;
        }
        List<SsoProvider> defaults = List.of(
                provider("github", "GitHub", "github",
                        "https://github.com/login/oauth/authorize",
                        "https://github.com/login/oauth/access_token",
                        "https://api.github.com/user", "read:user user:email"),
                provider("microsoft", "Microsoft", "microsoft",
                        "https://login.microsoftonline.com/common/oauth2/v2.0/authorize",
                        "https://login.microsoftonline.com/common/oauth2/v2.0/token",
                        "https://graph.microsoft.com/v1.0/me", "openid profile email User.Read"),
                provider("google", "Google", "google",
                        "https://accounts.google.com/o/oauth2/v2/auth",
                        "https://oauth2.googleapis.com/token",
                        "https://openidconnect.googleapis.com/v1/userinfo", "openid profile email"),
                provider("wechat", "WeChat", "wechat",
                        "https://open.weixin.qq.com/connect/qrconnect",
                        "https://api.weixin.qq.com/sns/oauth2/access_token",
                        "https://api.weixin.qq.com/sns/userinfo", "snsapi_login"));
        ssoProviderRepository.saveAll(defaults);
        log.info("SSO 提供商初始化完成: 写入 {} 条默认记录（enabled=false，需管理员开启并填写 OAuth 凭据）",
                defaults.size());
    }

    private SsoProvider provider(String code, String name, String icon,
                                 String authorizeUrl, String tokenUrl, String userInfoUrl, String scopes) {
        return SsoProvider.builder()
                .code(code)
                .name(name)
                .icon(icon)
                .enabled(false)
                .authorizeUrl(authorizeUrl)
                .tokenUrl(tokenUrl)
                .userInfoUrl(userInfoUrl)
                .scopes(scopes)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
