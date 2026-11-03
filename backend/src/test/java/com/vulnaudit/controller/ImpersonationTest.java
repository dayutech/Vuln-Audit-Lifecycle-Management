package com.vulnaudit.controller;

import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.AuditLog;
import com.vulnaudit.entity.User;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.AuditLogRepository;
import com.vulnaudit.repository.UserRepository;
import com.vulnaudit.security.JwtUtils;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 管理员模拟视角测试（t90）。
 *
 * <p>覆盖：签发与 claims（sub/act/imp/exp）、非 admin 403、模拟 admin 409、链式模拟 403、
 * 模拟态敏感端点 403（/users、/roles、/admin 写）、审计归属（impersonatedBy）、
 * STOP 审计、过期 token 401、目标停用 400、普通态非 admin 调 stop 403。
 */
@DisplayName("管理员模拟视角测试")
public class ImpersonationTest extends BaseTest {

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private Asset engineerOwnedAsset;

    @BeforeEach
    void setUpAsset() {
        engineerOwnedAsset = assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name("模拟测试资产_" + System.nanoTime())
                .code("IMP-A-" + System.nanoTime())
                .targetType("Web应用")
                .targetSource("internal")
                .priority("P1")
                .status("active")
                .ownerId(engineerUserId)
                .build()));
    }

    /** 发起模拟并返回受限 token */
    private String impersonateAs(UUID targetUserId) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/impersonate")
                        .header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":\"" + targetUserId + "\",\"reason\":\"审阅角色分配\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        int idx = body.indexOf("\"token\":\"");
        assertTrue(idx > 0, "响应应包含 token: " + body);
        int start = idx + "\"token\":\"".length();
        return body.substring(start, body.indexOf('"', start));
    }

    private Claims claimsOf(String token) {
        Claims claims = Jwts.parser()
                .verifyWith((javax.crypto.SecretKey) org.springframework.test.util.ReflectionTestUtils
                        .getField(jwtUtils, "secretKey"))
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims;
    }

    // ------------------------------------------------------------------ 签发与 claims

    @Test
    @DisplayName("正常签发：200 + 响应字段齐全，claims 为 sub=目标用户 / act=admin / imp=true")
    void impersonateIssuesRestrictedToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/admin/impersonate")
                        .header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":\"" + engineerUserId + "\",\"reason\":\"审阅权限\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").exists())
                .andExpect(jsonPath("$.data.expiresAt").exists())
                .andExpect(jsonPath("$.data.target.id").value(engineerUserId.toString()))
                .andExpect(jsonPath("$.data.target.roles[0]").value("vulnerability_engineer"))
                .andExpect(jsonPath("$.data.actor.id").value(adminUserId.toString()))
                .andReturn();

        String token = impersonateAs(engineerUserId);
        Claims claims = claimsOf(token);
        String targetUsername = userRepository.findById(engineerUserId).orElseThrow().getUsername();

        assertEquals(targetUsername, claims.getSubject(), "sub 必须是目标用户");
        assertEquals(adminUserId.toString(), claims.get("act"), "act 必须是真实操作者 adminId");
        assertEquals("true", String.valueOf(claims.get("imp")));
        long ttlMillis = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        assertEquals(7200_000L, ttlMillis, "默认有效期必须缩短为 2 小时");

        // 受限 token 可访问目标用户可见资源
        mockMvc.perform(get("/api/v1/assets").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/assets/" + engineerOwnedAsset.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        assertNotNull(result);
    }

    @Test
    @DisplayName("START 审计：action=IMPERSONATE_START、resourceType=user、resourceId=目标、含 reason 与操作者")
    void impersonateWritesStartAudit() throws Exception {
        impersonateAs(engineerUserId);

        List<AuditLog> logs = auditLogRepository.findByAction("IMPERSONATE_START");
        assertTrue(logs.stream().anyMatch(log -> engineerUserId.equals(log.getResourceId())
                        && "user".equals(log.getResourceType())
                        && adminUserId.equals(log.getUserId())
                        && log.getDetails() != null
                        && log.getDetails().contains("审阅角色分配")),
                "应记录 IMPERSONATE_START 审计（含 reason 与操作者）");
    }

    // ------------------------------------------------------------------ 发起方约束

    @Test
    @DisplayName("非 ADMIN 发起模拟 → 403（工程师 / 团队长）")
    void nonAdminCannotImpersonate() throws Exception {
        mockMvc.perform(post("/api/v1/admin/impersonate")
                        .header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":\"" + engineerUserId + "\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/impersonate")
                        .header("Authorization", leaderToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":\"" + engineerUserId + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("模拟 ADMIN → 409；模拟自己 → 409")
    void cannotImpersonateAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/admin/impersonate")
                        .header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":\"" + adminUserId + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("不能模拟管理员")));
    }

    @Test
    @DisplayName("目标用户不存在 → 404；目标已停用 → 400")
    void targetValidation() throws Exception {
        mockMvc.perform(post("/api/v1/admin/impersonate")
                        .header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound());

        User engineer = userRepository.findById(engineerUserId).orElseThrow();
        String originalStatus = engineer.getStatus();
        engineer.setStatus("disabled");
        userRepository.saveAndFlush(engineer);
        try {
            mockMvc.perform(post("/api/v1/admin/impersonate")
                            .header("Authorization", adminToken)
                            .contentType(APPLICATION_JSON)
                            .content("{\"userId\":\"" + engineerUserId + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("已停用")));
        } finally {
            engineer.setStatus(originalStatus);
            userRepository.saveAndFlush(engineer);
        }
    }

    @Test
    @DisplayName("链式模拟：模拟 token 再调 /admin/impersonate → 403")
    void chainedImpersonationForbidden() throws Exception {
        String impersonationToken = impersonateAs(engineerUserId);

        mockMvc.perform(post("/api/v1/admin/impersonate")
                        .header("Authorization", "Bearer " + impersonationToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"userId\":\"" + leaderUserId + "\"}"))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ 模拟态敏感端点

    @Test
    @DisplayName("模拟态写用户/角色/管理员端点一律 403；读接口不受限")
    void impersonationCannotWriteIdentityEndpoints() throws Exception {
        String token = "Bearer " + impersonateAs(engineerUserId);

        mockMvc.perform(post("/api/v1/users").header("Authorization", token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"imp_new_user\",\"password\":\"Passw0rd!\",\"email\":\"a@b.c\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/users/" + leaderUserId).header("Authorization", token)
                        .contentType(APPLICATION_JSON).content("{\"fullName\":\"改名\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/users/" + leaderUserId).header("Authorization", token))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/roles").header("Authorization", token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"模拟建角色\",\"code\":\"imp_role\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/roles/" + UUID.randomUUID()).header("Authorization", token))
                .andExpect(status().isForbidden());

        // 守卫只拦写：身份类读接口不被模拟态额外封禁（仍受目标用户自身权限约束）
        mockMvc.perform(get("/api/v1/users/me/permissions").header("Authorization", token))
                .andExpect(status().isOk());
        // /users、/roles 列表本身要 ADMIN/PL：被模拟的工程师拿 403 属其自身权限，非守卫拦截
        mockMvc.perform(get("/api/v1/users").header("Authorization", token))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ 审计归属

    @Test
    @DisplayName("业务审计归属：user_id=目标用户，details 含 impersonatedBy=adminId")
    void businessAuditAttributedToTargetWithActor() throws Exception {
        String token = "Bearer " + impersonateAs(engineerUserId);

        mockMvc.perform(post("/api/v1/assets/" + engineerOwnedAsset.getId() + "/tags")
                        .header("Authorization", token).param("tag", "imp-tag-" + System.nanoTime()))
                .andExpect(status().isCreated());

        boolean attributed = auditLogRepository.findAll().stream()
                .anyMatch(log -> engineerUserId.equals(log.getUserId())
                        && log.getDetails() != null
                        && log.getDetails().contains("impersonatedBy=" + adminUserId));
        assertTrue(attributed, "业务审计应记录 impersonatedBy=" + adminUserId);

        // 对照组：普通登录态（工程师原 token）做同类写操作，details 不应含 impersonatedBy
        mockMvc.perform(post("/api/v1/assets/" + engineerOwnedAsset.getId() + "/tags")
                        .header("Authorization", engineerToken).param("tag", "normal-tag-" + System.nanoTime()))
                .andExpect(status().isCreated());
        assertTrue(auditLogRepository.findAll().stream()
                        .noneMatch(log -> log.getDetails() != null
                                && log.getDetails().contains("normal-tag")
                                && log.getDetails().contains("impersonatedBy")),
                "普通态审计不应带 impersonatedBy");
    }

    @Test
    @DisplayName("STOP：模拟 token 调 /admin/impersonate/stop → 204 且写 IMPERSONATE_STOP 审计")
    void stopWritesAudit() throws Exception {
        String token = "Bearer " + impersonateAs(engineerUserId);

        mockMvc.perform(post("/api/v1/admin/impersonate/stop").header("Authorization", token))
                .andExpect(status().isNoContent());

        List<AuditLog> stopLogs = auditLogRepository.findByAction("IMPERSONATE_STOP");
        assertTrue(stopLogs.stream().anyMatch(log -> adminUserId.equals(log.getUserId())
                        && engineerUserId.equals(log.getResourceId())),
                "应记录 IMPERSONATE_STOP（actor=admin, target=engineer）");
    }

    @Test
    @DisplayName("普通态非 ADMIN 调 stop → 403（STOP 也必须是管理员）")
    void nonAdminCannotStop() throws Exception {
        mockMvc.perform(post("/api/v1/admin/impersonate/stop").header("Authorization", engineerToken))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ 过期

    @Test
    @DisplayName("模拟 token 过期后 → 401（既有 AuthenticationEntryPoint）")
    void expiredImpersonationTokenUnauthorized() throws Exception {
        String engineerUsername = userRepository.findById(engineerUserId).orElseThrow().getUsername();
        String expired = jwtUtils.generateImpersonationToken(engineerUsername,
                List.of(new SimpleGrantedAuthority("ROLE_VULNERABILITY_ENGINEER")),
                adminUserId, -60_000L);

        mockMvc.perform(get("/api/v1/assets").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }
}
