package com.vulnaudit.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败限流。
 *
 * <p>此前登录接口没有任何失败次数约束，弱口令 + 内网暴露时存在暴力破解风险。
 * 这里用进程内计数实现最简可用版本：同一「用户名 + 客户端 IP」在窗口期内
 * 失败达到阈值即锁定一段时间。
 *
 * <p>说明：单机部署够用；多实例部署时应替换为 Redis 计数（此处不引入额外依赖）。
 */
@Slf4j
@Service
public class LoginAttemptService {

    /** 允许的连续失败次数 */
    @Value("${app.security.login.max-attempts:5}")
    private int maxAttempts;

    /** 锁定时长（秒） */
    @Value("${app.security.login.lock-seconds:300}")
    private long lockSeconds;

    /** key = username@ip */
    private final Map<String, Attempt> attempts = new ConcurrentHashMap<>();

    private static final class Attempt {
        private int count;
        private long lastFailureMillis;

        private Attempt(int count, long lastFailureMillis) {
            this.count = count;
            this.lastFailureMillis = lastFailureMillis;
        }
    }

    public static String key(String username, String clientIp) {
        return (username == null ? "" : username.trim().toLowerCase()) + "@" + (clientIp == null ? "-" : clientIp);
    }

    /** 是否处于锁定期 */
    public boolean isLocked(String key) {
        Attempt attempt = attempts.get(key);
        if (attempt == null) {
            return false;
        }
        if (attempt.count < maxAttempts) {
            return false;
        }
        long elapsed = (System.currentTimeMillis() - attempt.lastFailureMillis) / 1000;
        if (elapsed >= lockSeconds) {
            attempts.remove(key);
            return false;
        }
        return true;
    }

    /** 距离解锁剩余秒数（未锁定返回 0） */
    public long retryAfterSeconds(String key) {
        Attempt attempt = attempts.get(key);
        if (attempt == null || attempt.count < maxAttempts) {
            return 0;
        }
        long elapsed = (System.currentTimeMillis() - attempt.lastFailureMillis) / 1000;
        return Math.max(lockSeconds - elapsed, 0);
    }

    /** 记录一次失败 */
    public void onFailure(String key) {
        attempts.compute(key, (k, old) -> {
            long now = System.currentTimeMillis();
            if (old == null) {
                return new Attempt(1, now);
            }
            // 超出窗口期重新计数
            if ((now - old.lastFailureMillis) / 1000 >= lockSeconds) {
                return new Attempt(1, now);
            }
            return new Attempt(old.count + 1, now);
        });
        Attempt attempt = attempts.get(key);
        if (attempt != null && attempt.count >= maxAttempts) {
            log.warn("登录失败次数达到阈值，账号临时锁定: key={}, attempts={}", key, attempt.count);
        }
    }

    /** 登录成功后清零 */
    public void onSuccess(String key) {
        attempts.remove(key);
    }

    /** 仅供测试/运维使用：重置全部计数 */
    public void reset() {
        attempts.clear();
    }
}
