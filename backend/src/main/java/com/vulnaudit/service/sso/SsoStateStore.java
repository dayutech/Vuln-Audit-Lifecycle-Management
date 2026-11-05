package com.vulnaudit.service.sso;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SSO state 存储（t104，防 CSRF）
 *
 * <p>授权跳转前生成一次性 state（UUID），5 分钟有效；回调时必须带回同一个 state，
 * 校验通过后立即删除（一次性）。state 里附带：
 * <ul>
 *   <li>provider code（防止 A 平台 state 拿去换 B 平台）；</li>
 *   <li>{@code redirectUri}（换 token 时需要原样回传）；</li>
 *   <li>{@code bindingUserId}（已登录用户发起绑定时的用户ID；为空表示登录流程）。</li>
 * </ul>
 *
 * <p><b>实现方式</b>：进程内 Map + TTL。生产多实例部署时需换成共享存储（Redis），
 * 或把 state 签发/校验改为签名 token（无状态）——已在 README 记录。
 */
@Slf4j
@Component
public class SsoStateStore {

    /** state 有效期 */
    public static final Duration TTL = Duration.ofMinutes(5);

    /** state 记录 */
    public record StateEntry(String providerCode, String redirectUri, UUID bindingUserId, Instant expiresAt) {
        boolean expired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    private final Map<String, StateEntry> states = new ConcurrentHashMap<>();

    /**
     * 生成并保存 state
     */
    public String issue(String providerCode, String redirectUri, UUID bindingUserId) {
        purgeExpired();
        String state = UUID.randomUUID().toString().replace("-", "");
        states.put(state, new StateEntry(providerCode, redirectUri, bindingUserId, Instant.now().plus(TTL)));
        return state;
    }

    /**
     * 校验并消费 state
     *
     * @return 命中的记录；不存在/过期/被用过 → {@code null}
     */
    public StateEntry consume(String state, String providerCode) {
        if (state == null || state.isBlank()) {
            return null;
        }
        StateEntry entry = states.remove(state);
        if (entry == null) {
            return null;
        }
        if (entry.expired()) {
            log.info("SSO state 已过期: provider={}, bindingUserId={}", entry.providerCode(), entry.bindingUserId());
            return null;
        }
        if (providerCode != null && !providerCode.equals(entry.providerCode())) {
            log.warn("SSO state 与 provider 不匹配: state.provider={}, request.provider={}",
                    entry.providerCode(), providerCode);
            return null;
        }
        return entry;
    }

    /** 清理过期 state，避免长期运行时 Map 无限增长 */
    public void purgeExpired() {
        states.entrySet().removeIf(e -> e.getValue().expired());
    }

    /** 仅测试/运维观察用 */
    public int size() {
        return states.size();
    }
}
