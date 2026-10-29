package com.vulnaudit.service;

import com.vulnaudit.entity.Permission;
import com.vulnaudit.entity.Role;
import com.vulnaudit.entity.RolePermission;
import com.vulnaudit.repository.PermissionRepository;
import com.vulnaudit.repository.RolePermissionRepository;
import com.vulnaudit.repository.RoleRepository;
import com.vulnaudit.security.PermissionCatalog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * RBAC 基线数据初始化服务。
 *
 * <p>功能说明：
 * <ul>
 *   <li>把 {@link PermissionCatalog} 中的权限点幂等落入 {@code permissions} 表；</li>
 *   <li>补齐四个内置角色（缺失时创建，已存在则保持不动）；</li>
 *   <li>按目录声明的默认集合幂等写入 {@code role_permissions}。</li>
 * </ul>
 *
 * <p><b>为什么用启动初始化器而不是 Flyway 迁移</b>：本项目的生产部署（见
 * {@code deploy/README.md} 第 6 节）以 {@code spring.flyway.enabled=false} +
 * {@code hibernate.ddl-auto=update} 运行，Flyway 脚本不会被执行；同时初始化必须
 * 兼容「表由 Hibernate 生成」的历史库。放在 Java 侧可以同时满足幂等、数据库无关
 * （MariaDB / H2 均可）与可重复执行。
 *
 * <p><b>幂等保证</b>：权限点以 {@code code} 为自然键、角色权限以
 * {@code (role_id, permission_id)} 为自然键，写入前先查后写，重复执行只做「补差」，
 * 不会产生重复行，也不会删除管理员手工调整过的额外授权。
 *
 * <p><b>双轨说明</b>：本类只落地权限数据，不改变任何 {@code @PreAuthorize} 角色判定；
 * 权限点是后续 ABAC（按 API / 资源属性判定）的基础数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PermissionBootstrapService {

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;
    private final RolePermissionRepository rolePermissionRepository;

    /**
     * 初始化结果，用于日志与验收核对。
     *
     * @param permissionCreated      新建权限点数
     * @param permissionUpdated      已存在但字段被刷新（名称/路径/方法/描述）的权限点数
     * @param roleCreated            新建内置角色数
     * @param rolePermissionCreated  新建角色权限关联数
     * @param permissionTotal        执行后权限点总数
     */
    public record BootstrapReport(int permissionCreated,
                                  int permissionUpdated,
                                  int roleCreated,
                                  int rolePermissionCreated,
                                  int permissionTotal) {

        /** 是否为「无任何变更」的空转（用于断言幂等） */
        public boolean noop() {
            return permissionCreated == 0 && permissionUpdated == 0
                    && roleCreated == 0 && rolePermissionCreated == 0;
        }
    }

    /**
     * 幂等初始化权限点、内置角色与角色权限关联。
     *
     * @return 本次执行的实际变更量
     */
    @Transactional
    public BootstrapReport bootstrap() {
        int permissionCreated = syncPermissions();
        int permissionUpdated = syncPermissionDetails();
        int roleCreated = ensureBuiltinRoles();
        int rolePermissionCreated = syncRolePermissions();
        int permissionTotal = (int) permissionRepository.count();

        BootstrapReport report = new BootstrapReport(
                permissionCreated, permissionUpdated, roleCreated, rolePermissionCreated, permissionTotal);

        if (report.noop()) {
            log.info("RBAC 权限点已是最新，无需变更（权限点总数={}）", permissionTotal);
        } else {
            log.info("RBAC 权限点初始化完成: 新增权限点={}, 刷新权限点={}, 新增角色={}, 新增角色权限={}, 权限点总数={}",
                    permissionCreated, permissionUpdated, roleCreated, rolePermissionCreated, permissionTotal);
        }
        return report;
    }

    /** 补建缺失的权限点；返回新建数量 */
    private int syncPermissions() {
        Set<String> existing = new HashSet<>();
        permissionRepository.findAll().forEach(permission -> existing.add(permission.getCode()));

        List<Permission> toCreate = new ArrayList<>();
        for (PermissionCatalog.Definition definition : PermissionCatalog.definitions()) {
            if (existing.contains(definition.code())) {
                continue;
            }
            toCreate.add(Permission.builder()
                    .code(definition.code())
                    .name(definition.name())
                    .apiPath(definition.apiPath())
                    .httpMethod(definition.httpMethod())
                    .description(definition.description())
                    .build());
        }
        if (!toCreate.isEmpty()) {
            permissionRepository.saveAll(toCreate);
        }
        return toCreate.size();
    }

    /** 刷新已存在权限点的展示字段与 API 映射，使其与目录一致；返回刷新数量 */
    private int syncPermissionDetails() {
        Map<String, PermissionCatalog.Definition> catalog = new HashMap<>();
        PermissionCatalog.definitions().forEach(definition -> catalog.put(definition.code(), definition));

        List<Permission> dirty = new ArrayList<>();
        for (Permission permission : permissionRepository.findAll()) {
            PermissionCatalog.Definition definition = catalog.get(permission.getCode());
            if (definition == null) {
                // 非目录内的自定义权限点不参与自动维护
                continue;
            }
            boolean changed = !Objects.equals(permission.getName(), definition.name())
                    || !Objects.equals(permission.getApiPath(), definition.apiPath())
                    || !Objects.equals(permission.getHttpMethod(), definition.httpMethod())
                    || !Objects.equals(permission.getDescription(), definition.description());
            if (changed) {
                permission.setName(definition.name());
                permission.setApiPath(definition.apiPath());
                permission.setHttpMethod(definition.httpMethod());
                permission.setDescription(definition.description());
                dirty.add(permission);
            }
        }
        if (!dirty.isEmpty()) {
            permissionRepository.saveAll(dirty);
        }
        return dirty.size();
    }

    /** 补建缺失的内置角色；返回新建数量 */
    private int ensureBuiltinRoles() {
        int created = 0;
        for (PermissionCatalog.BuiltinRole builtin : PermissionCatalog.BUILTIN_ROLES) {
            if (roleRepository.findByCode(builtin.code()).isPresent()) {
                continue;
            }
            roleRepository.save(Objects.requireNonNull(Role.builder()
                    .code(builtin.code())
                    .name(builtin.name())
                    .description(builtin.description())
                    .isSystem(true)
                    .build()));
            created++;
            log.info("补建内置角色: code={}", builtin.code());
        }
        return created;
    }

    /** 幂等写入角色权限关联；返回新建数量 */
    private int syncRolePermissions() {
        Map<String, UUID> permissionIdByCode = new HashMap<>();
        permissionRepository.findAll().forEach(permission -> permissionIdByCode.put(permission.getCode(), permission.getId()));

        int created = 0;
        for (Map.Entry<String, List<String>> grant : PermissionCatalog.defaultRoleGrants().entrySet()) {
            Role role = roleRepository.findByCode(grant.getKey()).orElse(null);
            if (role == null) {
                log.warn("内置角色不存在，跳过默认权限写入: role={}", grant.getKey());
                continue;
            }
            Set<UUID> granted = new HashSet<>(rolePermissionRepository.findPermissionIdsByRoleId(role.getId()));
            List<RolePermission> toCreate = new ArrayList<>();
            for (String code : grant.getValue()) {
                UUID permissionId = permissionIdByCode.get(code);
                if (permissionId == null) {
                    log.warn("权限点不存在，跳过角色授权: role={}, permission={}", grant.getKey(), code);
                    continue;
                }
                if (granted.add(permissionId)) {
                    toCreate.add(RolePermission.builder()
                            .roleId(role.getId())
                            .permissionId(permissionId)
                            .build());
                }
            }
            if (!toCreate.isEmpty()) {
                rolePermissionRepository.saveAll(toCreate);
                created += toCreate.size();
            }
        }
        return created;
    }
}
