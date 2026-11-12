package com.vulnaudit.entity.enums;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** 表单实例状态（t178）：DRAFT → ASSIGNED → SUBMITTED → CONFIRMED，可 RETURNED 退回 */
public enum FormSubmissionStatus {

    DRAFT, ASSIGNED, SUBMITTED, CONFIRMED, RETURNED;

    public static List<String> names() {
        return Arrays.stream(values()).map(Enum::name).toList();
    }

    public static FormSubmissionStatus require(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("status 不能为空（可选：" + String.join("/", names()) + "）");
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(value -> value.name().equals(normalized)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "status 不合法: " + raw + "（可选：" + String.join("/", names()) + "）"));
    }
}
