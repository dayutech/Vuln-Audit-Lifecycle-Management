package com.vulnaudit.exception;

import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 数据库完整性异常 → 对外消息码（t133）
 *
 * <p><b>要解决的问题</b>：{@code GlobalExceptionHandler} 此前没有
 * {@link DataIntegrityViolationException} 分支，唯一键冲突（例如两次提交同一个客户编码）
 * 会落到兜底 {@code Exception} 分支变成 <b>500</b>，前端只能显示「服务器内部错误」。
 *
 * <p><b>判定口径（先分类，再取文案）</b>：
 * <ol>
 *   <li>SQLState {@code 23505}（H2 唯一键）或消息含 {@code duplicate}/{@code unique} → 唯一键冲突 → <b>409</b>；</li>
 *   <li>其余（{@code 23502} NOT NULL、{@code 23503/23506} 外键、{@code check} 约束等）→ 请求数据问题 → <b>400</b>，
 *       不误判成 409；</li>
 *   <li>文案只取语言包，<b>不把 SQL、表名、列名、约束名回显给前端</b>（原文只进日志）。</li>
 * </ol>
 *
 * <p><b>约束名 → 消息码</b>：同时匹配三种常见形态，避免依赖单一数据库的命名：
 * <ul>
 *   <li>MariaDB 显式索引名：{@code Duplicate entry 'X' for key 'uk_customers_code'}；</li>
 *   <li>MariaDB / MySQL 8 的「表.索引」形态：{@code for key 'customers.uk_customers_code'}、
 *       {@code for key 'users.username'}（Hibernate 的 {@code unique = true} 列在库里是 {@code UK_xxx}，
 *       两者都覆盖）；</li>
 *   <li>H2 的列形态：{@code Unique index or primary key violation: … ON PUBLIC.CUSTOMERS(CODE NULLS FIRST)}。</li>
 * </ul>
 *
 * <p>匹配不到的约束 → 返回 {@code null}，由调用方使用通用文案 {@code error.data.duplicate}。
 */
public final class DataConflictMessages {

    /** H2：唯一键 / 主键冲突 */
    private static final String SQLSTATE_UNIQUE_H2 = "23505";
    /** NOT NULL 失败（H2 23502、Postgres 23502）；MySQL/MariaDB 为 23000 + 特定消息 */
    private static final String SQLSTATE_NOT_NULL = "23502";

    /** 约束名 / 表.索引 形态 → 专用消息码（顺序即优先级） */
    private static final Map<String, String> CONSTRAINT_KEYS = buildConstraintKeys();

    private DataConflictMessages() {
    }

    private static Map<String, String> buildConstraintKeys() {
        Map<String, String> map = new LinkedHashMap<>();
        // 用户
        put(map, "error.user.username.exists", "uk_users_username", "users.username", "users.uk_users_username");
        put(map, "error.user.email.exists", "uk_users_email", "users.email", "users.uk_users_email");
        put(map, "error.user.phone.exists", "uk_users_phone", "users.phone", "users.uk_users_phone");
        // 客户 / 项目 / 元数据
        put(map, "error.customer.code.exists", "uk_customers_code", "customers.code", "customers.uk_customers_code");
        put(map, "error.project.code.exists", "uk_projects_code", "projects.code", "projects.uk_projects_code");
        put(map, "error.industry_type.code.exists", "uk_industry_types_code", "industry_types.code");
        put(map, "error.project_type.code.exists", "uk_project_types_code", "project_types.code");
        put(map, "error.project.phase_no.exists", "uk_project_phases_project_no", "project_phases.project_no");
        put(map, "error.asset.code.exists", "uk_assets_code", "assets.code", "assets.uk_assets_code");
        // 角色 / 权限
        put(map, "error.role.code.exists", "uk_roles_code", "roles.code", "roles.uk_roles_code");
        put(map, "error.permission.code.exists", "uk_permissions_code", "permissions.code");
        // 登录态 / 文档版本
        put(map, "error.auth.remember_me.conflict", "uk_rm_hash", "remember_me_tokens.token_hash");
        // document_versions 的唯一键是 (document_id, version_no) 复合键，H2 消息会先列 document_id
        put(map, "error.document.version.conflict",
                "uk_document_versions_document_version", "uk_document_version",
                "document_versions(document_id", "document_versions.version_no");
        return java.util.Collections.unmodifiableMap(map);
    }

    private static void put(Map<String, String> map, String messageKey, String... needles) {
        for (String needle : needles) {
            map.put(needle.toLowerCase(Locale.ROOT), messageKey);
        }
    }

    /**
     * 是否为唯一键（或主键）冲突
     *
     * <p>优先看 SQLState（数据库无关），再兜底看消息关键词（MariaDB 的唯一键冲突是 23000，
     * 与部分外键失败同码，必须结合消息判断）。
     */
    public static boolean isUniqueViolation(DataIntegrityViolationException exception) {
        if (exception == null) {
            return false;
        }
        String sqlState = sqlStateOf(exception);
        if (SQLSTATE_UNIQUE_H2.equals(sqlState)) {
            return true;
        }
        String detail = detailOf(exception);
        return detail.contains("duplicate entry")
                || detail.contains("duplicate key")
                || detail.contains("unique index")
                || detail.contains("unique constraint")
                || detail.contains("unique or primary key violation")
                || detail.contains("uk_");
    }

    /**
     * 是否为「非唯一键」的完整性失败（NOT NULL / 外键 / CHECK 等）
     *
     * <p>只要不是唯一键冲突，就按请求数据问题处理（400），绝不能误报 409。
     */
    public static boolean isNonUniqueIntegrityViolation(DataIntegrityViolationException exception) {
        return exception != null && !isUniqueViolation(exception);
    }

    /**
     * 从异常中解析专用消息码
     *
     * @return 命中的消息码；无法识别约束时返回 {@code null}（调用方用通用文案）
     */
    public static String messageKeyFor(DataIntegrityViolationException exception) {
        String detail = detailOf(exception);
        if (detail.isEmpty()) {
            return null;
        }
        // 约束名优先（uk_...），命中即返回；再退到「表.索引 / 表(列)」形态
        for (Map.Entry<String, String> entry : CONSTRAINT_KEYS.entrySet()) {
            String needle = entry.getKey();
            if (needle.startsWith("uk_") && detail.contains(needle)) {
                return entry.getValue();
            }
        }
        for (Map.Entry<String, String> entry : CONSTRAINT_KEYS.entrySet()) {
            String needle = entry.getKey();
            if (!needle.startsWith("uk_") && matchesColumnShape(detail, needle)) {
                return entry.getValue();
            }
        }
        return null;
    }

    /** {@code customers(code}、{@code users.username}、{@code customers.uk_customers_code} 三种形态 */
    private static boolean matchesColumnShape(String detail, String needle) {
        if (detail.contains(needle)) {
            return true;
        }
        // 「表.列」在 H2 / MariaDB 消息里可能带 schema 前缀（public.customers.code）
        int dot = needle.indexOf('.');
        if (dot > 0) {
            String table = needle.substring(0, dot);
            String column = needle.substring(dot + 1);
            return detail.contains(table + "(" + column) || detail.contains("public." + needle);
        }
        return false;
    }

    /** 最具体原因的消息（小写、截断 300 字，仅用于判定与日志） */
    public static String detailOf(DataIntegrityViolationException exception) {
        if (exception == null) {
            return "";
        }
        Throwable cause = exception.getMostSpecificCause();
        String message = cause == null ? exception.getMessage() : cause.getMessage();
        if (message == null) {
            return "";
        }
        String normalized = message.toLowerCase(Locale.ROOT).replace('\n', ' ');
        return normalized.length() > 300 ? normalized.substring(0, 300) : normalized;
    }

    /** 取根因的 SQLState（反射无关：{@link SQLException#getSQLState()}） */
    public static String sqlStateOf(DataIntegrityViolationException exception) {
        Throwable cause = exception == null ? null : exception.getMostSpecificCause();
        while (cause != null) {
            if (cause instanceof SQLException sqlException && sqlException.getSQLState() != null) {
                return sqlException.getSQLState();
            }
            cause = cause.getCause();
        }
        return null;
    }

    /** 是否为 NOT NULL 失败（H2 23502 / 消息含 not null） */
    public static boolean isNotNullViolation(DataIntegrityViolationException exception) {
        if (SQLSTATE_NOT_NULL.equals(sqlStateOf(exception))) {
            return true;
        }
        String detail = detailOf(exception);
        return detail.contains("not null") || detail.contains("null not allowed") || detail.contains("cannot be null");
    }
}
