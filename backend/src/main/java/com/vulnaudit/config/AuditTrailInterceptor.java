package com.vulnaudit.config;

import com.vulnaudit.security.RoleAuthority;
import com.vulnaudit.service.AuditLogService;
import com.vulnaudit.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 写操作审计拦截器。
 *
 * <p>问题背景：原先只有 TaskController 手工调用 {@code auditLogService.log}，
 * 资产、漏洞、文档、用户、角色等敏感写操作完全没有审计记录，与 SRS 3.7.2
 * 「操作审计：记录所有敏感操作日志」不符。
 *
 * <p>本拦截器对 {@code /api/v1/**} 下的 POST/PUT/PATCH/DELETE 请求统一落审计，
 * 仅记录业务成功（2xx）的请求，避免把失败尝试与正常操作混淆。
 *
 * <p>已手工记录审计的控制器会被跳过，避免同一操作产生两条记录：
 * <ul>
 *   <li>{@code TaskController}：每个写接口都手工记录带业务语义的 action（task_create 等）；</li>
 *   <li>{@code ImportController}：{@code ImportServiceImpl} 手工记录包含文件名、成功/失败行数、
 *       首条失败原因的审计（action 形如 {@code POST ImportController.importAssets}）；
 *       拦截器生成的记录缺少这些上下文，两者叠加会让一次导入出现两条 import 审计。</li>
 * </ul>
 * 新增「手工审计」的控制器时，务必同步把类名加入 {@link #SKIP_CONTROLLERS}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditTrailInterceptor implements HandlerInterceptor {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    /**
     * 已经手工记录审计的控制器，跳过统一记录。
     *
     * <p>当前仅这两个：TaskController（逐接口手写 action）与 ImportController
     * （ImportServiceImpl 手写带上下文详情的 action）。其余控制器统一由本拦截器记录。
     */
    private static final String[] SKIP_CONTROLLERS = {"TaskController", "ImportController", "AdminImpersonationController"};

    /**
     * 已手工记录审计的单个写端点（「方法 + URI」精确匹配），用于同一控制器里只有个别端点
     * 需要保留语义化审计的场景（t96）。
     *
     * <p>{@code PUT /api/v1/users/me/preferences} 由 {@code UserPreferenceServiceImpl} 写
     * {@code action=UPDATE_LOGIN_THEME}、{@code resourceType=user}、{@code resourceId=本人}，
     * 拦截器再写一条「PUT UserController.updateMyPreferences」只会让一次改偏好出现两条审计。
     * 注意不能把 {@code UserController} 整体加入 {@link #SKIP_CONTROLLERS} ——
     * 用户 CRUD / 角色分配 / 重置密码的审计仍必须由拦截器覆盖。
     *
     * <p>t102 追加：注册审批的通过/拒绝由 {@code RegistrationServiceImpl} 写
     * {@code REGISTRATION_APPROVE / REGISTRATION_REJECT}（含 reviewer、申请ID、拒绝原因），
     * 拦截器不再重复记录。
     */
    private static final String[] SKIP_ENDPOINTS = {
            "PUT /api/v1/users/me/preferences",
            "POST /api/v1/admin/registration-requests/{id}/approve",
            "POST /api/v1/admin/registration-requests/{id}/reject"
    };

    private final AuditLogService auditLogService;
    private final UserService userService;

    @Override
    public void afterCompletion(@NonNull HttpServletRequest request,
                                @NonNull HttpServletResponse response,
                                @NonNull Object handler,
                                @Nullable Exception ex) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return;
        }
        String method = request.getMethod();
        if (!"POST".equals(method) && !"PUT".equals(method)
                && !"PATCH".equals(method) && !"DELETE".equals(method)) {
            return;
        }
        if (ex != null || response.getStatus() >= 400) {
            return;
        }
        String controller = handlerMethod.getBeanType().getSimpleName();
        for (String skip : SKIP_CONTROLLERS) {
            if (skip.equals(controller)) {
                return;
            }
        }
        if (shouldSkipEndpoint(method, request.getRequestURI())) {
            return;
        }
        try {
            UUID userId = currentUserId();
            if (userId == null) {
                return;
            }
            String action = method + " " + controller + "." + handlerMethod.getMethod().getName();
            String resourceType = resolveResourceType(request.getRequestURI());
            UUID resourceId = resolveResourceId(request.getRequestURI());
            String detail = request.getRequestURI();
            // t90：模拟视角下的业务审计归属 —— user_id 记目标用户（真实执行者），
            // details 追加 impersonatedBy=<adminId>，便于回溯「谁以谁的身份做了这件事」
            com.vulnaudit.security.ImpersonationContext.Snapshot impersonation =
                    com.vulnaudit.security.ImpersonationContext.get();
            if (impersonation != null && impersonation.actorId() != null) {
                detail = detail + "; impersonatedBy=" + impersonation.actorId();
            }
            auditLogService.log(userId, truncate(action, 50), resourceType, resourceId,
                    truncate(detail, 500), request.getRemoteAddr());
        } catch (Exception e) {
            // 审计失败不能影响主流程
            log.warn("写操作审计记录失败: {} {}", method, request.getRequestURI(), e);
        }
    }

    /**
     * 该请求是否属于「已手工记录审计的单个端点」（见 {@link #SKIP_ENDPOINTS}）
     *
     * <p>支持路径变量占位符：条目里的 {@code {id}} / {@code {任何名字}} 会被当成「一个路径段」匹配
     * （不允许跨 {@code /}），因此 {@code POST /api/v1/admin/registration-requests/{id}/approve}
     * 能匹配真实 URI {@code .../registration-requests/<uuid>/approve}（t102）。
     */
    private boolean shouldSkipEndpoint(String method, String uri) {
        if (uri == null) {
            return false;
        }
        String path = uri;
        int queryIndex = path.indexOf('?');
        if (queryIndex >= 0) {
            path = path.substring(0, queryIndex);
        }
        String key = method + " " + path;
        for (String skip : SKIP_ENDPOINTS) {
            if (skip.equals(key) || pathMatchesTemplate(key, skip)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 「方法 + 路径」与模板比对：模板中的 {@code {xxx}} 匹配一个非空路径段
     */
    private boolean pathMatchesTemplate(String key, String template) {
        if (!template.contains("{")) {
            return false;
        }
        StringBuilder regex = new StringBuilder();
        for (String part : template.split("\\{[^}]*}", -1)) {
            if (regex.length() > 0) {
                regex.append("[^/]+");
            }
            regex.append(Pattern.quote(part));
        }
        // split 以占位符为界：首段之后每段前都有一个占位符
        String pattern = regex.toString();
        return key.matches(pattern);
    }

    private UUID currentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null
                || "anonymousUser".equals(authentication.getName())) {
            return null;
        }
        return userService.findByUsername(authentication.getName())
                .map(user -> user.getId())
                .orElse(null);
    }

    /**
     * 从 URI 推断资源类型：/api/v1/assets/{id}/files → assets
     */
    private String resolveResourceType(String uri) {
        String path = uri;
        int apiIndex = path.indexOf("/api/v1/");
        if (apiIndex >= 0) {
            path = path.substring(apiIndex + "/api/v1/".length());
        }
        String[] segments = path.split("/");
        for (String segment : segments) {
            if (segment.isBlank() || UUID_PATTERN.matcher(segment).matches()) {
                continue;
            }
            String normalized = segment.toLowerCase(Locale.ROOT);
            // t68：期次挂在 /projects/{id}/phases 下，按「最后一段是否为 phases」识别为 project-phase
            if ("phases".equals(lastMeaningfulSegment(segments))) {
                return "project-phase";
            }
            // t68：客户/项目/期次使用单数（含连字符）资源类型，其余沿用 URI 段原名
            switch (normalized) {
                case "customers" -> { return "customer"; }
                case "projects" -> { return "project"; }
                case "phases" -> { return "project-phase"; }
                default -> { }
            }
            return truncate(normalized, 50);
        }
        return "unknown";
    }

    /**
     * 取 URI 中第一个 UUID 作为资源 ID，没有则返回 null
     */
    /**
     * 取 URI 中最后一个「非空且非 UUID」的段（t68，用于识别 /projects/{id}/phases 这类嵌套资源）
     */
    private String lastMeaningfulSegment(String[] segments) {
        for (int i = segments.length - 1; i >= 0; i--) {
            String segment = segments[i];
            if (!segment.isBlank() && !UUID_PATTERN.matcher(segment).matches()) {
                return segment.toLowerCase(Locale.ROOT);
            }
        }
        return "";
    }
    private UUID resolveResourceId(String uri) {
        Matcher matcher = UUID_PATTERN.matcher(uri);
        if (matcher.find()) {
            try {
                return UUID.fromString(matcher.group());
            } catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
