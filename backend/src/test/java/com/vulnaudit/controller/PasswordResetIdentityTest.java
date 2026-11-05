package com.vulnaudit.controller;

import com.vulnaudit.entity.User;
import com.vulnaudit.repository.AuditLogRepository;
import com.vulnaudit.repository.VerificationCodeRepository;
import com.vulnaudit.service.verification.VerificationCodeSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 忘记密码「用户名 + 手机/邮箱」双因子一致校验（t117）
 *
 * <p>核心回归：只知道手机号不能再重置他人密码 —— 必须 username 与该手机号属于同一账号，
 * 且不匹配时对外仍是 200（不泄露原因），只是**不发码**（用录制型 sender 断言）。
 */
@DisplayName("忘记密码双因子（用户名 + 手机/邮箱）一致性测试")
@Import(PasswordResetIdentityTest.RecordingSenderConfig.class)
public class PasswordResetIdentityTest extends BaseTest {

    private static final String SEND_CODE = "/api/v1/auth/password-reset/send-code";
    private static final String CONFIRM = "/api/v1/auth/password-reset/confirm";

    private static final String OLD_PASSWORD = "OldPass@2026";
    private static final String NEW_PASSWORD = "NewPass@2026";

    /** 录制被投递的验证码（(channel, target)） */
    static final List<String[]> SENT = new CopyOnWriteArrayList<>();

    @TestConfiguration
    static class RecordingSenderConfig {
        @Bean
        @Primary
        VerificationCodeSender recordingVerificationCodeSender() {
            return new VerificationCodeSender() {
                @Override
                public void sendSms(String target, String code) {
                    SENT.add(new String[]{"sms", target});
                }

                @Override
                public void sendEmail(String target, String code) {
                    SENT.add(new String[]{"email", target});
                }
            };
        }
    }

    @Autowired
    private VerificationCodeRepository verificationCodeRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @BeforeEach
    void resetRecorder() {
        SENT.clear();
        verificationCodeRepository.deleteAll();
    }

    // ------------------------------------------------------------------ 夹具

    private User createUser(String username, String phone, String email,
                            boolean phoneVerified, boolean emailVerified) {
        return userRepository.save(java.util.Objects.requireNonNull(User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(OLD_PASSWORD))
                .email(email)
                .phone(phone)
                .fullName(username)
                .status(User.STATUS_ACTIVE)
                .phoneVerified(phoneVerified)
                .emailVerified(emailVerified)
                .build()));
    }

    private String send(String username, String channel, String target) throws Exception {
        String body = mockMvc.perform(post(SEND_CODE).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "channel", channel, "target", target))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("devCode").asText("");
    }

    private void confirm(String username, String target, String code, String newPassword) throws Exception {
        mockMvc.perform(post(CONFIRM).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "target", target, "code", code, "newPassword", newPassword))))
                .andExpect(status().isOk());
    }

    private int loginStatus(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("username", username, "password", password))))
                .andReturn().getResponse().getStatus();
    }

    // ------------------------------------------------------------------ 发码：双因子一致

    @Test
    @DisplayName("用户名 + 手机号匹配 → 200 且真的发码")
    void matchedPhoneSendsCode() throws Exception {
        createUser("ident1", "13600001111", "ident1@test.com", true, true);

        String devCode = send("ident1", "sms", "13600001111");

        assertFalse(devCode.isBlank(), "匹配成功应发码（dev-mode 回传 devCode）");
        assertEquals(1, SENT.size(), "sender 应被调用一次");
        assertEquals("sms", SENT.get(0)[0]);
        assertEquals("13600001111", SENT.get(0)[1]);
    }

    @Test
    @DisplayName("用户名存在但手机号是别人的 → 200 但不发码")
    void phoneMismatchDoesNotSend() throws Exception {
        createUser("ident2", "13600002222", "ident2@test.com", true, true);
        createUser("ident3", "13600003333", "ident3@test.com", true, true);

        String devCode = send("ident2", "sms", "13600003333");

        assertTrue(devCode.isBlank(), "不匹配不得返回验证码");
        assertTrue(SENT.isEmpty(), "不匹配不得调用 sender");
    }

    @Test
    @DisplayName("用户名不存在 → 200 且不发码")
    void unknownUsernameDoesNotSend() throws Exception {
        createUser("ident4", "13600004444", "ident4@test.com", true, true);
        String devCode = send("nobody_here", "sms", "13600004444");
        assertTrue(devCode.isBlank());
        assertTrue(SENT.isEmpty());
    }

    @Test
    @DisplayName("用户名 + 邮箱匹配 → 200 且发码")
    void matchedEmailSendsCode() throws Exception {
        createUser("ident5", "13600005555", "ident5@test.com", true, true);
        String devCode = send("ident5", "email", "ident5@test.com");
        assertFalse(devCode.isBlank());
        assertEquals(1, SENT.size());
        assertEquals("email", SENT.get(0)[0]);
    }

    @Test
    @DisplayName("手机号未验证 → 200 但不发码")
    void unverifiedPhoneDoesNotSend() throws Exception {
        createUser("ident6", "13600006666", "ident6@test.com", false, true);
        String devCode = send("ident6", "sms", "13600006666");
        assertTrue(devCode.isBlank());
        assertTrue(SENT.isEmpty());
    }

    @Test
    @DisplayName("账号停用 → 200 但不发码")
    void inactiveAccountDoesNotSend() throws Exception {
        User user = createUser("ident7", "13600007777", "ident7@test.com", true, true);
        user.setStatus("suspended");
        userRepository.save(user);

        String devCode = send("ident7", "sms", "13600007777");
        assertTrue(devCode.isBlank());
        assertTrue(SENT.isEmpty());
    }

    @Test
    @DisplayName("同一手机号换一个用户名请求 → 不发码（手机号不能跨账号复用）")
    void phoneCannotBeReusedByAnotherUsername() throws Exception {
        createUser("ident8", "13600008888", "ident8@test.com", true, true);
        createUser("ident9", "13600009999", "ident9@test.com", true, true);

        // ident9 拿 ident8 的手机号
        String devCode = send("ident9", "sms", "13600008888");
        assertTrue(devCode.isBlank());
        assertTrue(SENT.isEmpty());
    }

    @Test
    @DisplayName("username 连续 5 次不匹配 → 该用户名冷却，后续即使匹配也 200 但不发送")
    void usernameCooldownAfterRepeatedMismatch() throws Exception {
        createUser("ident10", "13600010000", "ident10@test.com", true, true);
        createUser("ident11", "13600011111", "ident11@test.com", true, true);

        for (int i = 0; i < 5; i++) {
            assertTrue(send("ident10", "sms", "13600011111").isBlank());
        }
        assertEquals(0, SENT.size(), "不匹配期间不得发码");

        // 冷却期内即使因子匹配也不发码（仍 200）
        String devCode = send("ident10", "sms", "13600010000");
        assertTrue(devCode.isBlank(), "冷却期内不得发码");
        assertTrue(SENT.isEmpty());
    }

    // ------------------------------------------------------------------ confirm 阶段再校验

    @Test
    @DisplayName("confirm 阶段 username 与 target 不一致 → 400 且密码未变")
    void confirmRejectsIdentityMismatch() throws Exception {
        User a = createUser("ident12", "13600012222", "ident12@test.com", true, true);
        createUser("ident13", "13600013333", "ident13@test.com", true, true);
        String devCode = send("ident12", "sms", "13600012222");
        assertFalse(devCode.isBlank());

        // 用 A 的验证码 + A 的手机号，但声称是 B
        mockMvc.perform(post(CONFIRM).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "ident13", "target", "13600012222",
                                "code", devCode, "newPassword", NEW_PASSWORD))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("验证码无效或已过期")));

        assertEquals(OLD_PASSWORD, OLD_PASSWORD);
        assertEquals(401, loginStatus("ident13", NEW_PASSWORD), "B 的密码不应被改动");
        assertEquals(200, loginStatus("ident12", OLD_PASSWORD), "A 的密码也不应被改动");
        assertTrue(userRepository.findById(a.getId()).orElseThrow().getPasswordHash()
                .equals(userRepository.findByUsername("ident12").orElseThrow().getPasswordHash()));
    }

    @Test
    @DisplayName("完整链路：用户名 + 手机号匹配 → 重置成功 → 旧密码 401 / 新密码 200")
    void fullResetFlowWithUsername() throws Exception {
        createUser("ident14", "13600014444", "ident14@test.com", true, true);
        String devCode = send("ident14", "sms", "13600014444");
        assertFalse(devCode.isBlank());

        confirm("ident14", "13600014444", devCode, NEW_PASSWORD);

        assertEquals(401, loginStatus("ident14", OLD_PASSWORD));
        assertEquals(200, loginStatus("ident14", NEW_PASSWORD));
    }

    @Test
    @DisplayName("审计 RESET_PASSWORD 含 username 且不含验证码")
    void auditContainsUsername() throws Exception {
        createUser("ident15", "13600015555", "ident15@test.com", true, true);
        String devCode = send("ident15", "sms", "13600015555");
        confirm("ident15", "13600015555", devCode, NEW_PASSWORD);

        var logs = auditLogRepository.findByAction("RESET_PASSWORD");
        assertEquals(1, logs.size());
        String details = logs.get(0).getDetails();
        assertTrue(details.contains("username=ident15"), "审计应含 username: " + details);
        assertFalse(details.contains(devCode), "审计不得含验证码: " + details);
    }

    @Test
    @DisplayName("空 username → 400（参数校验，不是静默 200）")
    void blankUsernameRejected() throws Exception {
        mockMvc.perform(post(SEND_CODE).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "  ", "channel", "sms", "target", "13600016666"))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(CONFIRM).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "", "target", "13600016666",
                                "code", "123456", "newPassword", NEW_PASSWORD))))
                .andExpect(status().isBadRequest());
    }
}
