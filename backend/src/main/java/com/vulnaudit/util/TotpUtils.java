package com.vulnaudit.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.security.SecureRandom;
import java.time.Instant;

/**
 * TOTP 工具（t124）：Base32 编解码 + HMAC-SHA1 动态码 + 密钥生成与复杂度校验
 *
 * <p>规范：RFC 6238，步长 30s、6 位数字；校验允许 ±1 个时间窗（容忍时钟偏差）。
 */
public final class TotpUtils {

    /** Base32 字母表（RFC 4648，去填充） */
    private static final char[] BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    /** 时间步长（秒） */
    public static final int STEP_SECONDS = 30;

    /** 动态码位数 */
    public static final int DIGITS = 6;

    /** 每用户恢复码数量 */
    public static final int RECOVERY_CODE_COUNT = 10;

    private static final SecureRandom RANDOM = new SecureRandom();

    private TotpUtils() {
    }

    /** 生成 160bit（20 字节）随机密钥的 Base32 表示（32 字符，满足复杂度要求） */
    public static String generateSecret() {
        byte[] raw = new byte[20];
        RANDOM.nextBytes(raw);
        return base32Encode(raw);
    }

    /**
     * 密钥复杂度校验：长度 32、仅 A-Z2-7、非全相同、非连续递增/递减
     */
    public static boolean isValidSecret(String secret) {
        if (secret == null || secret.length() != 32) {
            return false;
        }
        boolean allSame = true;
        boolean ascending = true;
        for (int i = 0; i < secret.length(); i++) {
            char c = secret.charAt(i);
            boolean upper = c >= 'A' && c <= 'Z';
            boolean digit = c >= '2' && c <= '7';
            if (!upper && !digit) {
                return false;
            }
            if (i > 0) {
                if (secret.charAt(i) != secret.charAt(i - 1)) {
                    allSame = false;
                }
                if (secret.charAt(i) != secret.charAt(i - 1) + 1) {
                    ascending = false;
                }
            }
        }
        return !allSame && !ascending;
    }

    /** 生成 10 个恢复码（形如 XXXX-XXXX，字母数字，去掉易混字符） */
    public static java.util.List<String> generateRecoveryCodes() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        java.util.List<String> codes = new java.util.ArrayList<>(RECOVERY_CODE_COUNT);
        for (int i = 0; i < RECOVERY_CODE_COUNT; i++) {
            StringBuilder sb = new StringBuilder(9);
            for (int j = 0; j < 8; j++) {
                if (j == 4) {
                    sb.append('-');
                }
                sb.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
            }
            codes.add(sb.toString());
        }
        return codes;
    }

    /** 计算指定时间点的动态码 */
    public static String codeAt(String secretBase32, long epochSeconds) {
        long counter = epochSeconds / STEP_SECONDS;
        byte[] key = base32Decode(secretBase32);
        byte[] data = new byte[8];
        long c = counter;
        for (int i = 7; i >= 0; i--) {
            data[i] = (byte) (c & 0xFF);
            c >>= 8;
        }
        byte[] hash = hmacSha1(key, data);
        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);
        int otp = binary % 1_000_000;
        return String.format("%06d", otp);
    }

    /** 当前时间窗的动态码 */
    public static String currentCode(String secretBase32) {
        return codeAt(secretBase32, Instant.now().getEpochSecond());
    }

    /** 校验动态码（允许 ±1 个时间窗） */
    public static boolean verify(String secretBase32, String code) {
        if (code == null) {
            return false;
        }
        String normalized = code.replace(" ", "").trim();
        if (!normalized.matches("\\d{6}")) {
            return false;
        }
        long now = Instant.now().getEpochSecond();
        for (int offset = -1; offset <= 1; offset++) {
            String expected = codeAt(secretBase32, now + offset * (long) STEP_SECONDS);
            if (constantTimeEquals(expected, normalized)) {
                return true;
            }
        }
        return false;
    }

    /** otpauth URI（供扫码导入） */
    public static String otpauthUri(String issuer, String account, String secretBase32) {
        String label = urlEncode(issuer) + ":" + urlEncode(account);
        return "otpauth://totp/" + label + "?secret=" + secretBase32
                + "&issuer=" + urlEncode(issuer) + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SECONDS;
    }

    // ------------------------------------------------------------------ 内部

    public static String base32Encode(byte[] data) {
        StringBuilder sb = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                sb.append(BASE32[(buffer >> (bitsLeft - 5)) & 0x1F]);
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            sb.append(BASE32[(buffer << (5 - bitsLeft)) & 0x1F]);
        }
        return sb.toString();
    }

    public static byte[] base32Decode(String secret) {
        String normalized = secret.replace("=", "").replace(" ", "").toUpperCase(java.util.Locale.ROOT);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bitsLeft = 0;
        for (char c : normalized.toCharArray()) {
            int value = indexOf(c);
            if (value < 0) {
                throw new IllegalArgumentException("非法的 Base32 字符: " + c);
            }
            buffer = (buffer << 5) | value;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out.write((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return out.toByteArray();
    }

    private static int indexOf(char c) {
        for (int i = 0; i < BASE32.length; i++) {
            if (BASE32[i] == c) {
                return i;
            }
        }
        return -1;
    }

    private static byte[] hmacSha1(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            return mac.doFinal(data);
        } catch (Exception ex) {
            throw new IllegalStateException("HMAC-SHA1 计算失败", ex);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length(); i++) {
            diff |= a.charAt(i) ^ b.charAt(i);
        }
        return diff == 0;
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
