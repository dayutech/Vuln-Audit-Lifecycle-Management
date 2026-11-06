package com.vulnaudit.service.impl;

import com.vulnaudit.config.TwoFactorProperties;
import com.vulnaudit.dto.TwoFactorInitResponse;
import com.vulnaudit.dto.TwoFactorStatusResponse;
import com.vulnaudit.entity.SystemSetting;
import com.vulnaudit.entity.TwoFactorRecoveryCode;
import com.vulnaudit.entity.User;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.SystemSettingRepository;
import com.vulnaudit.repository.TwoFactorRecoveryCodeRepository;
import com.vulnaudit.repository.UserRepository;
import com.vulnaudit.security.SecretCipher;
import com.vulnaudit.service.AuditLogService;
import com.vulnaudit.service.TwoFactorService;
import com.vulnaudit.util.TotpUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** 双因子认证实现（t124） */
@Slf4j
@Service
@RequiredArgsConstructor
public class TwoFactorServiceImpl implements TwoFactorService {

    private final UserRepository userRepository;
    private final TwoFactorRecoveryCodeRepository recoveryCodeRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final SecretCipher secretCipher;
    private final TwoFactorProperties properties;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    /** remember-me 撤销（t125 要求 5）：关闭 2FA 时让该用户全部长效令牌立即失效 */
    private final com.vulnaudit.service.RememberMeService rememberMeService;

    private final SecureRandom random = new SecureRandom();

    /** challengeToken → 挑战状态 */
    private final Map<String, Challenge> challenges = new ConcurrentHashMap<>();

    private static final class Challenge {
        private final UUID userId;
        private final Instant expiresAt;
        private int attempts;

        private Challenge(UUID userId, Instant expiresAt) {
            this.userId = userId;
            this.expiresAt = expiresAt;
        }
    }

    @Override
    public boolean isAvailable() {
        return secretCipher.isAvailable();
    }

    @Override
    public boolean isValidSecret(String secretBase32) {
        return TotpUtils.isValidSecret(secretBase32);
    }

    @Override
    @Transactional
    public TwoFactorInitResponse init(UUID userId) {
        requireAvailable();
        User user = requireUser(userId);
        if (user.isTwoFactorEnabled()) {
            throw new BusinessException(ErrorCode.CONFLICT, "双因子已开启，如需重置请先关闭再重新初始化");
        }
        String secret = TotpUtils.generateSecret();
        if (!TotpUtils.isValidSecret(secret)) {
            throw new IllegalStateException("生成的 TOTP 密钥未通过复杂度校验");
        }
        user.setTotpSecret(secretCipher.encrypt(secret));
        user.setTwoFactorEnabled(false);
        user.setTwoFactorConfirmedAt(null);
        userRepository.save(user);
        String issuer = properties.getTwoFactor().getIssuer();
        return new TwoFactorInitResponse(secret, TotpUtils.otpauthUri(issuer, user.getUsername(), secret));
    }

    @Override
    @Transactional
    public List<String> confirm(UUID userId, String code) {
        requireAvailable();
        User user = requireUser(userId);
        String secret = provisionalSecret(user);
        if (secret == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先调用初始化接口获取密钥");
        }
        if (!TotpUtils.verify(secret, code)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "动态码不正确，请检查设备时间后重试");
        }
        user.setTwoFactorEnabled(true);
        user.setTwoFactorConfirmedAt(LocalDateTime.now());
        userRepository.save(user);

        recoveryCodeRepository.deleteByUserId(userId);
        List<String> codes = TotpUtils.generateRecoveryCodes();
        for (String plain : codes) {
            recoveryCodeRepository.save(TwoFactorRecoveryCode.builder()
                    .userId(userId)
                    .codeHash(passwordEncoder.encode(normalizeRecovery(plain)))
                    .createdAt(LocalDateTime.now())
                    .build());
        }
        auditLogService.log(userId, ACTION_ENABLED, "user", userId,
                "已开启双因子认证: username=" + user.getUsername() + ", recoveryCodes=" + codes.size());
        log.info("双因子已开启: userId={}, recoveryCodes={}", userId, codes.size());
        return codes;
    }

    @Override
    @Transactional
    public void disable(UUID userId, String password, String code) {
        requireAvailable();
        User user = requireUser(userId);
        if (isForceTwoFactor()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "管理员已强制开启双因子，无法关闭");
        }
        if (!user.isTwoFactorEnabled()) {
            throw new BusinessException(ErrorCode.CONFLICT, "当前未开启双因子");
        }
        if (!StringUtils.hasText(password) || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前密码不正确，无法关闭双因子");
        }
        if (!verifyLoginCode(userId, code)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "动态码或恢复码不正确");
        }
        user.setTwoFactorEnabled(false);
        user.setTotpSecret(null);
        user.setTwoFactorConfirmedAt(null);
        userRepository.save(user);
        recoveryCodeRepository.deleteByUserId(userId);
        // t125：关闭 2FA 属于安全降级，必须让该用户全部 remember-me 长效令牌立即失效
        rememberMeService.revokeAll(userId, "TWO_FACTOR_DISABLED");
        auditLogService.log(userId, ACTION_DISABLED, "user", userId,
                "已关闭双因子认证: username=" + user.getUsername());
    }

    @Override
    public TwoFactorStatusResponse status(UUID userId) {
        User user = requireUser(userId);
        long left = user.isTwoFactorEnabled() ? recoveryCodeRepository.countByUserIdAndUsedAtIsNull(userId) : 0;
        return new TwoFactorStatusResponse(user.isTwoFactorEnabled(), user.getTwoFactorConfirmedAt(),
                left, isForceTwoFactor(), isAvailable());
    }

    @Override
    @Transactional
    public boolean verifyLoginCode(UUID userId, String code) {
        if (!isAvailable() || !StringUtils.hasText(code)) {
            return false;
        }
        User user = userRepository.findById(userId).orElse(null);
        if (user == null || !user.isTwoFactorEnabled()) {
            return false;
        }
        String secret = decryptSecret(user);
        if (secret != null && TotpUtils.verify(secret, code)) {
            return true;
        }
        return consumeRecoveryCode(userId, code);
    }

    @Override
    public boolean mustSetupTwoFactor(User user) {
        return user != null && !user.isTwoFactorEnabled() && isForceTwoFactor();
    }

    @Override
    public boolean isForceTwoFactor() {
        return systemSettingRepository.findBySettingKey(FORCE_TWO_FACTOR_KEY)
                .map(setting -> "true".equalsIgnoreCase(setting.getSettingValue()))
                .orElse(false);
    }

    @Override
    @Transactional
    public boolean setForceTwoFactor(boolean enabled, UUID actorId) {
        SystemSetting setting = systemSettingRepository.findBySettingKey(FORCE_TWO_FACTOR_KEY)
                .orElseGet(() -> SystemSetting.builder()
                        .settingKey(FORCE_TWO_FACTOR_KEY)
                        .settingValue("false")
                        .updatedAt(LocalDateTime.now())
                        .build());
        boolean previous = "true".equalsIgnoreCase(setting.getSettingValue());
        setting.setSettingValue(String.valueOf(enabled));
        setting.setUpdatedBy(actorId);
        setting.setUpdatedAt(LocalDateTime.now());
        systemSettingRepository.save(setting);
        auditLogService.log(actorId, ACTION_FORCE_SETTING, "system_setting", setting.getId(),
                "双因子强制开关: from=" + previous + " -> to=" + enabled);
        log.info("双因子强制开关已更新: {} -> {}", previous, enabled);
        return enabled;
    }

    @Override
    public String registerChallenge(UUID userId) {
        cleanupChallenges();
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        challenges.put(token, new Challenge(userId,
                Instant.now().plusSeconds(properties.getTwoFactor().getChallengeTtlSeconds())));
        return token;
    }

    @Override
    public UUID consumeChallenge(String challengeToken) {
        Challenge challenge = challengeToken == null ? null : challenges.get(challengeToken);
        if (challenge == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "二次验证会话不存在或已失效，请重新登录");
        }
        if (challenge.expiresAt.isBefore(Instant.now())) {
            challenges.remove(challengeToken);
            throw new BusinessException(ErrorCode.BAD_REQUEST, "二次验证会话已过期，请重新登录");
        }
        return challenge.userId;
    }

    @Override
    public void recordChallengeFailure(String challengeToken) {
        Challenge challenge = challengeToken == null ? null : challenges.get(challengeToken);
        if (challenge == null) {
            return;
        }
        challenge.attempts++;
        if (challenge.attempts >= properties.getTwoFactor().getChallengeMaxAttempts()) {
            challenges.remove(challengeToken);
            log.warn("二次验证失败次数达到上限，挑战已作废: userId={}", challenge.userId);
        }
    }

    @Override
    public void resetChallenges() {
        challenges.clear();
    }

    // ------------------------------------------------------------------ 内部

    /** 第二步校验成功后作废该 challenge（一次性） */
    public void invalidateChallenge(String challengeToken) {
        if (challengeToken != null) {
            challenges.remove(challengeToken);
        }
    }

    private void requireAvailable() {
        if (!isAvailable()) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "双因子功能未启用（服务端未配置主密钥），请联系管理员");
        }
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    /** 待确认密钥（解密后）；未初始化返回 null */
    private String provisionalSecret(User user) {
        return decryptSecret(user);
    }

    private String decryptSecret(User user) {
        if (!StringUtils.hasText(user.getTotpSecret())) {
            return null;
        }
        try {
            return secretCipher.decrypt(user.getTotpSecret());
        } catch (RuntimeException ex) {
            log.error("TOTP 密钥解密失败: userId={}", user.getId(), ex);
            return null;
        }
    }

    /** 恢复码：比对未使用的哈希，命中即标记已用（一次性） */
    private boolean consumeRecoveryCode(UUID userId, String code) {
        String normalized = normalizeRecovery(code);
        for (TwoFactorRecoveryCode candidate : recoveryCodeRepository.findByUserIdAndUsedAtIsNull(userId)) {
            if (passwordEncoder.matches(normalized, candidate.getCodeHash())) {
                candidate.setUsedAt(LocalDateTime.now());
                recoveryCodeRepository.save(candidate);
                return true;
            }
        }
        return false;
    }

    private String normalizeRecovery(String code) {
        return code == null ? "" : code.replace("-", "").replace(" ", "").toUpperCase(java.util.Locale.ROOT);
    }

    private void cleanupChallenges() {
        Instant now = Instant.now();
        challenges.entrySet().removeIf(entry -> entry.getValue().expiresAt.isBefore(now));
    }
}
