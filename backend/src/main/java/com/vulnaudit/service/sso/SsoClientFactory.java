package com.vulnaudit.service.sso;

import com.vulnaudit.entity.SsoProvider;

/**
 * 按提供商配置产出 {@link SsoClient}（t104）
 *
 * <p>抽象成工厂而不是让业务层 switch：测试用 {@code FakeSsoClientFactory}（@Primary）
 * 即可整链替换，避免真实外网调用；生产实现只按 code 选择协议适配器。
 */
public interface SsoClientFactory {

    /**
     * 取该提供商的客户端
     *
     * @param provider 提供商配置（含 client_id/secret 与三个端点）
     * @return 客户端；未支持的 code 返回 {@code null}
     */
    SsoClient forProvider(SsoProvider provider);
}
