package com.vulnaudit.controller;

import com.vulnaudit.entity.User;
import com.vulnaudit.entity.enums.VerificationPurpose;
import com.vulnaudit.repository.AuditLogRepository;
import com.vulnaudit.repository.VerificationCodeRepository;
import com.vulnaudit.service.VerificationCodeService;
import com.vulnaudit.entity.enums.VerificationChannel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 忘记密码全链路测试（t103）
 *
 * <p>覆盖：发码 200、5 分钟内重复 429、未验证手机号/邮箱不发码、不存在的 target 返回 200 但不发送、
 * 验证码错误/过期/已消费 400、密码强度不足 400、新旧密码相同 400、
 * 重置成功后旧密码登录失败而新密码成功、旧 token 失效（401）、审计不含验证码与明文密码、连续失败 IP 限流 429。
 */
@DisplayName("忘记密码（验证码重置）全链路测试")
public class PasswordResetFlowTest extends BaseTest {

    private static final String SEND_CODE = "/api/v1/auth/password-reset/send-code";
    private static final String CONFIRM = "/api/v1/auth/password-reset/confirm";

    private static final String PHONE = "13700001111";
    private static final String EMAIL = "resetflow@test.com";
    private static final String OLD_PASSWORD = "OldPass@2026";
    private static final String NEW_PASSWORD = "NewPass@2026";

    @Autowired
    private VerificationCodeRepository verificationCodeRepository;

    @Autowired
    private VerificationCodeService verificationCodeService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    /** t117：createUser 记录最近创建的用户名，供夹具自动带上 username */
    private String currentUsername;

    // ------------------------------------------------------------------ 工具

    private User createUser(String username, String phone, String email,
                            boolean phoneVerified, boolean emailVerified, String password) {
        // t117：夹具自动记录用户名，后续发码/重置请求都会带上（不放宽后端校验）
        this.currentUsername = username;
        return userRepository.save(java.util.Objects.requireNonNull(User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(password))
                .email(email)
                .phone(phone)
                .fullName(username)
                .status(User.STATUS_ACTIVE)
                .phoneVerified(phoneVerified)
                .emailVerified(emailVerified)
                .build()));
    }

    private String sendCodeAndGetDevCode(String channel, String target) throws Exception {
        String body = mockMvc.perform(post(SEND_CODE).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", currentUsername, "channel", channel, "target", target))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String devCode = objectMapper.readTree(body).path("data").path("devCode").asText(null);
        assertNotNull(devCode, "测试环境 dev-mode 应回传 devCode: " + body);
        return devCode;
    }

    private String confirmJson(String target, String code, String newPassword) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "username", currentUsername, "target", target, "code", code, "newPassword", newPassword));
    }

    private int loginStatus(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "password", password))))
                .andReturn().getResponse().getStatus();
    }

    private long codesFor(String target) {
        return verificationCodeRepository.countByTargetAndPurposeAndCreatedAtAfter(
                target, VerificationPurpose.RESET_PASSWORD, LocalDateTime.now().minusHours(2));
    }

    // ------------------------------------------------------------------ 发码

    @Test
    @DisplayName("已验证手机号发码 → 200 且落库一条 RESET_PASSWORD 验证码")
    void sendCodeToVerifiedPhone() throws Exception {
        createUser("reset1", PHONE, "reset1@test.com", true, true, OLD_PASSWORD);

        mockMvc.perform(post(SEND_CODE).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", currentUsername, "channel", "sms", "target", PHONE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.purpose").value("RESET_PASSWORD"))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(300));

        assertEquals(1, codesFor(PHONE), "应落库 1 条重置验证码");
    }

    @Test
    @DisplayName("同一 target 5 分钟内重复发码 → 429")
    void resendWithinIntervalRejected() throws Exception {
        createUser("reset2", PHONE, "reset2@test.com", true, true, OLD_PASSWORD);
        sendCodeAndGetDevCode("sms", PHONE);

        mockMvc.perform(post(SEND_CODE).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", currentUsername, "channel", "sms", "target", PHONE))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message", containsString("过于频繁")));
    }

    @Test
    @DisplayName("未验证手机号 / 未验证邮箱：返回 200 但不发码（不泄露、也不可用）")
    void unverifiedContactNotUsable() throws Exception {
        createUser("reset3", "13700002222", "reset3@test.com", false, false, OLD_PASSWORD);

        String phoneBody = mockMvc.perform(post(SEND_CODE).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", currentUsername, "channel", "sms", "target", "13700002222"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertNull(objectMapper.readTree(phoneBody).path("data").path("devCode").asText(null),
                "未验证手机号不应真的发出验证码");
        assertEquals(0, codesFor("13700002222"));

        String emailBody = mockMvc.perform(post(SEND_CODE).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", currentUsername, "channel", "email", "target", "reset3@test.com"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertNull(objectMapper.readTree(emailBody).path("data").path("devCode").asText(null));
        assertEquals(0, codesFor("reset3@test.com"));
    }

    @Test
    @DisplayName("不存在的 target：返回 200 但不发送（sender 未被调用）")
    void unknownTargetReturns200WithoutSending() throws Exception {
        mockMvc.perform(post(SEND_CODE).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", "ghost_user", "channel", "sms", "target", "13700003333"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.devCode").doesNotExist());

        assertEquals(0, codesFor("13700003333"), "不应创建验证码（Sender 也就不会被调用）");
    }

    // ------------------------------------------------------------------ 验证码校验

    @Test
    @DisplayName("验证码错误 → 400；过期 → 400；已消费 → 400")
    void invalidExpiredAndConsumedCodes() throws Exception {
        createUser("reset4", PHONE, "reset4@test.com", true, true, OLD_PASSWORD);
        String devCode = sendCodeAndGetDevCode("sms", PHONE);

        mockMvc.perform(post(CONFIRM).contentType(APPLICATION_JSON)
                        .content(confirmJson(PHONE, "000000", NEW_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("验证码")));

        // 过期
        var code = verificationCodeRepository
                .findFirstByTargetAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(PHONE, VerificationPurpose.RESET_PASSWORD)
                .orElseThrow();
        code.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        verificationCodeRepository.save(code);

        mockMvc.perform(post(CONFIRM).contentType(APPLICATION_JSON)
                        .content(confirmJson(PHONE, devCode, NEW_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("已过期")));

        // 重新发码并消费后复用（先清空历史码，避免命中上一条「已过期」记录）
        verificationCodeRepository.deleteAll();
        String fresh = sendCodeAndGetDevCode("sms", PHONE);
        verificationCodeService.verifyAndConsume(PHONE, VerificationChannel.SMS,
                VerificationPurpose.RESET_PASSWORD, fresh);

        mockMvc.perform(post(CONFIRM).contentType(APPLICATION_JSON)
                        .content(confirmJson(PHONE, fresh, NEW_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("请先获取验证码")));
    }

    // ------------------------------------------------------------------ 密码策略

    @Test
    @DisplayName("密码强度不足 → 400（且不消耗验证码）")
    void weakPasswordRejected() throws Exception {
        createUser("reset5", PHONE, "reset5@test.com", true, true, OLD_PASSWORD);
        String devCode = sendCodeAndGetDevCode("sms", PHONE);

        mockMvc.perform(post(CONFIRM).contentType(APPLICATION_JSON)
                        .content(confirmJson(PHONE, devCode, "abc12345")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("密码强度不足")));

        // 验证码未被消费：换成强密码仍可成功
        mockMvc.perform(post(CONFIRM).contentType(APPLICATION_JSON)
                        .content(confirmJson(PHONE, devCode, NEW_PASSWORD)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("新密码与旧密码相同 → 400")
    void sameAsOldPasswordRejected() throws Exception {
        createUser("reset6", PHONE, "reset6@test.com", true, true, OLD_PASSWORD);
        String devCode = sendCodeAndGetDevCode("sms", PHONE);

        mockMvc.perform(post(CONFIRM).contentType(APPLICATION_JSON)
                        .content(confirmJson(PHONE, devCode, OLD_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("新密码不能与旧密码相同")));
    }

    // ------------------------------------------------------------------ 成功路径

    @Test
    @DisplayName("重置成功：旧密码登录失败（401）、新密码登录成功（200）")
    void resetThenLogin() throws Exception {
        createUser("reset7", PHONE, "reset7@test.com", true, true, OLD_PASSWORD);
        String devCode = sendCodeAndGetDevCode("sms", PHONE);

        mockMvc.perform(post(CONFIRM).contentType(APPLICATION_JSON)
                        .content(confirmJson(PHONE, devCode, NEW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message", containsString("密码已重置")));

        assertEquals(401, loginStatus("reset7", OLD_PASSWORD), "旧密码应失效");
        assertEquals(200, loginStatus("reset7", NEW_PASSWORD), "新密码应可登录");

        User reloaded = userRepository.findByUsername("reset7").orElseThrow();
        assertTrue(passwordEncoder.matches(NEW_PASSWORD, reloaded.getPasswordHash()));
        assertFalse(passwordEncoder.matches(OLD_PASSWORD, reloaded.getPasswordHash()));
        assertNotNull(reloaded.getTokenInvalidBefore(), "重置后应写入 token 失效时间戳");
    }

    @Test
    @DisplayName("重置后旧 token 失效（401），新登录 token 可用（200）")
    void resetInvalidatesOldTokens() throws Exception {
        createUser("reset8", PHONE, "reset8@test.com", true, true, OLD_PASSWORD);
        String oldToken = generateTestToken("reset8", "ROLE_VULNERABILITY_ENGINEER");

        // 旧 token 在重置前可用
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", oldToken))
                .andExpect(status().isOk());

        // JWT iat 为秒级精度：等 1.1s 让「重置时间」严格晚于旧 token 的签发秒
        Thread.sleep(1100L);

        String devCode = sendCodeAndGetDevCode("sms", PHONE);
        mockMvc.perform(post(CONFIRM).contentType(APPLICATION_JSON)
                        .content(confirmJson(PHONE, devCode, NEW_PASSWORD)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", oldToken))
                .andExpect(status().isUnauthorized());

        // 重新登录拿到的新 token 应可用
        String loginBody = mockMvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "reset8", "password", NEW_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String newToken = "Bearer " + objectMapper.readTree(loginBody).path("data").path("token").asText();
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", newToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("邮箱渠道同样可重置；审计 RESET_PASSWORD 含 channel 且不含验证码/明文密码")
    void emailChannelAndAudit() throws Exception {
        createUser("reset9", null, EMAIL, false, true, OLD_PASSWORD);
        String devCode = sendCodeAndGetDevCode("email", EMAIL);

        mockMvc.perform(post(CONFIRM).contentType(APPLICATION_JSON)
                        .content(confirmJson(EMAIL, devCode, NEW_PASSWORD)))
                .andExpect(status().isOk());

        assertEquals(200, loginStatus("reset9", NEW_PASSWORD));

        var logs = auditLogRepository.findByAction("RESET_PASSWORD");
        assertEquals(1, logs.size(), "一次重置应只产生 1 条 RESET_PASSWORD 审计");
        String details = logs.get(0).getDetails();
        assertTrue(details.contains("channel=email"), "审计应记录渠道: " + details);
        assertFalse(details.contains(devCode), "审计不得包含验证码: " + details);
        assertFalse(details.contains(NEW_PASSWORD), "审计不得包含明文密码: " + details);
        assertFalse(details.contains(OLD_PASSWORD), "审计不得包含旧密码: " + details);
        assertEquals("user", logs.get(0).getResourceType());
    }

    @Test
    @DisplayName("未知 target 的 confirm → 400（统一文案，不泄露账号是否存在）")
    void confirmUnknownTargetRejected() throws Exception {
        currentUsername = "ghost_user";
        mockMvc.perform(post(CONFIRM).contentType(APPLICATION_JSON)
                        .content(confirmJson("13700004444", "123456", NEW_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("验证码无效或已过期")));
    }

    @Test
    @DisplayName("连续失败按 IP 限流：达到上限后 → 429")
    void failedAttemptsRateLimited() throws Exception {
        createUser("reset10", PHONE, "reset10@test.com", true, true, OLD_PASSWORD);
        sendCodeAndGetDevCode("sms", PHONE);

        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post(CONFIRM).contentType(APPLICATION_JSON)
                            .content(confirmJson(PHONE, "000000", NEW_PASSWORD)))
                    .andExpect(status().isBadRequest());
        }

        mockMvc.perform(post(CONFIRM).contentType(APPLICATION_JSON)
                        .content(confirmJson(PHONE, "000000", NEW_PASSWORD)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message", containsString("失败次数过多")));
    }

    @Test
    @DisplayName("重置后可继续使用该账号的其它能力（偏好接口 200）")
    void accountStillUsableAfterReset() throws Exception {
        User user = createUser("reset11", PHONE, "reset11@test.com", true, true, OLD_PASSWORD);
        String devCode = sendCodeAndGetDevCode("sms", PHONE);

        mockMvc.perform(post(CONFIRM).contentType(APPLICATION_JSON)
                        .content(confirmJson(PHONE, devCode, NEW_PASSWORD)))
                .andExpect(status().isOk());

        String loginBody = mockMvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "reset11", "password", NEW_PASSWORD))))
                .andReturn().getResponse().getContentAsString();
        String token = "Bearer " + objectMapper.readTree(loginBody).path("data").path("token").asText();

        mockMvc.perform(get("/api/v1/users/me/preferences").header("Authorization", token))
                .andExpect(status().isOk());
        assertEquals(user.getId(), userRepository.findByUsername("reset11").orElseThrow().getId());
        assertNotNull(UUID.fromString(user.getId().toString()));
    }
}
