package com.vulnaudit.service.sso;

import com.vulnaudit.entity.SsoProvider;
import org.springframework.stereotype.Component;

/**
 * 默认客户端工厂（t104）：按 provider.code 选择协议适配器。
 *
 * <p>只映射 4 家已支持平台；未知 code 返回 {@code null}，由服务层转成 400「不支持的登录方式」。
 */
@Component
public class DefaultSsoClientFactory implements SsoClientFactory {

    @Override
    public SsoClient forProvider(SsoProvider provider) {
        if (provider == null || provider.getCode() == null) {
            return null;
        }
        return switch (provider.getCode().toLowerCase()) {
            case "github" -> new GithubSsoClient(provider);
            case "microsoft" -> new MicrosoftSsoClient(provider);
            case "google" -> new GoogleSsoClient(provider);
            case "wechat" -> new WechatSsoClient(provider);
            default -> null;
        };
    }
}
