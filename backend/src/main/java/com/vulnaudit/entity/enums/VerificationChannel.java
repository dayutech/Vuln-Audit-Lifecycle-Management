package com.vulnaudit.entity.enums;

import java.util.Locale;

/**
 * 验证码投递渠道（t102）
 */
public enum VerificationChannel {

    /** 短信（中国大陆手机号） */
    SMS,

    /** 邮件 */
    EMAIL;

    /**
     * 解析渠道代码（大小写不敏感）；未知返回 {@code null}
     */
    public static VerificationChannel fromCode(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        for (VerificationChannel channel : values()) {
            if (channel.name().equals(normalized)) {
                return channel;
            }
        }
        return null;
    }
}
