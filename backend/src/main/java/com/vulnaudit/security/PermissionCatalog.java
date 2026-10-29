package com.vulnaudit.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 权限点目录（RBAC 基线数据源）。
 *
 * <p>功能说明：
 * <ul>
 *   <li>按 {@code docs/design/api_design.md} 的模块清单声明系统内置权限点，
 *       编码格式统一为 {@code 资源:操作}（如 {@code asset:read}、{@code user:manage}）；</li>
 *   <li>声明四个内置角色（admin / project_leader / team_leader / vulnerability_engineer）
 *       的默认权限集合，供启动初始化器幂等落库；</li>
 *   <li>作为「权限点 → API 路径 + HTTP 方法」的唯一事实来源，供后续 ABAC 研判使用。</li>
 * </ul>
 *
 * <p><b>双轨说明（重要）</b>：本轮仍是「角色轨」——控制器继续用
 * {@code @PreAuthorize("hasRole(...)")} 做粗粒度准入，本目录只负责把权限点数据落地，
 * 不改变任何既有鉴权语义。权限点落库后即可支撑下一阶段的「ABAC 轨」：
 * 按 {@code (apiPath, httpMethod)} 或资源实例属性判定，逐步替代硬编码角色名。
 * 为此本目录内每条权限点的 {@code (apiPath, httpMethod)} 组合保持全局唯一，
 * 以兼容 {@code PermissionRepository#findByApiPathAndHttpMethod} 的单值返回语义。
 */
public final class PermissionCatalog {

    private PermissionCatalog() {
    }

    /**
     * 单个权限点定义。
     *
     * @param code        权限编码，全局唯一，格式 {@code 资源:操作}
     * @param name        权限名称（展示用）
     * @param apiPath     对应 API 路径
     * @param httpMethod  对应 HTTP 方法（GET/POST/PUT/DELETE/PATCH）
     * @param description 权限描述
     */
    public record Definition(String code, String name, String apiPath, String httpMethod, String description) {
    }

    /**
     * 内置角色定义。
     *
     * @param code        角色编码
     * @param name        角色名称
     * @param description 角色描述
     */
    public record BuiltinRole(String code, String name, String description) {
    }

    /** 四个内置角色（与 roles 表 init.sql 保持一致） */
    public static final List<BuiltinRole> BUILTIN_ROLES = List.of(
            new BuiltinRole("admin", "系统管理员", "系统最高权限管理员"),
            new BuiltinRole("project_leader", "项目负责人", "项目负责人，管理项目下所有资产和团队"),
            new BuiltinRole("team_leader", "小组长", "团队小组长，管理团队成员和分配任务"),
            new BuiltinRole("vulnerability_engineer", "漏洞挖掘工程师", "漏洞挖掘工程师，新成员默认角色")
    );

    /**
     * 内置权限点清单。
     *
     * <p>模块顺序与 api_design.md 一致：用户 / 团队 / 资产 / 漏洞 / 环境 / 文档 /
     * 工具 / 任务 / 知识库 / 权限 / 通知 / 仪表盘 / 审计 / 审批 / 导入。
     */
    private static final List<Definition> DEFINITIONS = List.of(

            // ---------------- 用户管理模块 ----------------
            new Definition("user:read", "用户列表查看", "/api/v1/users", "GET", "查看用户列表"),
            new Definition("user:read_detail", "用户详情查看", "/api/v1/users/{id}", "GET", "查看指定用户详情"),
            new Definition("user:write", "用户信息编辑", "/api/v1/users/{id}", "PUT", "编辑用户基础信息"),
            new Definition("user:manage", "用户账号管理", "/api/v1/users", "POST", "创建用户、停用账号、重置密码"),
            new Definition("user:delete", "用户删除", "/api/v1/users/{id}", "DELETE", "停用/删除用户"),
            new Definition("user:role:read", "用户角色查看", "/api/v1/users/{id}/roles", "GET", "查看用户已分配角色"),
            new Definition("user:role:write", "用户角色分配", "/api/v1/users/{id}/roles", "POST", "为用户分配/撤销角色"),
            new Definition("user:permission:write", "用户直接授权", "/api/v1/users/{id}/permissions", "POST", "绕过角色直接授予用户权限"),

            // ---------------- 权限管理模块：角色 ----------------
            new Definition("role:read", "角色列表查看", "/api/v1/roles", "GET", "查看角色列表及角色权限"),
            new Definition("role:read_detail", "角色详情查看", "/api/v1/roles/{id}", "GET", "查看指定角色详情"),
            new Definition("role:write", "角色创建", "/api/v1/roles", "POST", "创建自定义角色"),
            new Definition("role:update", "角色编辑", "/api/v1/roles/{id}", "PUT", "编辑角色信息"),
            new Definition("role:delete", "角色删除", "/api/v1/roles/{id}", "DELETE", "删除非内置角色"),
            new Definition("role:permission:write", "角色权限分配", "/api/v1/roles/{id}/permissions", "POST", "为角色分配/移除权限点"),

            // ---------------- 权限管理模块：权限点 ----------------
            new Definition("permission:read", "权限点查看", "/api/v1/permissions", "GET", "查看系统权限点清单"),
            new Definition("permission:read_detail", "权限点详情查看", "/api/v1/permissions/{id}", "GET", "查看指定权限点"),
            new Definition("permission:write", "权限点创建", "/api/v1/permissions", "POST", "新增权限点"),
            new Definition("permission:update", "权限点编辑", "/api/v1/permissions/{id}", "PUT", "编辑权限点"),
            new Definition("permission:delete", "权限点删除", "/api/v1/permissions/{id}", "DELETE", "删除权限点"),

            // ---------------- 团队管理模块 ----------------
            new Definition("team:read", "团队列表查看", "/api/v1/teams", "GET", "查看团队列表/树"),
            new Definition("team:write", "团队创建", "/api/v1/teams", "POST", "创建团队"),
            new Definition("team:update", "团队编辑", "/api/v1/teams/{id}", "PUT", "编辑团队信息"),
            new Definition("team:delete", "团队删除", "/api/v1/teams/{id}", "DELETE", "删除团队"),
            new Definition("team:member:read", "团队成员查看", "/api/v1/teams/{id}/members", "GET", "查看团队成员"),
            new Definition("team:member:write", "团队成员管理", "/api/v1/teams/{id}/members", "POST", "添加/移除团队成员"),

            // ---------------- 资产管理模块 ----------------
            new Definition("asset:read", "资产列表查看", "/api/v1/assets", "GET", "查看资产列表"),
            new Definition("asset:read_detail", "资产详情查看", "/api/v1/assets/{id}", "GET", "查看资产详情（仍需数据级授权）"),
            new Definition("asset:write", "资产创建", "/api/v1/assets", "POST", "创建资产"),
            new Definition("asset:update", "资产编辑", "/api/v1/assets/{id}", "PUT", "编辑资产"),
            new Definition("asset:delete", "资产删除", "/api/v1/assets/{id}", "DELETE", "删除资产"),
            new Definition("asset:claim", "资产认领", "/api/v1/assets/{id}/claims", "POST", "认领资产"),
            new Definition("asset:access_request", "资产访问申请", "/api/v1/assets/{id}/access-requests", "POST", "发起资产访问申请"),
            new Definition("asset:access_review", "资产访问审批", "/api/v1/assets/access-requests/{requestId}/review", "POST", "审批资产访问申请"),
            new Definition("asset:metadata:read", "资产元数据查看", "/api/v1/assets/{assetId}/metadata", "GET", "查看资产应用元数据"),
            new Definition("asset:metadata:write", "资产元数据编辑", "/api/v1/assets/{assetId}/metadata", "PUT", "创建/更新资产应用元数据"),
            new Definition("asset:file:read", "资产文件查看", "/api/v1/assets/{assetId}/files", "GET", "查看/下载资产文件"),
            new Definition("asset:file:write", "资产文件上传", "/api/v1/assets/{assetId}/files", "POST", "上传资产文件及新版本"),
            new Definition("asset:group:read", "资产分组查看", "/api/v1/asset-groups", "GET", "查看资产分组"),
            new Definition("asset:group:write", "资产分组维护", "/api/v1/asset-groups", "POST", "创建/编辑资产分组及组内资产"),
            new Definition("asset:history:read", "资产变更历史查看", "/api/v1/assets/{id}/history", "GET", "查看资产变更历史"),

            // ---------------- 漏洞管理模块 ----------------
            new Definition("vulnerability:read", "漏洞列表查看", "/api/v1/vulnerabilities", "GET", "查看漏洞列表"),
            new Definition("vulnerability:read_detail", "漏洞详情查看", "/api/v1/vulnerabilities/{id}", "GET", "查看漏洞详情"),
            new Definition("vulnerability:write", "漏洞创建", "/api/v1/vulnerabilities", "POST", "创建漏洞记录"),
            new Definition("vulnerability:update", "漏洞编辑", "/api/v1/vulnerabilities/{id}", "PUT", "编辑漏洞信息"),
            new Definition("vulnerability:workflow", "漏洞状态流转", "/api/v1/vulnerabilities/{id}/workflow-status", "PATCH", "驱动漏洞工作流状态机"),
            new Definition("vulnerability:delete", "漏洞删除", "/api/v1/vulnerabilities/{id}", "DELETE", "删除漏洞记录"),
            new Definition("vulnerability:export", "漏洞导出", "/api/v1/vulnerabilities/export", "GET", "导出漏洞数据"),
            new Definition("vulnerability:attachment:write", "漏洞附件上传", "/api/v1/vulnerabilities/{id}/attachments", "POST", "上传/删除漏洞附件"),
            new Definition("vulnerability:tag:write", "漏洞标签维护", "/api/v1/vulnerabilities/{id}/tags", "POST", "为漏洞增删标签"),
            new Definition("vulnerability:history:read", "漏洞历史查看", "/api/v1/vulnerabilities/{id}/history", "GET", "查看漏洞状态变更历史"),

            // ---------------- 环境管理模块 ----------------
            new Definition("environment:read", "环境列表查看", "/api/v1/environments", "GET", "查看环境列表与详情"),
            new Definition("environment:write", "环境创建", "/api/v1/environments", "POST", "创建环境"),
            new Definition("environment:update", "环境编辑", "/api/v1/environments/{id}", "PUT", "编辑环境"),
            new Definition("environment:delete", "环境删除", "/api/v1/environments/{id}", "DELETE", "删除环境"),
            new Definition("environment:deploy", "环境部署", "/api/v1/environments/{id}/deploy", "POST", "部署/启停环境"),
            new Definition("environment:snapshot", "环境快照", "/api/v1/environments/{id}/snapshot", "POST", "创建/回滚环境快照"),

            // ---------------- 文档管理模块 ----------------
            new Definition("document:read", "文档列表查看", "/api/v1/documents", "GET", "查看文档列表与内容"),
            new Definition("document:write", "文档创建", "/api/v1/documents", "POST", "在线创建文档"),
            new Definition("document:update", "文档编辑", "/api/v1/documents/{id}", "PUT", "编辑文档并生成新版本"),
            new Definition("document:delete", "文档删除", "/api/v1/documents/{id}", "DELETE", "删除文档"),
            new Definition("document:access_request", "文档访问申请", "/api/v1/documents/{id}/access-request", "POST", "申请文档访问权限"),

            // ---------------- 工具资源库模块 ----------------
            new Definition("tool:read", "工具列表查看", "/api/v1/tools", "GET", "查看工具资源列表"),
            new Definition("tool:write", "工具上传", "/api/v1/tools", "POST", "上传工具资源"),
            new Definition("tool:update", "工具编辑", "/api/v1/tools/{id}", "PUT", "编辑工具信息/上传新版本"),
            new Definition("tool:delete", "工具删除", "/api/v1/tools/{id}", "DELETE", "删除工具资源"),
            new Definition("tool:download", "工具下载", "/api/v1/tools/{id}/download", "GET", "下载工具包"),

            // ---------------- 任务管理模块 ----------------
            new Definition("task:read", "任务列表查看", "/api/v1/tasks", "GET", "查看任务列表与详情"),
            new Definition("task:write", "任务创建", "/api/v1/tasks", "POST", "创建并分配任务"),
            new Definition("task:update", "任务编辑", "/api/v1/tasks/{id}", "PUT", "更新任务状态/进度"),
            new Definition("task:delete", "任务删除", "/api/v1/tasks/{id}", "DELETE", "删除任务"),
            new Definition("task:review", "任务审核", "/api/v1/tasks/{id}/review", "POST", "审核任务提交结果"),

            // ---------------- 知识库模块 ----------------
            new Definition("knowledge:read", "知识文章查看", "/api/v1/knowledge/articles", "GET", "查看/搜索知识文章"),
            new Definition("knowledge:write", "知识文章创建", "/api/v1/knowledge/articles", "POST", "创建知识文章"),
            new Definition("knowledge:update", "知识文章编辑", "/api/v1/knowledge/articles/{id}", "PUT", "编辑知识文章"),
            new Definition("knowledge:delete", "知识文章删除", "/api/v1/knowledge/articles/{id}", "DELETE", "删除知识文章"),
            new Definition("knowledge:comment:write", "知识评论发表", "/api/v1/knowledge/articles/{id}/comments", "POST", "发表知识文章评论"),

            // ---------------- 消息通知模块 ----------------
            new Definition("notification:read", "通知查看", "/api/v1/notifications", "GET", "查看通知/未读数/公告"),
            new Definition("notification:update", "通知已读处理", "/api/v1/notifications/{id}/read", "POST", "标记已读、批量已读、删除通知"),
            new Definition("notification:setting", "通知设置", "/api/v1/notifications/settings/{type}", "PUT", "维护个人通知渠道开关"),
            new Definition("notification:announcement:write", "系统公告管理", "/api/v1/notifications/announcements", "POST", "发布/编辑/删除系统公告"),
            new Definition("notification:subscribe", "资产动态订阅", "/api/v1/notifications/subscriptions/{assetId}", "POST", "订阅/取消订阅资产动态"),

            // ---------------- 仪表盘统计模块 ----------------
            new Definition("dashboard:read", "统计大盘查看", "/api/v1/dashboard/overview", "GET", "查看资产/漏洞/工作量/趋势统计"),
            new Definition("dashboard:my", "我的仪表盘", "/api/v1/dashboard/my", "GET", "查看与保存个人仪表盘配置"),
            new Definition("dashboard:export", "统计报表导出", "/api/v1/dashboard/export", "GET", "导出统计报表 CSV"),

            // ---------------- 审计日志模块 ----------------
            new Definition("audit:read", "审计日志查看", "/api/v1/audit-logs", "GET", "查看系统操作审计日志"),
            new Definition("audit:export", "审计日志导出", "/api/v1/audit-logs/export", "GET", "导出审计日志 CSV"),

            // ---------------- 审批相关模块 ----------------
            new Definition("approval:scope:read", "审批范围查看", "/api/v1/approval-scopes", "GET", "查看审批范围"),
            new Definition("approval:scope:write", "审批范围授予", "/api/v1/approval-scopes", "POST", "授予/撤销审批范围"),
            new Definition("approval:review", "访问申请审批", "/api/v1/access-requests/{id}/review", "POST", "审批访问申请"),

            // ---------------- 批量导入模块 ----------------
            new Definition("import:write", "批量数据导入", "/api/v1/import/assets", "POST", "CSV 批量导入资产/漏洞/用户")
    );

    /** 全部内置权限点（不可变） */
    public static List<Definition> definitions() {
        return DEFINITIONS;
    }

    /** 全部内置权限编码（按目录顺序，去重） */
    public static Set<String> allCodes() {
        Set<String> codes = new LinkedHashSet<>();
        DEFINITIONS.forEach(def -> codes.add(def.code()));
        return Collections.unmodifiableSet(codes);
    }

    /** 权限点总数 */
    public static int size() {
        return DEFINITIONS.size();
    }

    /**
     * 四个内置角色的默认权限集合。
     *
     * <p>admin 取「全部权限点」，随目录增长自动扩展，避免新增权限点后管理员漏授。
     * 其余角色按 {@code docs/design/permission-matrix.md} 收敛。
     *
     * @return 角色编码 → 权限编码列表（保持字典序，便于比对）
     */
    public static Map<String, List<String>> defaultRoleGrants() {
        Map<String, List<String>> grants = new LinkedHashMap<>();

        // 系统管理员：全部权限
        grants.put("admin", sorted(allCodes()));

        // 项目负责人：除「账号/角色/权限点维护」「公告管理」之外的全部业务与管理权限
        grants.put("project_leader", sorted(Set.of(
                "user:read", "user:read_detail", "user:write", "user:role:read",
                "role:read", "role:read_detail", "permission:read", "permission:read_detail",
                "team:read", "team:write", "team:update", "team:delete", "team:member:read", "team:member:write",
                "asset:read", "asset:read_detail", "asset:write", "asset:update", "asset:delete",
                "asset:claim", "asset:access_request", "asset:access_review",
                "asset:metadata:read", "asset:metadata:write", "asset:file:read", "asset:file:write",
                "asset:group:read", "asset:group:write", "asset:history:read",
                "vulnerability:read", "vulnerability:read_detail", "vulnerability:write", "vulnerability:update",
                "vulnerability:workflow", "vulnerability:delete", "vulnerability:export",
                "vulnerability:attachment:write", "vulnerability:tag:write", "vulnerability:history:read",
                "environment:read", "environment:write", "environment:update", "environment:delete",
                "environment:deploy", "environment:snapshot",
                "document:read", "document:write", "document:update", "document:delete", "document:access_request",
                "tool:read", "tool:write", "tool:update", "tool:delete", "tool:download",
                "task:read", "task:write", "task:update", "task:delete", "task:review",
                "knowledge:read", "knowledge:write", "knowledge:update", "knowledge:delete", "knowledge:comment:write",
                "notification:read", "notification:update", "notification:setting", "notification:subscribe",
                "dashboard:read", "dashboard:my", "dashboard:export",
                "audit:read", "audit:export",
                "approval:scope:read", "approval:scope:write", "approval:review",
                "import:write"
        )));

        // 小组长：本团队范围的业务读写 + 组内审批，无资产/漏洞删除与全局导出
        grants.put("team_leader", sorted(Set.of(
                "user:read", "user:read_detail", "user:role:read",
                "team:read", "team:member:read", "team:member:write",
                "asset:read", "asset:read_detail", "asset:write", "asset:claim", "asset:access_request",
                "asset:access_review", "asset:metadata:read", "asset:metadata:write",
                "asset:file:read", "asset:file:write", "asset:group:read", "asset:history:read",
                "vulnerability:read", "vulnerability:read_detail", "vulnerability:write", "vulnerability:update",
                "vulnerability:workflow", "vulnerability:export", "vulnerability:attachment:write",
                "vulnerability:tag:write", "vulnerability:history:read",
                "environment:read", "environment:deploy", "environment:snapshot",
                "document:read", "document:write", "document:update", "document:delete", "document:access_request",
                "tool:read", "tool:write", "tool:update", "tool:download",
                "task:read", "task:write", "task:update", "task:review",
                "knowledge:read", "knowledge:write", "knowledge:update", "knowledge:delete", "knowledge:comment:write",
                "notification:read", "notification:update", "notification:setting", "notification:subscribe",
                "dashboard:read", "dashboard:my",
                "audit:read",
                "approval:scope:read", "approval:review"
        )));

        // 漏洞挖掘工程师：资产只读+申请、漏洞提交与流转、文档/工具/知识库协作
        grants.put("vulnerability_engineer", sorted(Set.of(
                "asset:read", "asset:read_detail", "asset:claim", "asset:access_request",
                "asset:metadata:read", "asset:file:read", "asset:group:read", "asset:history:read",
                "vulnerability:read", "vulnerability:read_detail", "vulnerability:write", "vulnerability:update",
                "vulnerability:workflow", "vulnerability:attachment:write", "vulnerability:tag:write",
                "vulnerability:history:read",
                "environment:read",
                "document:read", "document:write", "document:update", "document:delete", "document:access_request",
                "tool:read", "tool:write", "tool:download",
                "task:read", "task:update",
                "knowledge:read", "knowledge:write", "knowledge:update", "knowledge:comment:write",
                "notification:read", "notification:update", "notification:setting", "notification:subscribe",
                "dashboard:my"
        )));

        return Collections.unmodifiableMap(grants);
    }

    private static List<String> sorted(Iterable<String> codes) {
        List<String> list = new ArrayList<>();
        codes.forEach(list::add);
        list.sort(String::compareTo);
        return List.copyOf(list);
    }
}
