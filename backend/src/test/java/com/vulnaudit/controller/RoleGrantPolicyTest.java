package com.vulnaudit.controller;

import com.vulnaudit.entity.RegistrationRequest;
import com.vulnaudit.entity.Role;
import com.vulnaudit.entity.User;
import com.vulnaudit.entity.UserRole;
import com.vulnaudit.repository.AuditLogRepository;
import com.vulnaudit.repository.RegistrationRequestRepository;
import com.vulnaudit.repository.VerificationCodeRepository;
import com.vulnaudit.security.RoleAuthority;
import com.vulnaudit.service.RoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 角色授予边界测试（t115）
 *
 * <p>核心回归：前端隐藏 ≠ 后端强制 —— PROJECT_LEADER 直接调接口传 {@code roles:["admin"]}
 * 必须 **403**，且不得留下任何副作用；ADMIN 才能授予 admin / project_leader / is_system 角色。
 */
@DisplayName("角色授予边界（防自助提权）测试")
public class RoleGrantPolicyTest extends BaseTest {

    private static final String SEND_CODE = "/api/v1/auth/register/send-code";
    private static final String REGISTER = "/api/v1/auth/register";
    private static final String ADMIN_REQUESTS = "/api/v1/admin/registration-requests";
    private static final String PASSWORD = "Passw0rd!";

    @Autowired
    private RegistrationRequestRepository registrationRequestRepository;

    @Autowired
    private VerificationCodeRepository verificationCodeRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private RoleService roleService;

    private String plToken;
    private User plUser;
    private Role adminRole;
    private Role projectLeaderRole;
    private Role teamLeaderRole;
    private Role engineerRole;

    private static long counter = 0;

    @BeforeEach
    void setUpRoleGrant() {
        counter++;
        adminRole = roleRepository.findByCode("admin").orElseGet(() -> roleRepository.save(Role.builder()
                .name("管理员").code("admin").description("管理员").isSystem(true).build()));
        projectLeaderRole = roleRepository.findByCode("project_leader").orElseGet(() -> roleRepository.save(
                Role.builder().name("项目负责人").code("project_leader").description("项目负责人").isSystem(true).build()));
        teamLeaderRole = roleRepository.findByCode("team_leader").orElseThrow();
        engineerRole = roleRepository.findByCode("vulnerability_engineer").orElseThrow();

        plUser = userRepository.save(User.builder()
                .username("pl_" + counter)
                .passwordHash(passwordEncoder.encode("pl123456"))
                .email("pl_" + counter + "@test.com")
                .fullName("项目负责人")
                .status("active")
                .build());
        userRoleRepository.save(UserRole.builder()
                .userId(plUser.getId())
                .roleId(projectLeaderRole.getId())
                .assignedBy(adminUserId)
                .assignedAt(LocalDateTime.now())
                .build());
        plToken = generateTestToken(plUser.getUsername(), RoleAuthority.PROJECT_LEADER);
    }

    // ------------------------------------------------------------------ 工具

    private String registerPending(String username, String phone) throws Exception {
        mockMvc.perform(post(SEND_CODE).contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                        Map.of("channel", "sms", "target", phone, "purpose", "REGISTER")))).andExpect(status().isOk());
        String body = mockMvc.perform(post(SEND_CODE).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("channel", "sms", "target", phone, "purpose", "REGISTER"))))
                .andReturn().getResponse().getContentAsString();
        // 第二次可能 429（冷却）；直接从库取未消费码不现实，改用首次响应
        String devCode = objectMapper.readTree(body).path("data").path("devCode").asText(null);
        if (devCode == null || devCode.isBlank()) {
            verificationCodeRepository.deleteAll();
            String retry = mockMvc.perform(post(SEND_CODE).contentType(APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    Map.of("channel", "sms", "target", phone, "purpose", "REGISTER"))))
                    .andReturn().getResponse().getContentAsString();
            devCode = objectMapper.readTree(retry).path("data").path("devCode").asText();
        }
        mockMvc.perform(post(REGISTER).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "password", PASSWORD, "phone", phone, "code", devCode))))
                .andExpect(status().isCreated());
        return registrationRequestRepository.findByUsername(username).stream()
                .filter(r -> r.getStatus() == com.vulnaudit.entity.enums.RegistrationStatus.PENDING)
                .findFirst().orElseThrow().getId().toString();
    }

    private String sendCodeOnce(String phone) throws Exception {
        String body = mockMvc.perform(post(SEND_CODE).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("channel", "sms", "target", phone, "purpose", "REGISTER"))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("devCode").asText();
    }

    private String pendingRequest(String username, String phone) throws Exception {
        verificationCodeRepository.deleteAll();
        String code = sendCodeOnce(phone);
        mockMvc.perform(post(REGISTER).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "password", PASSWORD, "phone", phone, "code", code))))
                .andExpect(status().isCreated());
        RegistrationRequest request = registrationRequestRepository.findByUsername(username).stream()
                .filter(RegistrationRequest::isPending).findFirst().orElseThrow();
        return request.getId().toString();
    }

    private RegistrationRequest latestRequest(String username) {
        return registrationRequestRepository.findByUsername(username).stream()
                .max(java.util.Comparator.comparing(RegistrationRequest::getCreatedAt)).orElseThrow();
    }

    // ------------------------------------------------------------------ 审批入口

    @Test
    @DisplayName("PL 审批传 [admin] → 403，且账号仍待审批、无角色、无成功审计")
    void plCannotGrantAdminByApprove() throws Exception {
        String requestId = pendingRequest("grant1", "13400001111");

        mockMvc.perform(post(ADMIN_REQUESTS + "/" + requestId + "/approve")
                        .header("Authorization", plToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"roles\":[\"admin\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("无权授予管理类角色")))
                .andExpect(jsonPath("$.message", containsString("admin")));

        // 不得留下副作用：仍在待审批、无角色、申请状态未变
        User user = userRepository.findByUsername("grant1").orElseThrow();
        assertEquals("pending_approval", user.getStatus());
        assertTrue(userRoleRepository.findByUserId(user.getId()).isEmpty(), "越权被拒后不得授予任何角色");
        assertEquals(com.vulnaudit.entity.enums.RegistrationStatus.PENDING, latestRequest("grant1").getStatus());
        assertTrue(auditLogRepository.findByAction("REGISTRATION_APPROVE").isEmpty(),
                "被拒的越权请求不得写审批成功审计");
    }

    @Test
    @DisplayName("PL 审批传业务角色 → 200 且角色落库")
    void plCanGrantBusinessRole() throws Exception {
        String requestId = pendingRequest("grant2", "13400002222");

        mockMvc.perform(post(ADMIN_REQUESTS + "/" + requestId + "/approve")
                        .header("Authorization", plToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"roles\":[\"vulnerability_engineer\"]}"))
                .andExpect(status().isOk());

        User user = userRepository.findByUsername("grant2").orElseThrow();
        assertEquals("active", user.getStatus());
        var roles = userRoleRepository.findByUserId(user.getId());
        assertEquals(1, roles.size());
        assertEquals(engineerRole.getId(), roles.get(0).getRoleId());
    }

    @Test
    @DisplayName("ADMIN 审批传 [admin] → 200 且授予 admin")
    void adminCanGrantAdmin() throws Exception {
        String requestId = pendingRequest("grant3", "13400003333");

        mockMvc.perform(post(ADMIN_REQUESTS + "/" + requestId + "/approve")
                        .header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"roles\":[\"admin\"]}"))
                .andExpect(status().isOk());

        User user = userRepository.findByUsername("grant3").orElseThrow();
        var roles = userRoleRepository.findByUserId(user.getId());
        assertEquals(1, roles.size());
        assertEquals(adminRole.getId(), roles.get(0).getRoleId());
    }

    @Test
    @DisplayName("PL 审批传 team_leader（业务角色）→ 200")
    void plCanGrantTeamLeader() throws Exception {
        String requestId = pendingRequest("grant4", "13400004444");
        mockMvc.perform(post(ADMIN_REQUESTS + "/" + requestId + "/approve")
                        .header("Authorization", plToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"roles\":[\"team_leader\"]}"))
                .andExpect(status().isOk());
        User user = userRepository.findByUsername("grant4").orElseThrow();
        assertEquals(teamLeaderRole.getId(), userRoleRepository.findByUserId(user.getId()).get(0).getRoleId());
    }

    @Test
    @DisplayName("PL 审批传 project_leader（管理类）→ 403")
    void plCannotGrantProjectLeader() throws Exception {
        String requestId = pendingRequest("grant5", "13400005555");
        mockMvc.perform(post(ADMIN_REQUESTS + "/" + requestId + "/approve")
                        .header("Authorization", plToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"roles\":[\"project_leader\"]}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("project_leader")));
    }

    @Test
    @DisplayName("非法角色码 → 400（ADMIN / PL 一致），且不建号")
    void unknownRoleRejected() throws Exception {
        String requestId = pendingRequest("grant6", "13400006666");
        mockMvc.perform(post(ADMIN_REQUESTS + "/" + requestId + "/approve")
                        .header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"roles\":[\"not_a_role\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("角色不存在")));
        assertEquals("pending_approval", userRepository.findByUsername("grant6").orElseThrow().getStatus());
    }

    @Test
    @DisplayName("空数组 / 省略 roles → 200 且不授任何角色")
    void emptyRolesGrantsNothing() throws Exception {
        String requestId = pendingRequest("grant7", "13400007777");
        mockMvc.perform(post(ADMIN_REQUESTS + "/" + requestId + "/approve")
                        .header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"roles\":[]}"))
                .andExpect(status().isOk());
        User user = userRepository.findByUsername("grant7").orElseThrow();
        assertEquals("active", user.getStatus());
        assertTrue(userRoleRepository.findByUserId(user.getId()).isEmpty());
    }

    @Test
    @DisplayName("重复角色码去重：只写 1 行 user_roles")
    void duplicateRoleCodesDeduplicated() throws Exception {
        String requestId = pendingRequest("grant8", "13400008888");
        mockMvc.perform(post(ADMIN_REQUESTS + "/" + requestId + "/approve")
                        .header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"roles\":[\"vulnerability_engineer\",\"vulnerability_engineer\"]}"))
                .andExpect(status().isOk());
        User user = userRepository.findByUsername("grant8").orElseThrow();
        assertEquals(1, userRoleRepository.findByUserId(user.getId()).size());
    }

    @Test
    @DisplayName("审批审计记录授予的角色码（且只有成功审批才有该审计）")
    void approveAuditContainsGrantedRoles() throws Exception {
        String requestId = pendingRequest("grant9", "13400009999");
        mockMvc.perform(post(ADMIN_REQUESTS + "/" + requestId + "/approve")
                        .header("Authorization", plToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"roles\":[\"vulnerability_engineer\"]}"))
                .andExpect(status().isOk());

        var logs = auditLogRepository.findByAction("REGISTRATION_APPROVE");
        assertEquals(1, logs.size(), "一次审批只写 1 条审计");
        assertTrue(logs.get(0).getDetails().contains("vulnerability_engineer"),
                "审计应记录被授予的角色: " + logs.get(0).getDetails());
    }

    // ------------------------------------------------------------------ 用户角色接口 / 服务层

    @Test
    @DisplayName("PL 调用户角色接口 → 403（端点仍仅 ADMIN）")
    void plCannotUseUserRoleEndpoint() throws Exception {
        User target = userRepository.findByUsername("grant2").orElse(null);
        if (target == null) {
            target = userRepository.save(User.builder()
                    .username("rolestarget1")
                    .passwordHash(passwordEncoder.encode("x12345678"))
                    .email("rolestarget1@test.com").fullName("t").status("active").build());
        }
        mockMvc.perform(post("/api/v1/users/" + target.getId() + "/roles")
                        .header("Authorization", plToken)
                        .param("roleId", adminRole.getId().toString()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("服务层统一强制：PL 上下文授予 team_leader 成功、授予 admin 抛 403")
    void serviceLayerEnforcesPolicy() {
        User target = userRepository.save(User.builder()
                .username("rolestarget2")
                .passwordHash(passwordEncoder.encode("x12345678"))
                .email("rolestarget2@test.com").fullName("t").status("active").build());

        try {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(plUser.getUsername(), null,
                            List.of(new SimpleGrantedAuthority(RoleAuthority.PROJECT_LEADER))));

            assertTrue(roleService.assignRoleToUser(target.getId(), teamLeaderRole.getId(), plUser.getId()),
                    "PL 授予业务角色应成功");

            var ex = assertThrows(com.vulnaudit.exception.BusinessException.class,
                    () -> roleService.assignRoleToUser(target.getId(), adminRole.getId(), plUser.getId()));
            assertEquals(403, ex.getErrorCode().getCode(), "授予 admin 应 403");
            assertTrue(ex.getMessage().contains("无权授予管理类角色"));
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("ADMIN 上下文可以授予 admin；ADMIN 也不能授予不存在的角色")
    void adminContextAndExistence() {
        User target = userRepository.save(User.builder()
                .username("rolestarget3")
                .passwordHash(passwordEncoder.encode("x12345678"))
                .email("rolestarget3@test.com").fullName("t").status("active").build());
        try {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken("admin_ctx", null,
                            List.of(new SimpleGrantedAuthority(RoleAuthority.ADMIN))));
            assertTrue(roleService.assignRoleToUser(target.getId(), adminRole.getId(), adminUserId));

            var ex = assertThrows(com.vulnaudit.exception.BusinessException.class,
                    () -> roleService.assignRoleToUser(target.getId(), java.util.UUID.randomUUID(), adminUserId));
            assertEquals(1101, ex.getErrorCode().getCode(), "角色不存在应为 ROLE_NOT_FOUND(1101)");
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("策略自身：is_system / admin / project_leader 受限，业务角色不受限")
    void policyClassification() {
        com.vulnaudit.security.RoleGrantPolicy policy = new com.vulnaudit.security.RoleGrantPolicy();
        assertTrue(policy.isRestricted(adminRole));
        assertTrue(policy.isRestricted(projectLeaderRole));
        assertFalse(policy.isRestricted(teamLeaderRole));
        assertFalse(policy.isRestricted(engineerRole));
        assertTrue(policy.restrictedCodes(List.of(adminRole, teamLeaderRole)).contains("admin"));
        assertTrue(policy.restrictedCodes(List.of(teamLeaderRole, engineerRole)).isEmpty());
    }
}
