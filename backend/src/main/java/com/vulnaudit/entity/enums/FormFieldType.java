package com.vulnaudit.entity.enums;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** 表单字段类型白名单（t178） */
public enum FormFieldType {

    TEXT, TEXTAREA, NUMBER, DATE, SELECT, CHECKBOX, FILE;

    public static List<String> names() {
        return Arrays.stream(values()).map(Enum::name).toList();
    }

    public static FormFieldType require(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("field_type 不能为空（可选：" + String.join("/", names()) + "）");
        }
        String normalized = raw.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(value -> value.name().equals(normalized)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "field_type 不合法: " + raw + "（可选：" + String.join("/", names()) + "）"));
    }
}
