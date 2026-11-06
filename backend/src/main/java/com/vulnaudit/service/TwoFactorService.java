package com.vulnaudit.service;

import com.vulnaudit.dto.TwoFactorInitResponse;
import com.vulnaudit.dto.TwoFactorStatusResponse;
import com.vulnaudit.entity.User;
import jakarta.annotation.Nullable;

import java.util.UUID;

/**
 * 双因子认证（TOTP）服务（t124）
 *
 * <p>安全要点：
 * <ol>
 *   <li>密钥 160bit 随机 + Base32，<b>AES-GCM 加密后</b>才落库；未配置主密钥 → 功能整体禁用（不落明文）；</li>
 *   <li>init 只写「待确认」密钥，不开启；confirm 校验通过后才 {@code two_factor_enabled=true}；</li>
 *   <li>恢复码 10 个、仅返回一次、BCrypt 哈希存储、用后作废；</li>
 *   <li>关闭需要「密码 + TOTP/恢复码」双确认；管理员强制时不允许关闭（403）；</li>
 *   <li>登录两步：密码通过后发短期一次性 {@code challengeToken}，第二步校验 TOTP/恢复码才签发 JWT。</li>
 * </ol>
 */
public interface TwoFactorService {

    /** 系统设置键：管理员强制全体开启双因子 */
    String FORCE_TWO_FACTOR_KEY = "security.forceTwoFactor";

    /** 审计动作 */
    String ACTION_ENABLED = "TWO_FACTOR_ENABLED";
    String ACTION_DISABLED = "TWO_FACTOR_DISABLED";
    String ACTION_FORCE_SETTING = "UPDATE_FORCE_TWO_FACTOR";

    /** 2FA 功能是否可用（主密钥是否已配置） */
    boolean isAvailable();

    /** 校验密钥复杂度（长度 32 / A-Z2-7 / 非常量序列） */
    boolean isValidSecret(String secretBase32);

    /** 初始化：生成新密钥（加密落库），返回 Base32 与 otpauth URI；不开启 */
    TwoFactorInitResponse init(UUID userId);

    /**
     * 确认开启：校验 TOTP（±1 窗口）→ 置 enabled、写 confirmedAt、生成 10 个恢复码
     *
     * @return 恢复码明文（仅此一次返回）
     */
    java.util.List<String> confirm(UUID userId, String code);

    /** 关闭：需密码 + 当前 TOTP/恢复码；管理员强制时 403 */
    void disable(UUID userId, String password, String code);

    /** 状态（不返回 secret） */
    TwoFactorStatusResponse status(UUID userId);

    /**
     * 登录第二步校验：TOTP 优先，失败再试恢复码（恢复码用后作废）
     *
     * @return 是否通过
     */
    boolean verifyLoginCode(UUID userId, String code);

    /** 是否为「管理员强制开启且该用户尚未开启」 */
    boolean mustSetupTwoFactor(@Nullable User user);

    /** 管理员强制开关状态 */
    boolean isForceTwoFactor();

    /** 管理员设置强制开关（写审计），返回最新状态 */
    boolean setForceTwoFactor(boolean enabled, @Nullable UUID actorId);

    /** 登记一次性挑战 token（登录第一步通过后调用） */
    String registerChallenge(UUID userId);

    /**
     * 消费挑战 token：校验存在/未过期/未用尽；失败次数达到上限则作废
     *
     * @return 关联的 userId；无效时抛 400
     */
    UUID consumeChallenge(String challengeToken);

    /** 作废挑战（第二步校验成功后调用，保证一次性） */
    void invalidateChallenge(String challengeToken);

    /** 记录一次挑战失败（达到上限即作废） */
    void recordChallengeFailure(String challengeToken);

    /** 仅供测试：清空挑战与限流状态 */
    void resetChallenges();
}
