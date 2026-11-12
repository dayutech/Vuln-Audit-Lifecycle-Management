package com.vulnaudit.service;

import com.vulnaudit.entity.FormValue;
import jakarta.annotation.Nullable;

/**
 * 表单「生效值」的唯一计算口径（t180）
 *
 * <pre>
 * effectiveValue = overrideValue ?? filledValue ?? sourceValue
 * </pre>
 *
 * <p>语义：
 * <ul>
 *   <li>`overrideValue` 是**修改覆盖**，只影响展示/导出（**绝不回写业务库**）；</li>
 *   <li>`filledValue` 是被指派人填的；</li>
 *   <li>`sourceValue` 是生成时的库快照（只读）。</li>
 * </ul>
 *
 * <p>空串按「没有值」处理（否则前端清空输入框会挡住必填校验）。导出、详情、必填校验**都必须走这里**，
 * 避免三处各写一套优先级导致口径漂移。
 */
public final class FormValues {

    private FormValues() {
    }

    @Nullable
    public static String effectiveValue(@Nullable FormValue value) {
        if (value == null) {
            return null;
        }
        return effectiveValue(value.getOverrideValue(), value.getFilledValue(), value.getSourceValue());
    }

    @Nullable
    public static String effectiveValue(@Nullable String overrideValue, @Nullable String filledValue,
                                        @Nullable String sourceValue) {
        if (hasText(overrideValue)) {
            return overrideValue;
        }
        if (hasText(filledValue)) {
            return filledValue;
        }
        return hasText(sourceValue) ? sourceValue : null;
    }

    public static boolean hasText(@Nullable String value) {
        return value != null && !value.isBlank();
    }
}
