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
     * 文件过大（t160：超上传上限）
     */
    PAYLOAD_TOO_LARGE(413, "文件过大"),

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
    ASSET_ACCESS_DENIED(403, "无权访问该资产"),

    /**
     * 已拥有该资产的访问权限（重复申请）
     */
    ASSET_ACCESS_ALREADY_GRANTED(409, "你已有该资产的访问权限，无需重复申请"),

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
    USER_ALREADY_IN_TEAM(2003, "用户已在团队中"),

    // 客户/项目/期次相关错误码 (2100-2399，t68)

    /** 客户不存在 */
    CUSTOMER_NOT_FOUND(404, "客户不存在"),

    /** 客户编码已存在 */
    CUSTOMER_CODE_EXISTS(409, "客户编码已存在"),

    /** 客户下仍有项目，无法删除 */
    CUSTOMER_HAS_PROJECTS(409, "该客户下仍有项目，无法删除"),

    /** 项目不存在 */
    PROJECT_NOT_FOUND(404, "项目不存在"),

    /** 项目编码已存在 */
    PROJECT_CODE_EXISTS(409, "项目编码已存在"),

    /** 项目下仍有资产挂靠，无法删除 */
    PROJECT_HAS_ASSET_LINKS(409, "该项目下仍有资产挂靠，请先解绑"),

    /** 项目状态流转非法 */
    PROJECT_INVALID_TRANSITION(400, "项目状态流转非法"),

    /** 项目未启用分期，不能创建期次 */
    PROJECT_NOT_PHASED(400, "该项目未启用分期，不能创建期次"),

    /** 已存在按期次挂靠的记录，不能关闭分期 */
    PROJECT_PHASED_HAS_LINKS(409, "该项目已存在按期次挂靠的记录，无法关闭分期"),

    /** 期次不存在 */
    PROJECT_PHASE_NOT_FOUND(404, "项目期次不存在"),

    /** 期次下仍有资产挂靠，无法删除 */
    PROJECT_PHASE_HAS_ASSET_LINKS(409, "该期次下仍有资产挂靠，请先解绑"),

    /** 期次号在同项目内已存在 */
    PROJECT_PHASE_NO_EXISTS(409, "该项目的期次号已存在"),

    // ---------------- 资产—项目挂靠（t69） ----------------

    /** 同一资产在同一项目（同期次）已挂靠，重复挂靠被拒 */
    ASSET_PROJECT_LINK_EXISTS(409, "该资产已挂靠此项目（期次）"),

    /** 挂靠记录不存在，或不属于该资产（防枚举，与不存在同码） */
    ASSET_PROJECT_LINK_NOT_FOUND(404, "资产挂靠记录不存在"),

    /** 解绑后资产将没有任何项目归属，违反「资产必须挂靠至少一个项目」的不变式 */
    ASSET_LINK_REQUIRED(409, "资产必须至少挂靠一个项目，请先挂靠到其他项目再解绑"),

    /** 创建资产缺少 projectId（硬约束：不提供无项目资产） */
    ASSET_PROJECT_REQUIRED(400, "创建资产必须指定所属项目"),

    /** 传入的期次不属于指定项目 */
    PHASE_NOT_IN_PROJECT(400, "期次不属于该项目"),

    // ---------------- 数据库完整性兜底（t133） ----------------

    /**
     * 唯一键冲突（数据库唯一索引兜底）
     *
     * <p>当业务层的前置查重没有覆盖到（并发写入、Hibernate 生成约束名无法识别等），
     * {@code GlobalExceptionHandler} 用本码统一返回 <b>409</b>，不再落成 500。
     */
    DATA_DUPLICATE(409, "数据冲突，请检查唯一字段"),

    /**
     * 非唯一键的数据完整性失败（NOT NULL / 外键 / CHECK 等）
     *
     * <p>属于请求数据问题，统一返回 <b>400</b>（绝不误报 409）。
     */
    DATA_INTEGRITY_VIOLATION(400, "数据完整性校验失败，请检查必填项与关联数据");

    /**
     * HTTP状态码
     */
    private final Integer code;

    /**
     * 错误描述（中文默认文案，同时作为 i18n 的最终兜底）
     */
    private final String message;

    /**
     * i18n 消息码（t60）
     *
     * <p>返回 {@code null} 表示「本轮未纳入 i18n 覆盖」，此时对外文案仍使用 {@link #message}（中文）。
     * 已覆盖范围：认证 / 资产 / 漏洞 / 任务 四类高频错误 + 通用错误。
     */
    public String messageKey() {
        return switch (this) {
            case BAD_REQUEST -> "error.bad_request";
            case FORBIDDEN -> "error.forbidden";
            case NOT_FOUND -> "error.not_found";
            case CONFLICT -> "error.conflict";
            case PAYLOAD_TOO_LARGE -> "error.payload_too_large";
            case INTERNAL_ERROR -> "error.internal";
            case VALIDATION_ERROR -> "error.validation";
            case DATA_DUPLICATE -> "error.data.duplicate";
            case DATA_INTEGRITY_VIOLATION -> "error.data.integrity";
            case INVALID_STATUS -> "error.status.invalid";
            case UNAUTHORIZED -> "error.auth.unauthorized";
            case INVALID_CREDENTIALS -> "error.auth.invalid_credentials";
            case PASSWORD_ERROR -> "error.auth.password_error";
            case TOKEN_INVALID -> "error.auth.token_invalid";
            case ASSET_NOT_FOUND -> "error.asset.not_found";
            case ASSET_ACCESS_DENIED -> "error.asset.access_denied";
            case VULNERABILITY_NOT_FOUND -> "error.vulnerability.not_found";
            case VULNERABILITY_CVE_DUPLICATED -> "error.vulnerability.number_conflict";
            case TASK_NOT_FOUND -> "error.task.not_found";
            // 其余错误码（用户/角色/权限/文档/环境/工具/知识库/通知/团队等）本轮保留中文
            default -> null;
        };
    }

}
