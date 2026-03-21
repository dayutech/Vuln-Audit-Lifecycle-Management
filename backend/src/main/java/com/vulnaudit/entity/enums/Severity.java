package com.vulnaudit.entity.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 漏洞严重程度枚举
 * 
 * 功能说明：
 * - 定义漏洞的严重程度等级
 * - 用于漏洞优先级排序
 */
@Getter
@AllArgsConstructor
public enum Severity {

    /**
     * 严重 - 最高优先级，需要立即处理
     */
    CRITICAL("critical", "严重", 4),

    /**
     * 高危 - 高优先级，需要尽快处理
     */
    HIGH("high", "高危", 3),

    /**
     * 中危 - 中等优先级，正常处理
     */
    MEDIUM("medium", "中危", 2),

    /**
     * 低危 - 低优先级，可延后处理
     */
    LOW("low", "低危", 1);

    /**
     * 状态代码
     */
    private final String code;

    /**
     * 状态描述
     */
    private final String description;

    /**
     * 优先级数值（数值越大优先级越高）
     */
    private final int priority;

    /**
     * 根据代码获取枚举
     * 
     * @param code 状态代码
     * @return 枚举值（如果存在）
     */
    public static Severity fromCode(String code) {
        for (Severity severity : values()) {
            if (severity.getCode().equals(code)) {
                return severity;
            }
        }
        return null;
    }

    /**
     * 根据CVSS评分获取严重程度
     * 
     * @param cvssScore CVSS评分（0-10）
     * @return 严重程度
     */
    public static Severity fromCvssScore(double cvssScore) {
        if (cvssScore >= 9.0) {
            return CRITICAL;
        } else if (cvssScore >= 7.0) {
            return HIGH;
        } else if (cvssScore >= 4.0) {
            return MEDIUM;
        } else {
            return LOW;
        }
    }

}
