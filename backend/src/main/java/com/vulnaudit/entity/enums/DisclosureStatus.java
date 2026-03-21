package com.vulnaudit.entity.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 公开披露状态枚举
 * 
 * 功能说明：
 * - 定义漏洞的公开披露状态
 * - 用于跟踪漏洞是否已被公开披露或分配CVE
 * 
 * 注意：
 * - 本系统是漏洞挖掘系统，不主动通知厂商
 * - 仅被动观察漏洞是否被其他人发现并披露
 */
@Getter
@AllArgsConstructor
public enum DisclosureStatus {

    /**
     * 未公开 - 漏洞尚未公开披露
     */
    UNDISCLOSED("undisclosed", "未公开"),

    /**
     * 已公开 - 漏洞已被公开披露
     */
    DISCLOSED("disclosed", "已公开"),

    /**
     * 已分配CVE - 漏洞已获得CVE编号
     */
    HAS_CVE("has_cve", "已分配CVE");

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
    public static DisclosureStatus fromCode(String code) {
        for (DisclosureStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return null;
    }

}
