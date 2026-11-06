package com.vulnaudit.controller;

import com.vulnaudit.entity.User;
import com.vulnaudit.repository.AuditLogRepository;
import com.vulnaudit.service.TwoFactorService;
import com.vulnaudit.util.TotpUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 双因子认证（t125）：初始化 / 确认 / 登录两阶段 / 恢复码 / 关闭 / 管理员强制
 */
@DisplayName("双因子认证（TOTP）测试")
public class TwoFactorAuthTest extends BaseTest {

    private static final String MY_2FA = "/api/v1/users/me/2fa";
    private static final String PASSWORD = "TwoFactor@2026";

    @Autowired
    private TwoFactorService twoFactorService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private com.vulnaudit.repository.SystemSettingRepository systemSettingRepository;

    @BeforeEach
    void resetTwoFactorState() {
        twoFactorService.resetChallenges();
        // 复位管理员强制开关：直接删 system_settings 行，**不走 setForceTwoFactor**
        // —— 后者会额外写一条 UPDATE_FORCE_TWO_FACTOR 审计，导致本类「开关只写 1 条审计」的断言变成 2。
        systemSettingRepository.findBySettingKey(TwoFactorService.FORCE_TWO_FACTOR_KEY)
                .ifPresent(systemSettingRepository::delete);
    }

    // ------------------------------------------------------------------ 工具

    private User createUser(String username) {
        return userRepository.save(java.util.Objects.requireNonNull(User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .email(username + "@test.com")
                .fullName(username)
                .status(User.STATUS_ACTIVE)
                .emailVerified(true)
                .build()));
    }

    private String loginToken(String username) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", username, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + objectMapper.readTree(body).path("data").path("token").asText();
    }

    private String initSecret(String token) throws Exception {
        String body = mockMvc.perform(post(MY_2FA + "/init").header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("secretBase32").asText();
    }

    private java.util.List<String> enableTwoFactor(String token, String secret) throws Exception {
        String body = mockMvc.perform(post(MY_2FA + "/confirm").header("Authorization", token)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", TotpUtils.currentCode(secret)))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        java.util.List<String> codes = new java.util.ArrayList<>();
        objectMapper.readTree(body).path("data").forEach(node -> codes.add(node.asText()));
        return codes;
    }

    // ------------------------------------------------------------------ 初始化 / 确认

    @Test
    @DisplayName("init 返回 32 字符 Base32 密钥，两次初始化不同，且 otpauth URI 正确")
    void initReturnsRandomSecret() throws Exception {
        createUser("tfa1");
        String token = loginToken("tfa1");

        String body1 = mockMvc.perform(post(MY_2FA + "/init").header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String secret1 = objectMapper.readTree(body1).path("data").path("secretBase32").asText();
        String uri = objectMapper.readTree(body1).path("data").path("otpauthUri").asText();
        assertEquals(32, secret1.length());
        assertTrue(secret1.matches("[A-Z2-7]{32}"), "必须是 Base32: " + secret1);
        assertTrue(uri.startsWith("otpauth://totp/OwlAudit:tfa1?secret="), uri);

        String secret2 = initSecret(token);
        assertNotEquals(secret1, secret2, "重复初始化必须生成不同密钥");
    }

    @Test
    @DisplayName("confirm 错码 400、正确码 200 并开启；状态接口不返回 secret")
    void confirmEnablesTwoFactor() throws Exception {
        createUser("tfa2");
        String token = loginToken("tfa2");
        String secret = initSecret(token);

        mockMvc.perform(post(MY_2FA + "/confirm").header("Authorization", token)
                        .contentType(APPLICATION_JSON).content("{\"code\":\"000000\"}"))
                .andExpect(status().isBadRequest());

        java.util.List<String> codes = enableTwoFactor(token, secret);
        assertEquals(10, codes.size(), "应返回 10 个恢复码");

        String status = mockMvc.perform(get(MY_2FA).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.recoveryCodesLeft").value(10))
                .andReturn().getResponse().getContentAsString();
        assertFalse(status.contains(secret), "状态接口不得返回密钥");
        assertFalse(status.toLowerCase().contains("totpsecret"), status);

        assertEquals(1, auditLogRepository.findByAction("TWO_FACTOR_ENABLED").size(), "开启只写 1 条审计");
    }

    @Test
    @DisplayName("未初始化就 confirm → 400")
    void confirmWithoutInitRejected() throws Exception {
        createUser("tfa3");
        String token = loginToken("tfa3");
        mockMvc.perform(post(MY_2FA + "/confirm").header("Authorization", token)
                        .contentType(APPLICATION_JSON).content("{\"code\":\"123456\"}"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ 登录两阶段

    @Test
    @DisplayName("登录两步：密码 → 202 + challengeToken → 动态码 → 200 拿 JWT")
    void twoPhaseLogin() throws Exception {
        createUser("tfa4");
        String token = loginToken("tfa4");
        String secret = initSecret(token);
        enableTwoFactor(token, secret);

        String body = mockMvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "tfa4", "password", PASSWORD))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.twoFactorRequired").value(true))
                .andReturn().getResponse().getContentAsString();
        String challenge = objectMapper.readTree(body).path("data").path("challengeToken").asText();
        assertFalse(challenge.isBlank());
        assertFalse(body.contains("\"token\""), "第一步不得签发 JWT");

        mockMvc.perform(post("/api/v1/auth/login/2fa").contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "challengeToken", challenge, "code", TotpUtils.currentCode(secret)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    @Test
    @DisplayName("challenge 一次性：成功后复用 → 400")
    void challengeSingleUse() throws Exception {
        createUser("tfa5");
        String token = loginToken("tfa5");
        String secret = initSecret(token);
        enableTwoFactor(token, secret);

        String body = mockMvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "tfa5", "password", PASSWORD))))
                .andReturn().getResponse().getContentAsString();
        String challenge = objectMapper.readTree(body).path("data").path("challengeToken").asText();
        String code = TotpUtils.currentCode(secret);

        mockMvc.perform(post("/api/v1/auth/login/2fa").contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("challengeToken", challenge, "code", code))))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/login/2fa").contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("challengeToken", challenge, "code", code))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("未知 challenge → 400")
    void unknownChallengeRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login/2fa").contentType(APPLICATION_JSON)
                        .content("{\"challengeToken\":\"nope\",\"code\":\"123456\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("二次验证会话")));
    }

    @Test
    @DisplayName("挑战期连续 5 次错码 → 挑战作废（后续用正确码也 400）")
    void challengeVoidAfterFiveFailures() throws Exception {
        createUser("tfa6");
        String token = loginToken("tfa6");
        String secret = initSecret(token);
        enableTwoFactor(token, secret);

        String body = mockMvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "tfa6", "password", PASSWORD))))
                .andReturn().getResponse().getContentAsString();
        String challenge = objectMapper.readTree(body).path("data").path("challengeToken").asText();

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/auth/login/2fa").contentType(APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "challengeToken", challenge, "code", "000000"))))
                    .andExpect(status().isBadRequest());
        }
        String code = TotpUtils.currentCode(secret);
        mockMvc.perform(post("/api/v1/auth/login/2fa").contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("challengeToken", challenge, "code", code))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("恢复码可登录一次，第二次 400")
    void recoveryCodeSingleUse() throws Exception {
        createUser("tfa7");
        String token = loginToken("tfa7");
        String secret = initSecret(token);
        java.util.List<String> codes = enableTwoFactor(token, secret);

        String body = mockMvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "tfa7", "password", PASSWORD))))
                .andReturn().getResponse().getContentAsString();
        String challenge1 = objectMapper.readTree(body).path("data").path("challengeToken").asText();
        mockMvc.perform(post("/api/v1/auth/login/2fa").contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "challengeToken", challenge1, "code", codes.get(0)))))
                .andExpect(status().isOk());

        String body2 = mockMvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "tfa7", "password", PASSWORD))))
                .andReturn().getResponse().getContentAsString();
        String challenge2 = objectMapper.readTree(body2).path("data").path("challengeToken").asText();
        mockMvc.perform(post("/api/v1/auth/login/2fa").contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "challengeToken", challenge2, "code", codes.get(0)))))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ 关闭

    @Test
    @DisplayName("disable 需密码 + 动态码；密码错 403，成功后再登录不再需要二次验证")
    void disableRequiresPasswordAndCode() throws Exception {
        createUser("tfa8");
        String token = loginToken("tfa8");
        String secret = initSecret(token);
        enableTwoFactor(token, secret);

        mockMvc.perform(post(MY_2FA + "/disable").header("Authorization", token)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "password", "wrong-password", "code", TotpUtils.currentCode(secret)))))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(MY_2FA + "/disable").header("Authorization", token)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "password", PASSWORD, "code", TotpUtils.currentCode(secret)))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "tfa8", "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty());
        assertEquals(1, auditLogRepository.findByAction("TWO_FACTOR_DISABLED").size());
    }

    @Test
    @DisplayName("管理员强制开启时 disable → 403「无法关闭」")
    void disableBlockedWhenForced() throws Exception {
        createUser("tfa9");
        String token = loginToken("tfa9");
        String secret = initSecret(token);
        enableTwoFactor(token, secret);

        mockMvc.perform(put("/api/v1/admin/settings/force-two-factor").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(post(MY_2FA + "/disable").header("Authorization", token)
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "password", PASSWORD, "code", TotpUtils.currentCode(secret)))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("无法关闭")));
    }

    // ------------------------------------------------------------------ 管理员强制

    @Test
    @DisplayName("非 ADMIN 改强制开关 → 403；ADMIN 开启写审计且登录响应带 mustSetupTwoFactor")
    void forceSettingRequiresAdmin() throws Exception {
        createUser("tfa10");
        mockMvc.perform(put("/api/v1/admin/settings/force-two-factor").header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/admin/settings/force-two-factor").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(true));
        assertEquals(1, auditLogRepository.findByAction("UPDATE_FORCE_TWO_FACTOR").size());

        mockMvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "tfa10", "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mustSetupTwoFactor").value(true));
    }

    @Test
    @DisplayName("强制开启后：未开 2FA 的用户访问业务接口 403，2FA 设置接口放行")
    void enforcementBlocksBusinessCalls() throws Exception {
        createUser("tfa11");
        String token = loginToken("tfa11");

        mockMvc.perform(put("/api/v1/admin/settings/force-two-factor").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isOk());

        String body = mockMvc.perform(get("/api/v1/projects").header("Authorization", token))
                .andExpect(status().isForbidden())
                .andReturn().getResponse().getContentAsString();
        assertTrue(body.contains("TWO_FACTOR_SETUP_REQUIRED"), body);

        // 2FA 设置接口放行
        mockMvc.perform(get(MY_2FA).header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.forced").value(true));
    }

    @Test
    @DisplayName("已开启 2FA 的用户不受强制拦截影响")
    void enabledUserNotBlocked() throws Exception {
        createUser("tfa12");
        String token = loginToken("tfa12");
        String secret = initSecret(token);
        enableTwoFactor(token, secret);

        mockMvc.perform(put("/api/v1/admin/settings/force-two-factor").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/projects").header("Authorization", token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("关闭强制开关后不再拦截")
    void disablingForceUnblocks() throws Exception {
        createUser("tfa13");
        String token = loginToken("tfa13");

        mockMvc.perform(put("/api/v1/admin/settings/force-two-factor").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/projects").header("Authorization", token)).andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/admin/settings/force-two-factor").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/projects").header("Authorization", token)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("未登录访问 2FA 状态 → 401（不泄露）")
    void anonymousRejected() throws Exception {
        mockMvc.perform(get(MY_2FA)).andExpect(status().isUnauthorized());
    }
}
