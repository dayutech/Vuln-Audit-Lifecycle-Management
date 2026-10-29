package com.vulnaudit.service;

import com.vulnaudit.controller.BaseTest;
import com.vulnaudit.entity.Permission;
import com.vulnaudit.entity.Role;
import com.vulnaudit.entity.RolePermission;
import com.vulnaudit.repository.PermissionRepository;
import com.vulnaudit.repository.RolePermissionRepository;
import com.vulnaudit.repository.RoleRepository;
import com.vulnaudit.security.PermissionCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * RBAC 基线数据「补差」能力测试（t1 服务层补充覆盖）。
 *
 * <p>既有 {@code RbacPermissionTest} 覆盖的是「空库初始化」与「幂等重跑」，
 * 这里补的是真实运维会遇到的**被破坏后的自愈**场景：
 * <ol>
 *   <li>目录内权限点的展示字段/接口映射被改坏 → 重新初始化按目录刷新；</li>
 *   <li>内置角色的权限关联被误删一部分 → 重新初始化按默认集合补齐；</li>
 *   <li>目录外的自定义权限点不被删除、也不被改写（避免误伤管理员手工扩展）；</li>
 *   <li>内置角色被删除 → 重新初始化重建，并重新挂上默认权限。</li>
 * </ol>
 */
@DisplayName("RBAC 基线补差自愈测试")
public class PermissionBootstrapRepairTest extends BaseTest {

    @Autowired
    private PermissionBootstrapService permissionBootstrapService;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @BeforeEach
    void bootstrapBaseline() {
        permissionBootstrapService.bootstrap();
    }

    private PermissionCatalog.Definition definition(String code) {
        return PermissionCatalog.definitions().stream()
                .filter(item -> item.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("目录中不存在权限点: " + code));
    }

    private long duplicateRolePermissionRows() {
        Object single = entityManager.createNativeQuery(
                        "SELECT COUNT(*) FROM (SELECT role_id, permission_id FROM role_permissions "
                                + "GROUP BY role_id, permission_id HAVING COUNT(*) > 1) dup")
                .getSingleResult();
        return ((Number) single).longValue();
    }

    // ------------------------------------------------------------------ 字段刷新

    @Test
    @DisplayName("权限点展示字段被改坏时按目录刷新，且不新增/删除权限点")
    void bootstrap_refreshesTamperedPermissionDetails() {
        PermissionCatalog.Definition expected = definition("asset:read");
        Permission tampered = permissionRepository.findByCode("asset:read").orElseThrow();
        tampered.setName("被篡改的名称");
        tampered.setApiPath("/api/v9/not-exist");
        tampered.setHttpMethod("DELETE");
        tampered.setDescription("被篡改的描述");
        permissionRepository.save(tampered);
        entityManager.flush();

        long totalBefore = permissionRepository.count();
        PermissionBootstrapService.BootstrapReport report = permissionBootstrapService.bootstrap();

        assertEquals(1, report.permissionUpdated(), "应只刷新被篡改的那一个权限点");
        assertEquals(0, report.permissionCreated(), "权限点已齐全，不应新增");
        assertEquals(totalBefore, report.permissionTotal(), "总数不应变化");

        Permission repaired = permissionRepository.findByCode("asset:read").orElseThrow();
        assertEquals(expected.name(), repaired.getName());
        assertEquals(expected.apiPath(), repaired.getApiPath());
        assertEquals(expected.httpMethod(), repaired.getHttpMethod());
        assertEquals(expected.description(), repaired.getDescription());
        assertEquals(0, duplicateRolePermissionRows(), "刷新字段不应产生重复角色权限");
    }

    // ------------------------------------------------------------------ 关联补齐

    @Test
    @DisplayName("内置角色的权限关联被误删后按默认集合补齐（不重复、不越界）")
    void bootstrap_restoresRemovedRolePermission() {
        Role adminRole = roleRepository.findByCode("admin").orElseThrow();
        List<UUID> granted = rolePermissionRepository.findPermissionIdsByRoleId(adminRole.getId());
        assertEquals(PermissionCatalog.size(), granted.size(), "admin 初始应关联全部权限点");

        UUID removedPermissionId = granted.get(0);
        RolePermission row = rolePermissionRepository.findAll().stream()
                .filter(item -> adminRole.getId().equals(item.getRoleId())
                        && removedPermissionId.equals(item.getPermissionId()))
                .findFirst()
                .orElseThrow();
        rolePermissionRepository.delete(row);
        entityManager.flush();
        assertEquals(granted.size() - 1,
                rolePermissionRepository.findPermissionIdsByRoleId(adminRole.getId()).size(),
                "删除后应缺一项");

        PermissionBootstrapService.BootstrapReport report = permissionBootstrapService.bootstrap();

        assertEquals(1, report.rolePermissionCreated(), "应只补回被删的那一条关联");
        Set<UUID> after = rolePermissionRepository.findPermissionIdsByRoleId(adminRole.getId())
                .stream().collect(Collectors.toSet());
        assertTrue(after.contains(removedPermissionId), "被删的权限关联应被补回");
        assertEquals(PermissionCatalog.size(), after.size(), "admin 应恢复为全量权限");
        assertEquals(0, duplicateRolePermissionRows(), "补差不应产生重复组合");
    }

    @Test
    @DisplayName("重复初始化不会把管理员手工追加的额外授权删掉")
    void bootstrap_keepsExtraManualGrant() {
        Role engineerRole = roleRepository.findByCode("vulnerability_engineer").orElseThrow();
        Permission readLog = permissionRepository.findByCode("audit:read").orElseThrow();
        Set<UUID> before = rolePermissionRepository.findPermissionIdsByRoleId(engineerRole.getId())
                .stream().collect(Collectors.toSet());

        if (!before.contains(readLog.getId())) {
            rolePermissionRepository.save(Objects.requireNonNull(RolePermission.builder()
                    .roleId(engineerRole.getId())
                    .permissionId(readLog.getId())
                    .build()));
            entityManager.flush();
        }

        permissionBootstrapService.bootstrap();

        Set<UUID> after = rolePermissionRepository.findPermissionIdsByRoleId(engineerRole.getId())
                .stream().collect(Collectors.toSet());
        assertTrue(after.contains(readLog.getId()), "额外的手工授权不应被初始化逻辑撤销");
        assertTrue(after.containsAll(before), "默认集合也应保持完整");
    }

    // ------------------------------------------------------------------ 目录外权限点

    @Test
    @DisplayName("目录外的自定义权限点不被删除或改写，且初始化仍为空转")
    void bootstrap_preservesCustomPermission() {
        long baseTotal = permissionRepository.count();

        permissionRepository.save(Objects.requireNonNull(Permission.builder()
                .code("custom:demo")
                .name("自定义权限")
                .apiPath("/api/v1/custom-demo")
                .httpMethod("GET")
                .description("不在目录内的扩展权限")
                .build()));
        entityManager.flush();

        PermissionBootstrapService.BootstrapReport report = permissionBootstrapService.bootstrap();

        assertTrue(report.noop(), "自定义权限点不参与维护，因此这次初始化应为空转");
        assertEquals(baseTotal + 1, permissionRepository.count(), "自定义权限点不应被删除");
        Permission custom = permissionRepository.findByCode("custom:demo").orElseThrow();
        assertEquals("自定义权限", custom.getName());
        assertEquals("/api/v1/custom-demo", custom.getApiPath());
        assertFalse(PermissionCatalog.allCodes().contains("custom:demo"), "目录内不应包含运行时自定义权限");
    }

    // ------------------------------------------------------------------ 角色重建

    @Test
    @DisplayName("内置角色被删除后重建，并重新挂上默认权限集合")
    void bootstrap_recreatesDeletedBuiltinRole() {
        Role leader = roleRepository.findByCode("project_leader").orElseThrow();
        rolePermissionRepository.deleteAll(rolePermissionRepository.findAll().stream()
                .filter(item -> leader.getId().equals(item.getRoleId()))
                .toList());
        roleRepository.delete(leader);
        entityManager.flush();
        assertTrue(roleRepository.findByCode("project_leader").isEmpty(), "前置：角色应已删除");

        PermissionBootstrapService.BootstrapReport report = permissionBootstrapService.bootstrap();

        assertTrue(report.roleCreated() >= 1, "应重建缺失的内置角色");
        Role recreated = roleRepository.findByCode("project_leader").orElseThrow();
        assertTrue(Boolean.TRUE.equals(recreated.getIsSystem()), "重建的内置角色应标记为系统角色");

        int expectedGrants = PermissionCatalog.defaultRoleGrants().get("project_leader").size();
        assertEquals(expectedGrants,
                rolePermissionRepository.findPermissionIdsByRoleId(recreated.getId()).size(),
                "重建角色应按目录默认集合重新授权");
        assertEquals(0, duplicateRolePermissionRows(), "重建后不应出现重复关联");
    }
}
