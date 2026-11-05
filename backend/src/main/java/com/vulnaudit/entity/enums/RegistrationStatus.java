package com.vulnaudit.entity.enums;

/**
 * 注册申请状态（t102）
 *
 * <p>状态流转：{@link #PENDING} →（管理员通过）{@link #APPROVED} /（管理员拒绝）{@link #REJECTED}。
 * 只有 PENDING 可被审批，重复审批返回 409。
 */
public enum RegistrationStatus {

    /** 待管理员审批 */
    PENDING,

    /** 已通过（对应 users.status 转为 active） */
    APPROVED,

    /** 已拒绝（对应 users.status 转为 rejected，登录返回 403） */
    REJECTED
}
