package com.vulnaudit.controller;

import com.vulnaudit.config.RbacBootstrapRunner;
import com.vulnaudit.entity.Permission;
import com.vulnaudit.entity.Role;
import com.vulnaudit.entity.UserPermission;
import com.vulnaudit.entity.UserRole;
import com.vulnaudit.repository.PermissionRepository;
import com.vulnaudit.repository.RolePermissionRepository;
import com.vulnaudit.repository.RoleRepository;
import com.vulnaudit.repository.UserPermissionRepository;
import com.vulnaudit.repository.UserRoleRepository;
import com.vulnaudit.security.PermissionCatalog;
import com.vulnaudit.service.PermissionBootstrapService;
import com.vulnaudit.service.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.http.MediaType;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RBAC 权限点体系测试。
 *
 * <p>覆盖 t1 的验收口径：
 * <ol>
 *   <li>{@code GET /api/v1/permissions} 非空；</li>
 *   <li>admin 的 {@code /users/me/permissions} 至少包含 10 个权限码；</li>
 *   <li>重复初始化不产生重复行；</li>
 *   <li>角色/权限分配的幂等性与存在性校验；</li>
 *   <li>直接授权按 {@code expires_at} 生效、过期即失效。</li>
 * </ol>
 *
 * <p>测试环境把 {@code app.rbac.bootstrap-enabled} 置 false（见 application-test.yml），
 * 因此这里显式调用 {@link PermissionBootstrapService#bootstrap()} 以精确控制初始化时机。
 */
@DisplayName("RBAC 权限点体系测试")
public class RbacPermissionTest extends BaseTest {

    @Autowired
    private PermissionBootstrapService permissionBootstrapService;

    @Autowired
    private PermissionService permissionService;

    @Autowired
    private com.vulnaudit.service.RoleService roleService;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private RolePermissionRepository rolePermissionRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private UserPermissionRepository userPermissionRepository;

    private Role adminRole;
    private Role projectLeaderRole;

    @BeforeEach
    void bootstrapRbac() {
        // BaseTest 已清库并重建 admin/team_leader/vulnerability_engineer 三个角色与用户
        permissionBootstrapService.bootstrap();
        adminRole = roleRepository.findByCode("admin").orElseThrow();
        projectLeaderRole = roleRepository.findByCode("project_leader").orElseThrow();
    }

    private long rolePermissionCount(UUID roleId) {
        return rolePermissionRepository.findPermissionIdsByRoleId(roleId).size();
    }

    private long duplicateRolePermissionRows() {
        Object single = entityManager.createNativeQuery(
                        "SELECT COUNT(*) FROM (SELECT role_id, permission_id FROM role_permissions "
                                + "GROUP BY role_id, permission_id HAVING COUNT(*) > 1) dup")
                .getSingleResult();
        return ((Number) single).longValue();
    }

    private long duplicatePermissionCodes() {
        Object single = entityManager.createNativeQuery(
                        "SELECT COUNT(*) FROM (SELECT code FROM permissions "
                                + "GROUP BY code HAVING COUNT(*) > 1) dup")
                .getSingleResult();
        return ((Number) single).longValue();
    }

    private Permission permissionByCode(String code) {
        return permissionRepository.findByCode(code).orElseThrow();
    }

    // ------------------------------------------------------------------ 初始化

    @Test
    @DisplayName("初始化生成全部权限点并挂到 admin 角色")
    void bootstrap_createsPermissionsAndGrantsAdmin() throws Exception {
        assertEquals(PermissionCatalog.size(), permissionRepository.count(),
                "权限点数量应等于目录定义数量");

        assertEquals(PermissionCatalog.size(), rolePermissionCount(adminRole.getId()),
                "admin 角色应关联全部权限点");

        Role teamLeader = roleRepository.findByCode("team_leader").orElseThrow();
        Role engineer = roleRepository.findByCode("vulnerability_engineer").orElseThrow();
        assertTrue(rolePermissionCount(projectLeaderRole.getId()) > 0, "project_leader 应有默认权限");
        assertTrue(rolePermissionCount(teamLeader.getId()) > 0, "team_leader 应有默认权限");
        assertTrue(rolePermissionCount(engineer.getId()) > 0, "vulnerability_engineer 应有默认权限");

        // GET /permissions 非空且包含关键权限码（验收项 1）
        mockMvc.perform(get("/api/v1/permissions")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isNotEmpty())
                .andExpect(jsonPath("$.data[*].code", hasItem("asset:read")))
                .andExpect(jsonPath("$.data[*].code", hasItem("vulnerability:read")))
                .andExpect(jsonPath("$.data[*].code", hasItem("user:manage")));
    }

    @Test
    @DisplayName("重复初始化幂等：不产生重复行")
    void bootstrap_isIdempotent() {
        long permissionsBefore = permissionRepository.count();
        List<UUID> adminPermissionsBefore = rolePermissionRepository.findPermissionIdsByRoleId(adminRole.getId());
        long totalRolePermissionsBefore = rolePermissionRepository.count();

        PermissionBootstrapService.BootstrapReport second = permissionBootstrapService.bootstrap();
        PermissionBootstrapService.BootstrapReport third = permissionBootstrapService.bootstrap();

        assertTrue(second.noop(), "第二次初始化不应产生任何变更");
        assertTrue(third.noop(), "第三次初始化不应产生任何变更");
        assertEquals(permissionsBefore, permissionRepository.count(), "权限点数量不应变化");
        assertEquals(adminPermissionsBefore.size(),
                rolePermissionRepository.findPermissionIdsByRoleId(adminRole.getId()).size(),
                "admin 角色权限数量不应变化");
        assertEquals(totalRolePermissionsBefore, rolePermissionRepository.count(), "角色权限关联总数不应变化");
        assertEquals(0, duplicatePermissionCodes(), "permissions.code 不应出现重复");
        assertEquals(0, duplicateRolePermissionRows(), "role_permissions 不应出现重复组合");
    }

    @Test
    @DisplayName("启动初始化器可直接驱动初始化且幂等")
    void bootstrapRunner_runsAndIsIdempotent() {
        rolePermissionRepository.deleteAll();
        permissionRepository.deleteAll();
        // 显式 flush：Hibernate 先执行 INSERT 再执行 DELETE，不清空会触发 code 唯一键冲突
        entityManager.flush();
        adminRole = roleRepository.findByCode("admin").orElseThrow();

        RbacBootstrapRunner runner = new RbacBootstrapRunner(permissionBootstrapService);
        runner.run(new DefaultApplicationArguments(new String[0]));
        assertEquals(PermissionCatalog.size(), permissionRepository.count(), "Runner 应补齐全部权限点");

        runner.run(new DefaultApplicationArguments(new String[0]));
        assertEquals(PermissionCatalog.size(), permissionRepository.count(), "重复执行不应产生重复权限点");
        assertEquals(0, duplicateRolePermissionRows(), "重复执行不应产生重复角色权限");
    }

    @Test
    @DisplayName("权限点目录的 (apiPath, httpMethod) 唯一，兼容按接口反查")
    void catalog_apiMappingIsUnique() {
        Map<String, Long> duplicated = PermissionCatalog.definitions().stream()
                .collect(Collectors.groupingBy(
                        def -> def.httpMethod() + " " + def.apiPath(),
                        Collectors.counting()))
                .entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        assertTrue(duplicated.isEmpty(), "存在重复的接口映射: " + duplicated);
    }

    @Test
    @DisplayName("角色默认授权引用的权限编码全部存在")
    void catalog_defaultGrantsReferenceExistingCodes() {
        Set<String> allCodes = PermissionCatalog.allCodes();
        Set<String> referenced = new HashSet<>();
        PermissionCatalog.defaultRoleGrants().values().forEach(referenced::addAll);

        Set<String> unknown = referenced.stream()
                .filter(code -> !allCodes.contains(code))
                .collect(Collectors.toSet());
        assertTrue(unknown.isEmpty(), "角色默认授权引用了未定义的权限编码: " + unknown);
    }

    // ------------------------------------------------------------------ 有效权限

    @Test
    @DisplayName("admin 的 me/permissions 覆盖全部权限点（≥10 个）")
    void mePermissions_adminHasFullCatalog() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/permissions")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissionCount").value(PermissionCatalog.size()))
                .andExpect(jsonPath("$.data.permissions").isArray())
                .andExpect(jsonPath("$.data.permissions", hasItem("asset:read")))
                .andExpect(jsonPath("$.data.permissions", hasItem("vulnerability:read")))
                .andExpect(jsonPath("$.data.permissions", hasItem("user:manage")));

        List<String> codes = permissionService.getUserPermissionCodes(adminUserId);
        assertTrue(codes.size() >= 10, "admin 权限码应不少于 10 个，实际 " + codes.size());
        assertTrue(codes.contains("asset:read"));
        // 排序稳定，便于前端与断言比对
        assertEquals(codes.stream().sorted().toList(), codes);
    }

    @Test
    @DisplayName("工程师的权限仅限角色默认集合，不含账号管理类权限")
    void mePermissions_engineerScoped() throws Exception {
        List<String> codes = permissionService.getUserPermissionCodes(engineerUserId);
        assertFalse(codes.isEmpty(), "工程师应至少拥有角色默认权限");
        assertTrue(codes.contains("vulnerability:read"));
        assertTrue(codes.contains("asset:access_request"));
        assertFalse(codes.contains("user:manage"), "工程师不应拥有用户管理权限");
        assertFalse(codes.contains("permission:read"), "工程师不应拥有权限点查看权限");

        mockMvc.perform(get("/api/v1/users/me/permissions")
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions", not(hasItem("user:manage"))));
    }

    @Test
    @DisplayName("未初始化权限点时工程师权限为空（基线缺陷复现，说明初始化必要性）")
    void mePermissions_emptyBeforeBootstrap() throws Exception {
        rolePermissionRepository.deleteAll();

        mockMvc.perform(get("/api/v1/users/me/permissions")
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissionCount").value(0));
    }

    // ------------------------------------------------------------------ 角色分配

    @Test
    @DisplayName("重复分配同一角色不产生重复行")
    void assignRoleToUser_isIdempotent() throws Exception {
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/users/" + engineerUserId + "/roles")
                            .header("Authorization", adminToken)
                            .param("roleId", projectLeaderRole.getId().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        List<UserRole> links = userRoleRepository.findByUserId(engineerUserId);
        long matches = links.stream()
                .filter(link -> projectLeaderRole.getId().equals(link.getRoleId()))
                .count();
        assertEquals(1, matches, "同一用户同一角色只应有一条关联");
        // assigned_by 必须落库（此前 RoleService 漏写导致 NOT NULL 约束失败）
        UserRole link = links.stream()
                .filter(item -> projectLeaderRole.getId().equals(item.getRoleId()))
                .findFirst()
                .orElseThrow();
        assertNotNull(link.getAssignedBy(), "分配人 assigned_by 不能为空");
    }

    @Test
    @DisplayName("角色不存在返回 404")
    void assignRoleToUser_unknownRoleReturns404() throws Exception {
        mockMvc.perform(post("/api/v1/users/" + engineerUserId + "/roles")
                        .header("Authorization", adminToken)
                        .param("roleId", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("用户不存在返回 404")
    void assignRoleToUser_unknownUserReturns404() throws Exception {
        mockMvc.perform(post("/api/v1/users/" + UUID.randomUUID() + "/roles")
                        .header("Authorization", adminToken)
                        .param("roleId", projectLeaderRole.getId().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("重复撤销角色幂等：不报错")
    void removeRoleFromUser_isIdempotent() throws Exception {
        mockMvc.perform(post("/api/v1/users/" + engineerUserId + "/roles")
                        .header("Authorization", adminToken)
                        .param("roleId", projectLeaderRole.getId().toString()))
                .andExpect(status().isOk());

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(delete("/api/v1/users/" + engineerUserId + "/roles/" + projectLeaderRole.getId())
                            .header("Authorization", adminToken))
                    .andExpect(status().isOk());
        }

        long remaining = userRoleRepository.findByUserId(engineerUserId).stream()
                .filter(link -> projectLeaderRole.getId().equals(link.getRoleId()))
                .count();
        assertEquals(0, remaining, "角色关联应已删除");
    }

    // ------------------------------------------------------------------ 直接授权

    @Test
    @DisplayName("重复直接授权幂等且立即生效")
    void assignPermissionToUser_isIdempotent() throws Exception {
        Permission permission = permissionByCode("permission:read");

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/users/" + engineerUserId + "/permissions")
                            .header("Authorization", adminToken)
                            .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                            .param("permissionId", permission.getId().toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        long rows = userPermissionRepository.findByUserId(engineerUserId).stream()
                .filter(item -> permission.getId().equals(item.getPermissionId()))
                .count();
        assertEquals(1, rows, "同一用户同一权限只应有一条直接授权");
        assertTrue(permissionService.getUserPermissionCodes(engineerUserId).contains("permission:read"),
                "直接授权应计入有效权限");
    }

    @Test
    @DisplayName("权限点不存在返回 404")
    void assignPermissionToUser_unknownPermissionReturns404() throws Exception {
        mockMvc.perform(post("/api/v1/users/" + engineerUserId + "/permissions")
                        .header("Authorization", adminToken)
                        .param("permissionId", UUID.randomUUID().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("已过期的直接授权不计入有效权限，重新授权后恢复")
    void expiredDirectPermission_isExcluded() {
        Permission permission = permissionByCode("permission:read");

        permissionService.assignPermissionToUser(engineerUserId, permission.getId(), adminUserId,
                LocalDateTime.now().minusHours(1));
        Set<String> codes = new HashSet<>(permissionService.getUserPermissionCodes(engineerUserId));
        assertFalse(codes.contains("permission:read"), "已过期授权不应计入有效权限");

        boolean changed = permissionService.assignPermissionToUser(engineerUserId, permission.getId(),
                adminUserId, LocalDateTime.now().plusHours(1));
        assertTrue(changed, "过期授权重新授权应被视为变更");
        assertTrue(permissionService.getUserPermissionCodes(engineerUserId).contains("permission:read"),
                "重新授权后应恢复生效");

        boolean second = permissionService.assignPermissionToUser(engineerUserId, permission.getId(),
                adminUserId, LocalDateTime.now().plusDays(30));
        assertFalse(second, "已生效的直接授权不应被重复写入");
        List<UserPermission> rows = userPermissionRepository.findByUserId(engineerUserId).stream()
                .filter(item -> permission.getId().equals(item.getPermissionId()))
                .toList();
        assertEquals(1, rows.size(), "直接授权仍应只有一行");
    }

    @Test
    @DisplayName("重复撤销直接授权幂等：不报错")
    void removePermissionFromUser_isIdempotent() throws Exception {
        Permission permission = permissionByCode("permission:read");
        mockMvc.perform(post("/api/v1/users/" + engineerUserId + "/permissions")
                        .header("Authorization", adminToken)
                        .param("permissionId", permission.getId().toString()))
                .andExpect(status().isOk());

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(delete("/api/v1/users/" + engineerUserId + "/permissions/" + permission.getId())
                            .header("Authorization", adminToken))
                    .andExpect(status().isOk());
        }

        assertFalse(permissionService.getUserPermissionCodes(engineerUserId).contains("permission:read"));
    }

    // ------------------------------------------------------------------ 回显与准入

    @Test
    @DisplayName("GET /roles/{id}/permissions 回显已分配权限点")
    void getRolePermissions_returnsAssignedPoints() throws Exception {
        mockMvc.perform(get("/api/v1/roles/" + adminRole.getId() + "/permissions")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(PermissionCatalog.size()));

        assertEquals(PermissionCatalog.size(),
                permissionService.getUserPermissionCodes(adminUserId).size(),
                "admin 有效权限应覆盖全部权限点");
    }

    @Test
    @DisplayName("GET /roles?includePermissions=true 返回每个角色的权限点")
    void listRoles_includePermissions() throws Exception {
        String response = mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", adminToken)
                        .param("includePermissions", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.JsonNode roles = objectMapper.readTree(response).path("data");
        boolean adminHasAssetRead = false;
        for (com.fasterxml.jackson.databind.JsonNode role : roles) {
            if (!"admin".equals(role.path("code").asText())) {
                continue;
            }
            for (com.fasterxml.jackson.databind.JsonNode permission : role.path("permissions")) {
                if ("asset:read".equals(permission.path("code").asText())) {
                    adminHasAssetRead = true;
                }
            }
        }
        assertTrue(adminHasAssetRead, "includePermissions=true 时 admin 角色应带上 asset:read");

        // 不传参数时保持原响应结构（不附带 permissions 字段）
        mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].permissions").doesNotExist());
    }

    @Test
    @DisplayName("创建角色可一并分配权限点")
    void createRole_withPermissionIds() throws Exception {
        Permission read = permissionByCode("asset:read");
        Permission write = permissionByCode("asset:write");
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "测试角色",
                "code", "test_role_rbac",
                "description", "RBAC 用例角色",
                "permission_ids", java.util.List.of(read.getId().toString(), write.getId().toString())));

        String response = mockMvc.perform(post("/api/v1/roles")
                        .header("Authorization", adminToken)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andReturn().getResponse().getContentAsString();

        String roleId = objectMapper.readTree(response).path("data").path("id").asText();
        Role created = roleRepository.findById(UUID.fromString(roleId)).orElseThrow();

        assertEquals(2, rolePermissionCount(created.getId()), "创建时应写入 2 条角色权限");
        assertEquals(List.of("asset:read", "asset:write"),
                roleServiceCodes(created.getId()), "权限点应与提交的一致");
    }

    @Test
    @DisplayName("更新角色按目标集合覆盖式同步权限点且幂等")
    void updateRole_syncsPermissions() throws Exception {
        Permission read = permissionByCode("asset:read");
        Permission write = permissionByCode("asset:write");
        Permission del = permissionByCode("asset:delete");

        Role role = roleRepository.findByCode("test_role_sync").orElseGet(() -> roleRepository.save(
                Objects.requireNonNull(Role.builder()
                        .name("同步测试角色")
                        .code("test_role_sync")
                        .description("用于验证覆盖式同步")
                        .isSystem(false)
                        .build())));
        roleService.syncPermissions(role.getId(), List.of(read.getId(), write.getId()));
        assertEquals(2, rolePermissionCount(role.getId()));

        // 目标集合改为 write + delete：read 被移除、delete 被补上
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "同步测试角色",
                "permissionIds", List.of(write.getId().toString(), del.getId().toString())));
        mockMvc.perform(put("/api/v1/roles/" + role.getId())
                        .header("Authorization", adminToken)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions.length()").value(2));

        assertEquals(List.of("asset:delete", "asset:write"), roleServiceCodes(role.getId()),
                "覆盖式同步后应只剩目标集合");

        // 幂等：同一目标集合再次提交不改变结果
        mockMvc.perform(put("/api/v1/roles/" + role.getId())
                        .header("Authorization", adminToken)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(body))
                .andExpect(status().isOk());
        assertEquals(2, rolePermissionCount(role.getId()), "重复同步不应产生额外关联");
    }

    @Test
    @DisplayName("覆盖式同步包含不存在的权限点时整体失败")
    void syncPermissions_unknownPermissionFails() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "同步测试角色",
                "permissionIds", List.of(UUID.randomUUID().toString())));

        mockMvc.perform(put("/api/v1/roles/" + projectLeaderRole.getId())
                        .header("Authorization", adminToken)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(body))
                .andExpect(status().isNotFound());

        // 失败不应破坏原有授权
        assertEquals(PermissionCatalog.defaultRoleGrants().get("project_leader").size(),
                rolePermissionCount(projectLeaderRole.getId()));
    }

    @Test
    @DisplayName("工程师访问权限点/角色接口返回 403（权限矩阵 2.11）")
    void permissionEndpoints_forbiddenForEngineer() throws Exception {
        mockMvc.perform(get("/api/v1/permissions")
                        .header("Authorization", engineerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", engineerToken))
                .andExpect(status().isForbidden());
    }

    private List<String> roleServiceCodes(UUID roleId) {
        return rolePermissionRepository.findPermissionIdsByRoleId(roleId).stream()
                .map(permissionRepository::findById)
                .filter(java.util.Optional::isPresent)
                .map(item -> item.orElseThrow().getCode())
                .sorted()
                .toList();
    }
}
