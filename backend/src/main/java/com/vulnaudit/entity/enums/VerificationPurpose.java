package com.vulnaudit.entity.enums;

import java.util.Locale;

/**
 * 验证码用途（t102）
 *
 * <p>当前仅 {@link #REGISTER} 落地（自助注册）；{@link #RESET_PASSWORD} / {@link #BIND} 预留给后续迭代，
 * 服务层对未启用用途返回 400，避免「能发码但不能用」的隐性漏洞。
 */
public enum VerificationPurpose {

    /** 自助注册（手机号 + 可选邮箱） */
    REGISTER,

    /** 找回密码（预留） */
    RESET_PASSWORD,

    /** 绑定手机/邮箱（预留） */
    BIND;

    /**
     * 解析用途代码（大写不敏感）；未知返回 {@code null}
     */
    public static VerificationPurpose fromCode(String raw) {
        if (raw == null) {
            return null;
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        for (VerificationPurpose purpose : values()) {
            if (purpose.name().equals(normalized)) {
                return purpose;
            }
        }
        return null;
    }

    /**
     * 当前是否已启用（仅注册）
     */
    public boolean isEnabled() {
        return this == REGISTER;
    }
}
