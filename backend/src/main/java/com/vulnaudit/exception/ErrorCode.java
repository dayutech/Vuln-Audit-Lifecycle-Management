package com.vulnaudit.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 错误码枚举
 * 
 * 功能说明：
 * - 定义系统所有错误码
 * - 包含HTTP状态码和错误描述
 * - 统一错误码规范，便于前端处理
 */
@Getter
@AllArgsConstructor
public enum ErrorCode {

    /**
     * 操作成功
     */
    SUCCESS(200, "操作成功"),

    /**
     * 创建成功
     */
    CREATED(201, "创建成功"),

    /**
     * 参数错误
     */
    BAD_REQUEST(400, "参数错误"),

    /**
     * 未授权
     */
    UNAUTHORIZED(401, "未授权，请先登录"),

    /**
     * 无权限
     */
    FORBIDDEN(403, "无权限访问该资源"),

    /**
     * 资源未找到
     */
    NOT_FOUND(404, "资源未找到"),

    /**
     * 方法不允许
     */
    METHOD_NOT_ALLOWED(405, "请求方法不允许"),

    /**
     * 请求冲突
     */
    CONFLICT(409, "资源冲突"),

    /**
     * 参数校验失败
     */
    VALIDATION_ERROR(422, "参数校验失败"),

    /**
     * 服务器内部错误
     */
    INTERNAL_ERROR(500, "服务器内部错误"),

    /**
     * 服务不可用
     */
    SERVICE_UNAVAILABLE(503, "服务暂时不可用"),

    // 用户相关错误码 (1000-1099)

    /**
     * 用户名已存在
     */
    USERNAME_ALREADY_EXISTS(1001, "用户名已存在"),

    /**
     * 邮箱已存在
     */
    EMAIL_ALREADY_EXISTS(1002, "邮箱已存在"),

    /**
     * 用户不存在
     */
    USER_NOT_FOUND(1003, "用户不存在"),

    /**
     * 密码错误
     */
    PASSWORD_ERROR(1004, "密码错误"),

    /**
     * 用户已被禁用
     */
    USER_DISABLED(1005, "用户已被禁用"),

    /**
     * Token无效
     */
    TOKEN_INVALID(1006, "Token无效或已过期"),

    /**
     * 用户未激活
     */
    USER_NOT_ACTIVE(1007, "用户账号未激活"),

    /**
     * 无效的凭据
     */
    INVALID_CREDENTIALS(1008, "用户名或密码错误"),

    /**
     * 旧密码错误
     */
    INVALID_OLD_PASSWORD(1009, "旧密码错误"),

    /**
     * 无效的状态值
     */
    INVALID_STATUS(1010, "无效的状态值"),

    // 角色权限相关错误码 (1100-1199)

    /**
     * 角色不存在
     */
    ROLE_NOT_FOUND(1101, "角色不存在"),

    /**
     * 角色代码已存在
     */
    ROLE_CODE_EXISTS(1102, "角色代码已存在"),

    /**
     * 权限不存在
     */
    PERMISSION_NOT_FOUND(1103, "权限不存在"),

    /**
     * 权限代码已存在
     */
    PERMISSION_CODE_EXISTS(1104, "权限代码已存在"),

    /**
     * 用户已有该角色
     */
    USER_ROLE_EXISTS(1105, "用户已拥有该角色"),

    /**
     * 用户已有该权限
     */
    USER_PERMISSION_EXISTS(1106, "用户已拥有该权限"),

    // 资产相关错误码 (1200-1299)

    /**
     * 资产不存在
     */
    ASSET_NOT_FOUND(1201, "资产不存在"),

    /**
     * 资产已被认领
     */
    ASSET_ALREADY_CLAIMED(1202, "资产已被认领"),

    /**
     * 无权访问该资产
     */
    ASSET_ACCESS_DENIED(1203, "无权访问该资产"),

    /**
     * 资产编号已存在
     */
    ASSET_CODE_EXISTS(1204, "资产编号已存在"),

    /**
     * 资产认领不存在
     */
    ASSET_CLAIM_NOT_FOUND(1205, "资产认领记录不存在"),

    /**
     * 资产访问申请不存在
     */
    ASSET_ACCESS_NOT_FOUND(1206, "资产访问申请不存在"),

    // 漏洞相关错误码 (1300-1399)

    /**
     * 漏洞不存在
     */
    VULNERABILITY_NOT_FOUND(1301, "漏洞不存在"),

    /**
     * 漏洞状态流转非法
     */
    VULNERABILITY_STATUS_INVALID(1302, "漏洞状态流转非法"),

    /**
     * 漏洞附件不存在
     */
    VULNERABILITY_ATTACHMENT_NOT_FOUND(1303, "漏洞附件不存在"),

    /**
     * CVE 编号重复
     */
    VULNERABILITY_CVE_DUPLICATED(1304, "CVE 编号已存在"),

    // 文档相关错误码 (1400-1499)

    /**
     * 文档不存在
     */
    DOCUMENT_NOT_FOUND(1401, "文档不存在"),

    /**
     * 无权访问该文档
     */
    DOCUMENT_ACCESS_DENIED(1402, "无权访问该文档"),

    // 环境相关错误码 (1500-1599)

    /**
     * 环境不存在
     */
    ENVIRONMENT_NOT_FOUND(1501, "环境不存在"),

    /**
     * 环境模板不存在
     */
    ENVIRONMENT_TEMPLATE_NOT_FOUND(1502, "环境模板不存在"),

    // 工具相关错误码 (1600-1699)

    /**
     * 工具不存在
     */
    TOOL_NOT_FOUND(1601, "工具不存在"),

    // 任务相关错误码 (1700-1799)

    /**
     * 任务不存在
     */
    TASK_NOT_FOUND(1701, "任务不存在"),

    // 知识库相关错误码 (1800-1899)

    /**
     * 知识文章不存在
     */
    KNOWLEDGE_NOT_FOUND(1801, "知识文章不存在"),

    // 通知相关错误码 (1900-1999)

    /**
     * 通知不存在
     */
    NOTIFICATION_NOT_FOUND(1901, "通知不存在"),

    // 团队相关错误码 (2000-2099)

    /**
     * 团队不存在
     */
    TEAM_NOT_FOUND(2001, "团队不存在"),

    /**
     * 团队名称已存在
     */
    TEAM_NAME_EXISTS(2002, "团队名称已存在"),

    /**
     * 用户已在团队中
     */
    USER_ALREADY_IN_TEAM(2003, "用户已在团队中");

    /**
     * HTTP状态码
     */
    private final Integer code;

    /**
     * 错误描述
     */
    private final String message;

}
