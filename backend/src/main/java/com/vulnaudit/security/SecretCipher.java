package com.vulnaudit.security;

import com.vulnaudit.config.TwoFactorProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * TOTP 密钥的加密存储（t124）：AES-GCM + 环境变量主密钥
 *
 * <p>格式：{@code v1:base64(iv[12] || ciphertext||tag)}。主密钥来自 {@code APP_TOTP_SECRET_KEY}；
 * <b>缺主密钥时不落库明文</b>：{@link #isAvailable()} 为 false，2FA 功能整体禁用并打 WARN。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecretCipher {

    private static final String PREFIX = "v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_BITS = 128;

    private final TwoFactorProperties properties;
    private final SecureRandom random = new SecureRandom();

    /** 主密钥是否可用（不可用时 2FA 功能禁用） */
    public boolean isAvailable() {
        return secretKey() != null;
    }

    /** 加密（返回带版本前缀的 Base64 串） */
    public String encrypt(String plaintext) {
        SecretKeySpec key = requireKey();
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + cipherText.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(cipherText, 0, combined, iv.length, cipherText.length);
            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception ex) {
            throw new IllegalStateException("TOTP 密钥加密失败", ex);
        }
    }

    /** 解密 */
    public String decrypt(String stored) {
        SecretKeySpec key = requireKey();
        if (stored == null || !stored.startsWith(PREFIX)) {
            throw new IllegalArgumentException("无法识别的密文格式");
        }
        try {
            byte[] combined = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            byte[] iv = new byte[IV_LENGTH];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] plain = cipher.doFinal(combined, IV_LENGTH, combined.length - IV_LENGTH);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception ex) {
            throw new IllegalStateException("TOTP 密钥解密失败", ex);
        }
    }

    private SecretKeySpec requireKey() {
        SecretKeySpec key = secretKey();
        if (key == null) {
            throw new IllegalStateException("未配置 APP_TOTP_SECRET_KEY，2FA 功能不可用");
        }
        return key;
    }

    /** 主密钥：SHA-256(env) → 32 字节 AES-256 密钥；未配置返回 null */
    private SecretKeySpec secretKey() {
        String master = properties.totpSecretKey();
        if (master == null || master.isBlank()) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(master.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(digest, "AES");
        } catch (Exception ex) {
            log.error("计算 TOTP 主密钥失败", ex);
            return null;
        }
    }
}
