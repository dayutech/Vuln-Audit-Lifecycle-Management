package com.vulnaudit.controller;

import com.vulnaudit.entity.Role;
import com.vulnaudit.entity.User;
import com.vulnaudit.entity.UserRole;
import com.vulnaudit.repository.RoleRepository;
import com.vulnaudit.repository.UserRepository;
import com.vulnaudit.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Objects;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户端点 PII 收敛与 401/403 语义测试（t45）。
 *
 * <p>背景（验收报告 §2.3.2 第 13 组）：{@code GET /users}、{@code /users/{id}}、{@code /{id}/roles}、
 * {@code check-*} 此前仅需登录即可读，构成 PII 枚举面（邮箱、手机号、角色、最后登录时间可被批量拉取），
 * 而权限矩阵 2.2 规定工程师不可读用户管理数据。
 *
 * <p>本用例验证四件事：
 * <ol>
 *   <li>管理视角收紧：工程师访问列表/详情/角色/存在性检查 → 403；admin/project_leader → 200；</li>
 *   <li>最小暴露目录：{@code GET /users/directory} 对所有登录用户开放，但只含 id/username/fullName，
 *       不含 email/phone/roles/status/lastLoginAt/passwordHash；</li>
 *   <li>不锁死自己：{@code /users/me/permissions} 与 {@code /auth/me} 对工程师仍 200；</li>
 *   <li>未认证语义：未携带凭证 → <b>401</b>（不再统一 403），已认证但无权限仍是 403。</li>
 * </ol>
 */
@DisplayName("用户端点 PII 收敛与 401/403 语义测试")
public class UserDirectoryAuthorizationTest extends BaseTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String projectLeaderToken;

    @BeforeEach
    void setUpProjectLeader() {
        String suffix = String.valueOf(System.currentTimeMillis());

        Role projectLeaderRole = roleRepository.findByCode("project_leader")
                .orElseGet(() -> roleRepository.save(Objects.requireNonNull(Role.builder()
                        .name("项目负责人")
                        .code("project_leader")
                        .description("用户目录授权用例")
                        .isSystem(true)
                        .build())));

        User projectLeader = userRepository.save(Objects.requireNonNull(User.builder()
                .username("pii_pl_" + suffix)
                .passwordHash(passwordEncoder.encode("plPass123"))
                .email("pii_pl_" + suffix + "@test.com")
                .fullName("PII 项目负责人")
                .phone("13800000001")
                .status("active")
                .build()));

        userRoleRepository.save(Objects.requireNonNull(UserRole.builder()
                .userId(projectLeader.getId())
                .roleId(projectLeaderRole.getId())
                .assignedBy(adminUserId)
                .assignedAt(LocalDateTime.now())
                .build()));

        projectLeaderToken = Objects.requireNonNull(
                generateTestToken(projectLeader.getUsername(), "ROLE_PROJECT_LEADER"));
    }

    // ------------------------------------------------------------------ 管理视角收紧

    @Test
    @DisplayName("工程师访问用户管理数据 → 全部 403（列表/详情/角色/check-*）")
    void userManagementReads_forbiddenForEngineer() throws Exception {
        mockMvc.perform(get("/api/v1/users").header("Authorization", engineerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/users/" + engineerUserId).header("Authorization", engineerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/users/" + engineerUserId + "/roles")
                        .header("Authorization", engineerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/users/check-username")
                        .header("Authorization", engineerToken)
                        .param("username", "admin"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/users/check-email")
                        .header("Authorization", engineerToken)
                        .param("email", "admin@test.com"))
                .andExpect(status().isForbidden());

        // team_leader 也不在管理视角内（矩阵 2.2 仅 admin/project_leader 可读用户管理数据）
        mockMvc.perform(get("/api/v1/users").header("Authorization", leaderToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("admin / project_leader 访问用户管理数据 → 200，且不含 passwordHash")
    void userManagementReads_allowedForManagers() throws Exception {
        mockMvc.perform(get("/api/v1/users").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());

        mockMvc.perform(get("/api/v1/users").header("Authorization", projectLeaderToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/users/" + engineerUserId).header("Authorization", adminToken))
                .andExpect(status().isOk())
                // t22 的字段级兜底回归：hash 永不出现在响应里
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        mockMvc.perform(get("/api/v1/users/" + engineerUserId + "/roles")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk());

        User admin = userRepository.findById(adminUserId).orElseThrow();

        mockMvc.perform(get("/api/v1/users/check-username")
                        .header("Authorization", adminToken)
                        .param("username", admin.getUsername()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(get("/api/v1/users/check-email")
                        .header("Authorization", adminToken)
                        .param("email", admin.getEmail()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));

        mockMvc.perform(get("/api/v1/users/check-username")
                        .header("Authorization", projectLeaderToken)
                        .param("username", admin.getUsername()))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ 最小暴露目录

    @Test
    @DisplayName("工程师读用户目录 → 200，且只含 id/username/fullName")
    void directory_openToEngineer_withMinimalFields() throws Exception {
        mockMvc.perform(get("/api/v1/users/directory").header("Authorization", engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].id").exists())
                .andExpect(jsonPath("$.data[0].username").exists())
                .andExpect(jsonPath("$.data[*]", everyItem(not(hasKey("email")))))
                .andExpect(jsonPath("$.data[*]", everyItem(not(hasKey("phone")))))
                .andExpect(jsonPath("$.data[*]", everyItem(not(hasKey("roles")))))
                .andExpect(jsonPath("$.data[*]", everyItem(not(hasKey("status")))))
                .andExpect(jsonPath("$.data[*]", everyItem(not(hasKey("lastLoginAt")))))
                .andExpect(jsonPath("$.data[*]", everyItem(not(hasKey("passwordHash")))));
    }

    @Test
    @DisplayName("用户目录支持关键词过滤与条数上限，且团队长/管理员同样可用")
    void directory_supportsKeywordAndLimit() throws Exception {
        mockMvc.perform(get("/api/v1/users/directory")
                        .header("Authorization", leaderToken)
                        .param("keyword", "admin")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(get("/api/v1/users/directory").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    // ------------------------------------------------------------------ 不锁死自己

    @Test
    @DisplayName("工程师读自己的权限与信息 → 200（不能把自己锁死）")
    void selfEndpoints_openToEngineer() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/permissions").header("Authorization", engineerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
    }

    // ------------------------------------------------------------------ 401 / 403 语义

    @Test
    @DisplayName("未认证请求 → 401（不是 403）")
    void unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/users/directory"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/assets"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/users/me/permissions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("已认证但无权限 → 仍然是 403（与 401 区分）")
    void authenticatedWithoutPermission_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/users").header("Authorization", engineerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("无效 token → 401")
    void invalidToken_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/directory")
                        .header("Authorization", "Bearer invalid.jwt.token"))
                .andExpect(status().isUnauthorized());
    }
}
