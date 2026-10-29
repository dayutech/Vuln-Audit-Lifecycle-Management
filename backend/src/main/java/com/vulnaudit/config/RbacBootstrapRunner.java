package com.vulnaudit.config;

import com.vulnaudit.service.PermissionBootstrapService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * RBAC 权限点启动初始化器。
 *
 * <p>在 Spring 容器就绪（Hibernate 已完成 {@code ddl-auto=update} 建表）后执行
 * {@link PermissionBootstrapService#bootstrap()}，把 {@code permissions} /
 * {@code role_permissions} 的基线数据补齐。生产库此前这两张表均为空，
 * 导致 {@code GET /api/v1/users/me/permissions} 恒返回空集合。
 *
 * <p>行为约定：
 * <ul>
 *   <li>可通过 {@code app.rbac.bootstrap-enabled=false} 关闭（测试环境默认关闭，
 *       避免污染用例数据）；</li>
 *   <li>幂等：重复启动只补差，不产生重复行；</li>
 *   <li>失败不阻断启动——权限点属于增强能力，接口仍按角色轨（@PreAuthorize）鉴权，
 *       因此异常只记 ERROR 日志。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.rbac.bootstrap-enabled", havingValue = "true", matchIfMissing = true)
public class RbacBootstrapRunner implements ApplicationRunner {

    private final PermissionBootstrapService permissionBootstrapService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            PermissionBootstrapService.BootstrapReport report = permissionBootstrapService.bootstrap();
            log.info("RBAC 启动初始化: 权限点总数={}, 本次新增权限点={}, 新增角色权限={}",
                    report.permissionTotal(), report.permissionCreated(), report.rolePermissionCreated());
        } catch (Exception ex) {
            log.error("RBAC 启动初始化失败，接口继续按角色轨鉴权", ex);
        }
    }
}
