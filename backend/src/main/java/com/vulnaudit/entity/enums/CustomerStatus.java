package com.vulnaudit.entity.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 客户确认状态枚举
 * 
 * 功能说明：
 * - 定义漏洞的客户确认状态
 * - 独立于工作流状态，由客户确认
 */
@Getter
@AllArgsConstructor
public enum CustomerStatus {

    /**
     * 待确认 - 等待客户确认
     */
    PENDING("pending", "待确认"),

    /**
     * 已确认 - 客户已确认漏洞
     */
    CONFIRMED("confirmed", "已确认"),

    /**
     * 已拒绝 - 客户拒绝漏洞
     */
    REJECTED("rejected", "已拒绝");

    /**
     * 状态代码
     */
    private final String code;

    /**
     * 状态描述
     */
    private final String description;

    /**
     * 根据代码获取枚举
     * 
     * @param code 状态代码
     * @return 枚举值（如果存在）
     */
    public static CustomerStatus fromCode(String code) {
        for (CustomerStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return null;
    }

}
