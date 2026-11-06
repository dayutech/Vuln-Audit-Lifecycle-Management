package com.vulnaudit.service;

import jakarta.annotation.Nullable;

import java.util.UUID;

/**
 * remember-me 长效令牌（t124）
 *
 * <p>Cookie 只携带 {@code <seriesId>.<secret>}；库里存 {@code SHA-256(secret)}。
 * 刷新时**轮换**（旧行作废、series 复用）；若检测到已作废令牌被再次使用 → 判定为令牌重放，
 * 整条 series 撤销并写审计 {@code REMEMBER_ME_REPLAY}。
 */
public interface RememberMeService {

    String ACTION_REPLAY = "REMEMBER_ME_REPLAY";
    String ACTION_REVOKED = "REMEMBER_ME_REVOKED";

    boolean isEnabled();

    /** Cookie 名（owl_rm） */
    String cookieName();

    /** Cookie Max-Age（秒） */
    long cookieMaxAgeSeconds();

    boolean cookieSecure();

    /** 下发新令牌（新 series） */
    String issue(UUID userId, @Nullable String userAgent, @Nullable String ip);

    /**
     * 轮换：校验旧令牌 → 作废 → 在同 series 内签发新令牌
     *
     * @return 新令牌（{@code seriesId.secret}）
     * @throws com.vulnaudit.exception.BusinessException 401（无效/过期/已撤销/重放）
     */
    String rotate(String rawToken, @Nullable String userAgent, @Nullable String ip);

    /** 撤销单个令牌（登出） */
    void revoke(String rawToken);

    /** 撤销该用户全部令牌（改密/重置/停用/关闭 2FA） */
    void revokeAll(UUID userId, @Nullable String reason);

    /** 该用户当前有效令牌数（测试/运维用） */
    long activeCount(UUID userId);

    /**
     * 由原始令牌解析所属用户（不轮换、不校验过期）；令牌无效返回 {@code null}
     *
     * <p>供「JWT 过期后凭 cookie 刷新」在轮换前解析用户、再决定是否签发。
     */
    @Nullable
    UUID userIdOf(@Nullable String rawToken);
}
