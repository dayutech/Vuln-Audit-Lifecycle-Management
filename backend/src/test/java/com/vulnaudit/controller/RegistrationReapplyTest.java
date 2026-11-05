package com.vulnaudit.controller;

import com.vulnaudit.entity.RegistrationRequest;
import com.vulnaudit.entity.Role;
import com.vulnaudit.entity.User;
import com.vulnaudit.entity.UserRole;
import com.vulnaudit.entity.enums.RegistrationStatus;
import com.vulnaudit.repository.AuditLogRepository;
import com.vulnaudit.repository.RegistrationRequestRepository;
import com.vulnaudit.repository.VerificationCodeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 拒绝后重新申请 + 管理员清理（t111）
 *
 * <p>核心回归：t102 的已知边界「被拒后同手机号/用户名被占位用户唯一键占用 → 无法重新申请」。
 */
@DisplayName("拒绝后重新申请与管理端清理测试")
public class RegistrationReapplyTest extends BaseTest {

    private static final String SEND_CODE = "/api/v1/auth/register/send-code";
    private static final String REGISTER = "/api/v1/auth/register";
    private static final String ADMIN_REQUESTS = "/api/v1/admin/registration-requests";

    private static final String PHONE = "13500001111";
    private static final String PASSWORD = "Passw0rd!";

    @Autowired
    private RegistrationRequestRepository registrationRequestRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private VerificationCodeRepository verificationCodeRepository;

    // ------------------------------------------------------------------ 工具

    private String sendCode(String phone) throws Exception {
        // 模拟「距上次发码已超过冷却期」：清掉历史验证码，避免 t102 的 5 分钟重发冷却干扰「复用同一手机号」场景
        verificationCodeRepository.deleteAll();
        String body = mockMvc.perform(post(SEND_CODE).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("channel", "sms", "target", phone, "purpose", "REGISTER"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("devCode").asText();
    }

    private String register(String username, String phone) throws Exception {
        String code = sendCode(phone);
        return mockMvc.perform(post(REGISTER).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "password", PASSWORD, "phone", phone, "code", code))))
                .andReturn().getResponse().getContentAsString();
    }

    private String registerOk(String username, String phone) throws Exception {
        String body = register(username, phone);
        String id = objectMapper.readTree(body).path("data").path("id").asText();
        assertFalse(id.isBlank(), "注册应成功: " + body);
        return id;
    }

    private void reject(String requestId) throws Exception {
        mockMvc.perform(post(ADMIN_REQUESTS + "/" + requestId + "/reject")
                        .header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"note\":\"材料不全\"}"))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ 拒绝即释放

    @Test
    @DisplayName("拒绝后同一手机号可重新注册 201，旧申请保留 REJECTED")
    void rejectReleasesPhone() throws Exception {
        String requestId = registerOk("reapply1", PHONE);
        reject(requestId);

        // 占位用户从未登录且无关联 → 直接删除（释放唯一键）
        assertTrue(userRepository.findByUsername("reapply1").isEmpty(), "占位用户应已被删除");

        String newId = registerOk("reapply1b", PHONE);
        assertFalse(newId.isBlank());

        // 旧申请记录保留，作为审批审计证据
        assertEquals(RegistrationStatus.REJECTED,
                registrationRequestRepository.findById(java.util.UUID.fromString(requestId)).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("拒绝后同一用户名也可重新注册 201")
    void rejectReleasesUsername() throws Exception {
        String requestId = registerOk("reapply2", "13500002222");
        reject(requestId);

        String newId = registerOk("reapply2", "13500003333");
        assertFalse(newId.isBlank());
    }

    @Test
    @DisplayName("同一手机号不会同时存在两个 ACTIVE/PENDING_APPROVAL 用户")
    void noDuplicateActiveOrPendingUsers() throws Exception {
        String requestId = registerOk("reapply3", "13500004444");
        reject(requestId);
        registerOk("reapply3b", "13500004444");

        long activeOrPending = userRepository.findAll().stream()
                .filter(u -> "13500004444".equals(u.getPhone()))
                .filter(u -> u.isActive() || u.isPendingApproval())
                .count();
        assertEquals(1, activeOrPending, "同一手机号只允许 1 个 active/pending 用户");
    }

    @Test
    @DisplayName("有关联角色时不删除而是归档释放（username/phone 带后缀，状态 rejected）")
    void archivesWhenUserHasRelatedData() throws Exception {
        String requestId = registerOk("reapply4", "13500005555");
        User placeholder = userRepository.findByUsername("reapply4").orElseThrow();
        Role role = roleRepository.findAll().get(0);
        userRoleRepository.save(UserRole.builder()
                .userId(placeholder.getId())
                .roleId(role.getId())
                .assignedBy(adminUserId)
                .assignedAt(LocalDateTime.now())
                .build());

        reject(requestId);

        User archived = userRepository.findById(placeholder.getId()).orElseThrow();
        assertTrue(archived.getUsername().startsWith("rejected_"), "应归档 username: " + archived.getUsername());
        assertFalse("13500005555".equals(archived.getPhone()), "手机号应被改写以释放唯一键");
        assertEquals("rejected", archived.getStatus());

        // 归档后同一手机号仍可重新申请
        registerOk("reapply4b", "13500005555");
    }

    @Test
    @DisplayName("重复 reject → 409")
    void repeatRejectConflicts() throws Exception {
        String requestId = registerOk("reapply5", "13500006666");
        reject(requestId);

        mockMvc.perform(post(ADMIN_REQUESTS + "/" + requestId + "/reject")
                        .header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"note\":\"再次拒绝\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("已处理")));
    }

    @Test
    @DisplayName("拒绝审计恰好 1 条，且 details 记录释放策略")
    void rejectAuditSingle() throws Exception {
        String requestId = registerOk("reapply6", "13500007777");
        reject(requestId);

        var logs = auditLogRepository.findByAction("REGISTRATION_REJECT");
        assertEquals(1, logs.size(), "一次拒绝应只产生 1 条审计");
        assertTrue(logs.get(0).getDetails().contains("release="), "审计应记录释放策略");
    }

    // ------------------------------------------------------------------ 管理员清理

    @Test
    @DisplayName("purge：204、申请与占位用户均被清理，且同手机号可再注册")
    void purgeRemovesRequestAndUser() throws Exception {
        String requestId = registerOk("reapply7", "13500008888");

        mockMvc.perform(delete(ADMIN_REQUESTS + "/" + requestId).header("Authorization", adminToken))
                .andExpect(status().isNoContent());

        assertTrue(registrationRequestRepository.findById(java.util.UUID.fromString(requestId)).isEmpty());
        assertTrue(userRepository.findByUsername("reapply7").isEmpty());

        registerOk("reapply7b", "13500008888");
    }

    @Test
    @DisplayName("purge 幂等：不存在也返回 204")
    void purgeIsIdempotent() throws Exception {
        String requestId = registerOk("reapply8", "13500009999");
        mockMvc.perform(delete(ADMIN_REQUESTS + "/" + requestId).header("Authorization", adminToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(ADMIN_REQUESTS + "/" + requestId).header("Authorization", adminToken))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("purge 写 REGISTRATION_PURGE 审计（仅 1 条）")
    void purgeAuditedOnce() throws Exception {
        String requestId = registerOk("reapply9", "13500010001");
        mockMvc.perform(delete(ADMIN_REQUESTS + "/" + requestId).header("Authorization", adminToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(ADMIN_REQUESTS + "/" + requestId).header("Authorization", adminToken))
                .andExpect(status().isNoContent());

        assertEquals(1, auditLogRepository.findByAction("REGISTRATION_PURGE").size(),
                "幂等清理不应重复写审计");
    }

    @Test
    @DisplayName("已通过的申请不可 purge（409），避免「删号后同名可重复注册」")
    void purgeApprovedConflicts() throws Exception {
        String requestId = registerOk("reapply10", "13500010002");
        mockMvc.perform(post(ADMIN_REQUESTS + "/" + requestId + "/approve").header("Authorization", adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(delete(ADMIN_REQUESTS + "/" + requestId).header("Authorization", adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("不能清理")));

        // 该账号仍是 active，手机号仍属它
        assertEquals("active", userRepository.findByUsername("reapply10").orElseThrow().getStatus());
    }

    @Test
    @DisplayName("approve 与 purge 的先后：approve 先到则 purge 409；purge 先到则 approve 404/409")
    void approveAndPurgeAreMutuallyExclusive() throws Exception {
        String approvedId = registerOk("reapply11", "13500010003");
        mockMvc.perform(post(ADMIN_REQUESTS + "/" + approvedId + "/approve").header("Authorization", adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(delete(ADMIN_REQUESTS + "/" + approvedId).header("Authorization", adminToken))
                .andExpect(status().isConflict());

        String purgedId = registerOk("reapply12", "13500010004");
        mockMvc.perform(delete(ADMIN_REQUESTS + "/" + purgedId).header("Authorization", adminToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(post(ADMIN_REQUESTS + "/" + purgedId + "/approve").header("Authorization", adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("非 ADMIN 调 purge → 403")
    void purgeRequiresAdmin() throws Exception {
        String requestId = registerOk("reapply13", "13500010005");
        mockMvc.perform(delete(ADMIN_REQUESTS + "/" + requestId).header("Authorization", engineerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(ADMIN_REQUESTS + "/" + requestId).header("Authorization", leaderToken))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ 审批角色（captain 口径）

    @Test
    @DisplayName("approve 传空 roles：建号但不授角色（不得隐式给 vulnerability_engineer）")
    void approveWithEmptyRolesGrantsNothing() throws Exception {
        String requestId = registerOk("reapply14", "13500010006");

        mockMvc.perform(post(ADMIN_REQUESTS + "/" + requestId + "/approve")
                        .header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"roles\":[]}"))
                .andExpect(status().isOk());

        User user = userRepository.findByUsername("reapply14").orElseThrow();
        assertEquals("active", user.getStatus());
        assertTrue(userRoleRepository.findByUserId(user.getId()).isEmpty(),
                "空 roles 不得隐式授予任何角色");
    }

    @Test
    @DisplayName("approve 指定 roles：按角色码授权；未知角色码 400")
    void approveWithRolesGrantsExactlyThoseRoles() throws Exception {
        String requestId = registerOk("reapply15", "13500010007");
        Role engineer = roleRepository.findByCode("vulnerability_engineer").orElseThrow();

        mockMvc.perform(post(ADMIN_REQUESTS + "/" + requestId + "/approve")
                        .header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"roles\":[\"vulnerability_engineer\"]}"))
                .andExpect(status().isOk());

        User user = userRepository.findByUsername("reapply15").orElseThrow();
        var roles = userRoleRepository.findByUserId(user.getId());
        assertEquals(1, roles.size());
        assertEquals(engineer.getId(), roles.get(0).getRoleId());

        // 未知角色码 → 400（显式失败，不静默跳过）
        String anotherId = registerOk("reapply16", "13500010008");
        mockMvc.perform(post(ADMIN_REQUESTS + "/" + anotherId + "/approve")
                        .header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"roles\":[\"not_a_role\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("角色不存在")));
    }

    @Test
    @DisplayName("归档方案下：原用户名登录 401（不泄露），归档用户名登录 403（状态 rejected）")
    void archivedUserLoginSemantics() throws Exception {
        String requestId = registerOk("reapply17", "13500010009");
        User placeholder = userRepository.findByUsername("reapply17").orElseThrow();
        Role role = roleRepository.findAll().get(0);
        userRoleRepository.save(UserRole.builder()
                .userId(placeholder.getId())
                .roleId(role.getId())
                .assignedBy(adminUserId)
                .assignedAt(LocalDateTime.now())
                .build());
        reject(requestId);

        String archivedUsername = userRepository.findById(placeholder.getId()).orElseThrow().getUsername();
        assertTrue(archivedUsername.startsWith("rejected_"));

        // 原用户名：用户已不存在 → 401 通用文案（不泄露「曾被拒绝」）
        mockMvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", "reapply17", "password", PASSWORD))))
                .andExpect(status().isUnauthorized());

        // 归档用户名：行仍在且状态 rejected → 403（内部口径；外部不会知道该用户名）
        mockMvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", archivedUsername, "password", PASSWORD))))
                .andExpect(status().isForbidden());
    }
}
