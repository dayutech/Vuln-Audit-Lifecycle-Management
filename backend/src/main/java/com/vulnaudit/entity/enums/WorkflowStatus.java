package com.vulnaudit.entity.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 漏洞工作流状态枚举
 * 
 * 功能说明：
 * - 定义漏洞的工作流程状态
 * - 状态流转：discovered → verified → analyzing → analyzed → exploit_development → field_testing → delivered → closed
 */
@Getter
@AllArgsConstructor
public enum WorkflowStatus {

    /**
     * 已发现 - 漏洞刚被发现，尚未验证
     */
    DISCOVERED("discovered", "已发现"),

    /**
     * 已验证 - 漏洞已验证真实存在
     */
    VERIFIED("verified", "已验证"),

    /**
     * 分析中 - 正在进行漏洞分析
     */
    ANALYZING("analyzing", "分析中"),

    /**
     * 已分析 - 漏洞分析完成
     */
    ANALYZED("analyzed", "已分析"),

    /**
     * 利用开发中 - 正在开发漏洞利用代码
     */
    EXPLOIT_DEVELOPMENT("exploit_development", "利用开发中"),

    /**
     * 出场测试中 - 正在进行出场测试
     */
    FIELD_TESTING("field_testing", "出场测试中"),

    /**
     * 已交付 - 漏洞已交付给客户
     */
    DELIVERED("delivered", "已交付"),

    /**
     * 已关闭 - 漏洞流程结束
     */
    CLOSED("closed", "已关闭");

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
    public static WorkflowStatus fromCode(String code) {
        for (WorkflowStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 检查是否可以转换到目标状态
     * 
     * @param target 目标状态
     * @return 是否可以转换
     */
    public boolean canTransitionTo(WorkflowStatus target) {
        // 定义合法的状态流转规则
        return switch (this) {
            case DISCOVERED -> target == VERIFIED;
            case VERIFIED -> target == ANALYZING;
            case ANALYZING -> target == ANALYZED;
            case ANALYZED -> target == EXPLOIT_DEVELOPMENT || target == DELIVERED;
            case EXPLOIT_DEVELOPMENT -> target == FIELD_TESTING || target == DELIVERED;
            case FIELD_TESTING -> target == DELIVERED || target == EXPLOIT_DEVELOPMENT;
            case DELIVERED -> target == CLOSED;
            case CLOSED -> false;
        };
    }

}
