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
    private static final String[] SKIP_CONTROLLERS = {"TaskController", "ImportController"};

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
        try {
            UUID userId = currentUserId();
            if (userId == null) {
                return;
            }
            String action = method + " " + controller + "." + handlerMethod.getMethod().getName();
            String resourceType = resolveResourceType(request.getRequestURI());
            UUID resourceId = resolveResourceId(request.getRequestURI());
            String detail = request.getRequestURI();
            auditLogService.log(userId, truncate(action, 50), resourceType, resourceId,
                    truncate(detail, 500), request.getRemoteAddr());
        } catch (Exception e) {
            // 审计失败不能影响主流程
            log.warn("写操作审计记录失败: {} {}", method, request.getRequestURI(), e);
        }
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
            return truncate(segment.toLowerCase(Locale.ROOT), 50);
        }
        return "unknown";
    }

    /**
     * 取 URI 中第一个 UUID 作为资源 ID，没有则返回 null
     */
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
