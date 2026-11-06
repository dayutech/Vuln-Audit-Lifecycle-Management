package com.vulnaudit.security;

import com.vulnaudit.config.LoginLockoutProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败计数与锁定状态（t123，进程内实现）
 *
 * <p>键设计：
 * <ul>
 *   <li>{@code ip:<ip>}：该 IP 的失败时间戳队列（滑动窗口）</li>
 *   <li>{@code acct:<userId>}：该账户的失败时间戳队列</li>
 *   <li>{@code ipSet:<userId>}：该账户近期的来源 IP 与最近出现时间（带 TTL 清理）</li>
 * </ul>
 *
 * <p><b>多实例</b>：状态在进程内，多副本部署时每个实例各自计数（会削弱防护强度），生产多副本需替换为 Redis。
 * 所有写入都带 TTL 清理，避免长期运行内存泄漏。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LoginLockoutTracker {

    private final LoginLockoutProperties properties;

    /** ip → 失败时间戳（毫秒） */
    private final Map<String, Deque<Long>> ipFailures = new ConcurrentHashMap<>();

    /** ip → 锁定截止时间 */
    private final Map<String, Instant> ipLockedUntil = new ConcurrentHashMap<>();

    /** userId → 失败时间戳 */
    private final Map<UUID, Deque<Long>> accountFailures = new ConcurrentHashMap<>();

    /** userId → (ip → 最近出现时间) */
    private final Map<UUID, Map<String, Long>> accountIps = new ConcurrentHashMap<>();

    /** userId → 锁定截止时间 */
    private final Map<UUID, Instant> accountLockedUntil = new ConcurrentHashMap<>();

    /** userId → 锁定原因（审计与响应提示用） */
    private final Map<UUID, String> accountLockReason = new ConcurrentHashMap<>();

    // ------------------------------------------------------------------ IP 层

    /** 记录一次来自某 IP 的登录失败；返回该 IP 是否已达到锁定阈值 */
    public boolean recordIpFailure(String ip) {
        if (ip == null || ip.isBlank()) {
            return false;
        }
        long now = System.currentTimeMillis();
        long windowMillis = minutes(properties.getIpWindowMinutes());
        Deque<Long> queue = ipFailures.computeIfAbsent(ip, k -> new java.util.ArrayDeque<>());
        synchronized (queue) {
            prune(queue, now, windowMillis);
            queue.addLast(now);
            if (queue.size() >= properties.getIpMaxFailures()) {
                ipLockedUntil.put(ip, Instant.now().plusSeconds(minutes(properties.getIpLockMinutes())));
                queue.clear();
                return true;
            }
        }
        return false;
    }

    /** 该 IP 的锁定剩余秒数（未锁定返回 0） */
    public long ipLockRemainingSeconds(String ip) {
        if (ip == null || ip.isBlank()) {
            return 0;
        }
        Instant until = ipLockedUntil.get(ip);
        if (until == null) {
            return 0;
        }
        long remaining = until.getEpochSecond() - Instant.now().getEpochSecond();
        if (remaining <= 0) {
            ipLockedUntil.remove(ip);
            return 0;
        }
        return remaining;
    }

    /** 该 IP 当前是否处于锁定期（用于判定「锁 IP 期间又从别的 IP 尝试」） */
    public boolean isIpCurrentlyLocked(String ip) {
        return ipLockRemainingSeconds(ip) > 0;
    }

    // ------------------------------------------------------------------ 账户层

    /**
     * 记录一次账户维度的失败，并判断是否需要升级为账户锁定
     *
     * @param userId 已知账号（用户名不存在时为 null —— 此时只做 IP 计数，避免账号枚举）
     * @param ip     来源 IP
     * @return 触发升级时返回锁定原因；否则返回 null
     */
    public String recordAccountFailure(UUID userId, String ip) {
        if (userId == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        long windowMillis = minutes(properties.getDistinctIpWindowMinutes());

        Deque<Long> queue = accountFailures.computeIfAbsent(userId, k -> new java.util.ArrayDeque<>());
        int failures;
        synchronized (queue) {
            prune(queue, now, windowMillis);
            queue.addLast(now);
            failures = queue.size();
        }

        Map<String, Long> ips = accountIps.computeIfAbsent(userId, k -> new LinkedHashMap<>());
        synchronized (ips) {
            ips.entrySet().removeIf(e -> now - e.getValue() > windowMillis);
            if (ip != null && !ip.isBlank()) {
                ips.put(ip, now);
            }
        }

        Set<String> distinctIps = new LinkedHashSet<>(ips.keySet());
        boolean distinctIpEscalation = distinctIps.size() >= properties.getDistinctIpThreshold();

        // 条件 a：该账户在「某个 IP 被锁」期间，从**不同 IP** 又失败了一次 → 目标化破解
        boolean crossIpEscalation = false;
        if (distinctIps.size() >= 2) {
            for (String knownIp : distinctIps) {
                if (!knownIp.equals(ip) && isIpCurrentlyLocked(knownIp)) {
                    crossIpEscalation = true;
                    break;
                }
            }
        }

        if (!crossIpEscalation && !distinctIpEscalation) {
            return null;
        }
        String reason = crossIpEscalation
                ? "跨 IP 尝试（被锁 IP 期间从另一 IP 继续失败）"
                : "窗口期内来自 " + distinctIps.size() + " 个不同 IP 的连续失败";
        return reason + "；failures=" + failures + "，ips=" + String.join(",", distinctIps);
    }

    /** 锁定账户 */
    public void lockAccount(UUID userId, String reason) {
        if (userId == null) {
            return;
        }
        accountLockedUntil.put(userId, Instant.now().plusSeconds(minutes(properties.getAccountLockMinutes())));
        accountLockReason.put(userId, reason);
        log.warn("账户已锁定: userId={}, reason={}", userId, reason);
    }

    /** 账户锁定剩余秒数（未锁定返回 0） */
    public long accountLockRemainingSeconds(UUID userId) {
        if (userId == null) {
            return 0;
        }
        Instant until = accountLockedUntil.get(userId);
        if (until == null) {
            return 0;
        }
        long remaining = until.getEpochSecond() - Instant.now().getEpochSecond();
        if (remaining <= 0) {
            accountLockedUntil.remove(userId);
            accountLockReason.remove(userId);
            return 0;
        }
        return remaining;
    }

    public boolean isAccountLocked(UUID userId) {
        return accountLockRemainingSeconds(userId) > 0;
    }

    public String accountLockReasonOf(UUID userId) {
        return accountLockReason.get(userId);
    }

    /** 登录成功 / 解锁：清零该账户的失败计数、来源 IP 与锁定状态 */
    public void clearAccount(UUID userId) {
        if (userId == null) {
            return;
        }
        accountFailures.remove(userId);
        accountIps.remove(userId);
        accountLockedUntil.remove(userId);
        accountLockReason.remove(userId);
    }

    /** 定期清理过期条目（每次访问时按需清理，这里是显式的全量清理入口） */
    public void cleanup() {
        long now = System.currentTimeMillis();
        long ipWindow = minutes(properties.getIpWindowMinutes());
        ipFailures.entrySet().removeIf(e -> {
            Deque<Long> q = e.getValue();
            synchronized (q) {
                prune(q, now, ipWindow);
                return q.isEmpty();
            }
        });
        long distinctWindow = minutes(properties.getDistinctIpWindowMinutes());
        accountIps.entrySet().removeIf(e -> {
            Map<String, Long> ips = e.getValue();
            synchronized (ips) {
                ips.entrySet().removeIf(x -> now - x.getValue() > distinctWindow);
                return ips.isEmpty();
            }
        });
        ipLockedUntil.entrySet().removeIf(e -> e.getValue().isBefore(Instant.now()));
        accountLockedUntil.entrySet().removeIf(e -> e.getValue().isBefore(Instant.now()));
    }

    /** 仅供测试：清空全部状态 */
    public void reset() {
        ipFailures.clear();
        ipLockedUntil.clear();
        accountFailures.clear();
        accountIps.clear();
        accountLockedUntil.clear();
        accountLockReason.clear();
    }

    /** 该账户近期来源 IP（审计/告警用） */
    public List<String> recentIps(UUID userId) {
        Map<String, Long> ips = accountIps.get(userId);
        if (ips == null) {
            return List.of();
        }
        synchronized (ips) {
            return new ArrayList<>(ips.keySet());
        }
    }

    private void prune(Deque<Long> queue, long now, long windowMillis) {
        while (!queue.isEmpty() && now - queue.peekFirst() > windowMillis) {
            queue.pollFirst();
        }
    }

    private long minutes(int value) {
        return Math.max(value, 0) * 60_000L;
    }
}
