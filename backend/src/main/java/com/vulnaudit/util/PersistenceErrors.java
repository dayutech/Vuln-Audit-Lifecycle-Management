package com.vulnaudit.util;

import org.springframework.dao.DataIntegrityViolationException;

import java.util.Locale;

/**
 * 数据库约束异常判定工具
 *
 * <p>用于区分「唯一键冲突」（并发重复编号，应返回 409 让前端重试）
 * 与其他完整性约束失败（字段 NOT NULL / 长度超限等，属于请求数据问题）。
 */
public final class PersistenceErrors {

    private PersistenceErrors() {
    }

    /**
     * 是否为唯一键 / 主键冲突
     *
     * <p>兼容 H2（"Unique index or primary key violation"）、MariaDB/MySQL
     * （"Duplicate entry ... for key 'uk_xxx'"）以及 Hibernate 包装后的消息。
     */
    public static boolean isUniqueViolation(DataIntegrityViolationException exception) {
        if (exception == null) {
            return false;
        }
        Throwable cause = exception.getMostSpecificCause();
        String message = cause == null ? exception.getMessage() : cause.getMessage();
        if (message == null) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return lower.contains("unique")
                || lower.contains("duplicate entry")
                || lower.contains("duplicate key")
                || lower.contains("uk_");
    }

    /**
     * 提取最具体的错误消息（用于返回给调用方的说明）
     */
    public static String detail(DataIntegrityViolationException exception) {
        Throwable cause = exception.getMostSpecificCause();
        if (cause != null && cause.getMessage() != null) {
            String message = cause.getMessage();
            return message.length() > 200 ? message.substring(0, 200) : message;
        }
        return "数据完整性约束校验失败";
    }
}
