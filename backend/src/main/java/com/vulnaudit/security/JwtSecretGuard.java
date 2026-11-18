package com.vulnaudit.security;

import org.springframework.lang.Nullable;

import java.util.Locale;
import java.util.Set;

/**
 * JWT 签名密钥启动校验（fail-fast）。
 *
 * <p><b>背景</b>：{@code application.yml} 里 {@code jwt.secret} 的写法是
 * {@code ${JWT_SECRET:your-256-bit-secret-key-here-please-change-in-production}}，
 * 一旦部署时漏配环境变量，应用会**静默**使用这个写在开源仓库里的默认密钥。
 * 任何人都能用它离线伪造 {@code sub=admin, roles=ROLE_ADMIN} 的 token 直接登录
 * （2026-11 实测：用默认密钥自签的 token 在 8089/8088 均返回 200 与 admin 权限清单）。
 *
 * <p><b>对策</b>：启动时校验；不合规直接抛 {@link IllegalStateException} 让应用起不来，
 * 而不是带着公开密钥对外提供服务。校验项：
 * <ol>
 *   <li>非空；</li>
 *   <li>不是已知占位符（仓库默认值、changeme、secret 等）；</li>
 *   <li>长度 ≥ {@value #MIN_LENGTH} 字符（HS256 的 HMAC 密钥下限，本项目实际按密钥长度走 HS384/HS512）。</li>
 * </ol>
 *
 * <p>部署侧生成方式：{@code openssl rand -base64 48}，写入 {@code backend.env} 的 {@code JWT_SECRET}。
 */
public final class JwtSecretGuard {

    /** 密钥最小长度（字符）。短于此长度的密钥可被暴力/字典攻击离线破解。 */
    public static final int MIN_LENGTH = 32;

    /** 已知的「不安全默认值」：仓库里的占位符与常见弱口令。 */
    private static final Set<String> KNOWN_PLACEHOLDERS = Set.of(
            "your-256-bit-secret-key-here-please-change-in-production",
            "your-secret-key",
            "your_jwt_secret",
            "change-me",
            "changeme",
            "secret",
            "jwt-secret",
            "test"
    );

    private JwtSecretGuard() {
    }

    /**
     * 校验密钥；不合规立即失败。
     *
     * @param secret 配置的 jwt.secret
     * @throws IllegalStateException 密钥缺失、是占位符或过短
     */
    public static void validate(@Nullable String secret) {
        String reason = findProblem(secret);
        if (reason != null) {
            throw new IllegalStateException(reason);
        }
    }

    /**
     * 返回不合规原因；合规返回 null（便于单测与调用方定制处理）。
     *
     * @param secret 配置的 jwt.secret
     * @return 不合规原因，或 null
     */
    @Nullable
    public static String findProblem(@Nullable String secret) {
        if (secret == null || secret.isBlank()) {
            return buildMessage("未配置 jwt.secret");
        }
        String trimmed = secret.trim();
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (KNOWN_PLACEHOLDERS.contains(lower) || lower.startsWith("your-")) {
            return buildMessage("jwt.secret 仍是仓库里的默认占位值（该值已公开，等于没有密钥）");
        }
        if (trimmed.length() < MIN_LENGTH) {
            return buildMessage("jwt.secret 长度 " + trimmed.length() + " 小于最小值 " + MIN_LENGTH);
        }
        if (trimmed.chars().distinct().count() < 8) {
            return buildMessage("jwt.secret 字符种类过少（疑似重复填充的弱密钥）");
        }
        return null;
    }

    private static String buildMessage(String cause) {
        return "JWT 密钥不合规，拒绝启动：" + cause
                + "。请设置环境变量 JWT_SECRET（例如 `openssl rand -base64 48`），"
                + "写入 deploy/backend.env 后重启服务；详见 deploy/README.md「安全加固」一节。";
    }
}