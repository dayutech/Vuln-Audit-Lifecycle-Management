package com.vulnaudit.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * 模拟视角响应（t90）
 *
 * @param token     受限 JWT（subject=目标用户，含 act/imp claim，有效期显著缩短）
 * @param expiresAt token 过期时间
 * @param target    被模拟用户信息
 * @param actor     真实操作者（ADMIN）信息
 */
public record ImpersonateResponse(
        String token,
        Instant expiresAt,
        Target target,
        Actor actor) {

    /** 被模拟用户 */
    public record Target(UUID id, String username, List<String> roles) {
    }

    /** 真实操作者 */
    public record Actor(UUID id, String username) {
    }
}
