package com.vulnaudit.service.impl;

import com.vulnaudit.config.TwoFactorProperties;
import com.vulnaudit.entity.RememberMeToken;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.RememberMeTokenRepository;
import com.vulnaudit.repository.UserRepository;
import com.vulnaudit.service.AuditLogService;
import com.vulnaudit.service.RememberMeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/** remember-me 实现（t124） */
@Slf4j
@Service
@RequiredArgsConstructor
public class RememberMeServiceImpl implements RememberMeService {

    private final RememberMeTokenRepository tokenRepository;
    private final UserRepository userRepository;
    private final TwoFactorProperties properties;
    private final AuditLogService auditLogService;

    private final SecureRandom random = new SecureRandom();

    @Override
    public boolean isEnabled() {
        return properties.getRememberMe().isEnabled();
    }

    @Override
    public String cookieName() {
        return properties.getRememberMe().getCookieName();
    }

    @Override
    public long cookieMaxAgeSeconds() {
        return properties.getRememberMe().getTtlDays() * 24L * 3600L;
    }

    @Override
    public boolean cookieSecure() {
        return properties.getRememberMe().isSecure();
    }

    @Override
    @Transactional
    public String issue(UUID userId, String userAgent, String ip) {
        String seriesId = randomToken(16);
        String secret = randomToken(32);
        tokenRepository.save(RememberMeToken.builder()
                .userId(userId)
                .seriesId(seriesId)
                .tokenHash(sha256(secret))
                .expiresAt(LocalDateTime.now().plusDays(properties.getRememberMe().getTtlDays()))
                .createdAt(LocalDateTime.now())
                .userAgent(truncate(userAgent))
                .ip(truncate(ip))
                .build());
        return seriesId + "." + secret;
    }

    @Override
    @Transactional
    public String rotate(String rawToken, String userAgent, String ip) {
        ParsedToken parsed = parse(rawToken);
        RememberMeToken stored = tokenRepository.findByTokenHash(sha256(parsed.secret()))
                .orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态已失效，请重新登录"));

        if (stored.getRevokedAt() != null) {
            // 重放：已作废令牌被再次使用 → 整条 series 撤销
            revokeSeries(stored.getSeriesId(), "令牌重放");
            auditLogService.log(stored.getUserId(), ACTION_REPLAY, "user", stored.getUserId(),
                    "检测到 remember-me 令牌重放，已撤销整条会话系列: series=" + stored.getSeriesId());
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态已失效，请重新登录");
        }
        if (stored.getExpiresAt() == null || stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态已过期，请重新登录");
        }
        if (userRepository.findById(stored.getUserId()).isEmpty()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "用户不存在或已删除");
        }

        // 轮换：旧行作废、同 series 签发新令牌
        stored.setRevokedAt(LocalDateTime.now());
        stored.setLastUsedAt(LocalDateTime.now());
        tokenRepository.save(stored);

        String secret = randomToken(32);
        tokenRepository.save(RememberMeToken.builder()
                .userId(stored.getUserId())
                .seriesId(stored.getSeriesId())
                .tokenHash(sha256(secret))
                .expiresAt(LocalDateTime.now().plusDays(properties.getRememberMe().getTtlDays()))
                .createdAt(LocalDateTime.now())
                .userAgent(truncate(userAgent))
                .ip(truncate(ip))
                .build());
        return stored.getSeriesId() + "." + secret;
    }

    @Override
    @Transactional
    public void revoke(String rawToken) {
        ParsedToken parsed = parseOrNull(rawToken);
        if (parsed == null) {
            return;
        }
        tokenRepository.findByTokenHash(sha256(parsed.secret())).ifPresent(token -> {
            if (token.getRevokedAt() == null) {
                token.setRevokedAt(LocalDateTime.now());
                tokenRepository.save(token);
            }
        });
    }

    @Override
    @Transactional
    public void revokeAll(UUID userId, String reason) {
        if (userId == null) {
            return;
        }
        List<RememberMeToken> tokens = tokenRepository.findByUserIdAndRevokedAtIsNull(userId);
        if (tokens.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        tokens.forEach(token -> token.setRevokedAt(now));
        tokenRepository.saveAll(tokens);
        auditLogService.log(userId, ACTION_REVOKED, "user", userId,
                "已撤销该用户全部 remember-me 令牌: count=" + tokens.size() + ", reason=" + reason);
        log.info("撤销 remember-me 令牌: userId={}, count={}, reason={}", userId, tokens.size(), reason);
    }

    @Override
    public long activeCount(UUID userId) {
        return tokenRepository.countByUserIdAndRevokedAtIsNullAndExpiresAtAfter(userId, LocalDateTime.now());
    }

    @Override
    public UUID userIdOf(String rawToken) {
        ParsedToken parsed = parseOrNull(rawToken);
        if (parsed == null) {
            return null;
        }
        return tokenRepository.findByTokenHash(sha256(parsed.secret()))
                .map(RememberMeToken::getUserId)
                .orElse(null);
    }

    // ------------------------------------------------------------------ 内部

    private record ParsedToken(String seriesId, String secret) {
    }

    private ParsedToken parse(String rawToken) {
        ParsedToken parsed = parseOrNull(rawToken);
        if (parsed == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "登录状态已失效，请重新登录");
        }
        return parsed;
    }

    private ParsedToken parseOrNull(String rawToken) {
        if (!StringUtils.hasText(rawToken)) {
            return null;
        }
        int dot = rawToken.indexOf('.');
        if (dot <= 0 || dot == rawToken.length() - 1) {
            return null;
        }
        return new ParsedToken(rawToken.substring(0, dot), rawToken.substring(dot + 1));
    }

    private void revokeSeries(String seriesId, String reason) {
        List<RememberMeToken> series = tokenRepository.findBySeriesId(seriesId);
        LocalDateTime now = LocalDateTime.now();
        series.stream().filter(t -> t.getRevokedAt() == null).forEach(t -> t.setRevokedAt(now));
        tokenRepository.saveAll(series);
        log.warn("撤销 remember-me series: series={}, reason={}", seriesId, reason);
    }

    private String randomToken(int bytes) {
        byte[] raw = new byte[bytes];
        random.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("SHA-256 失败", ex);
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 255 ? value : value.substring(0, 255);
    }
}
