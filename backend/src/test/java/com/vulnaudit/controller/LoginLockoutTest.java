package com.vulnaudit.controller;

import com.vulnaudit.entity.User;
import com.vulnaudit.repository.AuditLogRepository;
import com.vulnaudit.repository.VerificationCodeRepository;
import com.vulnaudit.service.AccountUnlockService;
import com.vulnaudit.service.LoginLockoutService;
import com.vulnaudit.service.verification.VerificationCodeSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 登录暴力破解防护（t123）：IP 优先锁定 → 跨 IP 升级锁账户 → 告警 → OTP 解锁
 *
 * <p>用 {@link FakeSenderConfig} 录制安全告警（真实实现依赖短信/邮件网关）。
 * 不同来源 IP 通过 {@code X-Forwarded-For} 模拟（{@code AuthController.resolveClientIp} 优先取该头）。
 */
@DisplayName("登录暴力破解防护（两级锁定 + OTP 解锁）测试")
@Import(LoginLockoutTest.FakeSenderConfig.class)
public class LoginLockoutTest extends BaseTest {

    private static final String LOGIN = "/api/v1/auth/login";
    private static final String UNLOCK_SEND = "/api/v1/auth/unlock/send-code";
    private static final String UNLOCK_CONFIRM = "/api/v1/auth/unlock/confirm";

    private static final String IP_A = "10.10.0.1";
    private static final String IP_B = "10.10.0.2";
    private static final String IP_C = "10.10.0.3";
    private static final String PASSWORD = "Locked@2026";

    /** 录制到的安全告警（channel|target|message） */
    static final List<String[]> ALERTS = new CopyOnWriteArrayList<>();

    @TestConfiguration
    static class FakeSenderConfig {
        @Bean
        @Primary
        VerificationCodeSender fakeVerificationCodeSender() {
            return new VerificationCodeSender() {
                @Override
                public void sendSms(String target, String code) {
                    // 测试不需要真实投递
                }

                @Override
                public void sendEmail(String target, String code) {
                    // 测试不需要真实投递
                }

                @Override
                public void sendSecurityAlert(String target, String channel, String message) {
                    ALERTS.add(new String[]{channel, target, message});
                }
            };
        }
    }

    @Autowired
    private LoginLockoutService loginLockoutService;

    @Autowired
    private AccountUnlockService accountUnlockService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private VerificationCodeRepository verificationCodeRepository;

    @BeforeEach
    void resetLockoutState() {
        ALERTS.clear();
        verificationCodeRepository.deleteAll();
        loginLockoutService.reset();
        accountUnlockService.resetThrottleState();
    }

    // ------------------------------------------------------------------ 工具

    private User createUser(String username, String phone, String email,
                            boolean phoneVerified, boolean emailVerified) {
        return userRepository.save(java.util.Objects.requireNonNull(User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode(PASSWORD))
                .phone(phone)
                .email(email)
                .fullName(username)
                .status(User.STATUS_ACTIVE)
                .phoneVerified(phoneVerified)
                .emailVerified(emailVerified)
                .build()));
    }

    private MockHttpServletRequestBuilder login(String username, String password, String ip) {
        return post(LOGIN).header("X-Forwarded-For", ip).contentType(APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
    }

    private int loginStatus(String username, String password, String ip) throws Exception {
        return mockMvc.perform(login(username, password, ip)).andReturn().getResponse().getStatus();
    }

    private void failTimes(String username, String ip, int times) throws Exception {
        for (int i = 0; i < times; i++) {
            loginStatus(username, "wrong-password", ip);
        }
    }

    private String unlockCode(String username) throws Exception {
        String body = mockMvc.perform(post(UNLOCK_SEND).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", username))))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("devCode").asText("");
    }

    // ------------------------------------------------------------------ IP 层

    @Test
    @DisplayName("同一 IP 5 次失败 → 第 6 次 429（带 Retry-After），账户不受影响：换 IP + 正确密码 200")
    void ipLockedAfterThresholdButAccountUsable() throws Exception {
        createUser("lock1", "13800000001", "lock1@test.com", true, true);
        failTimes("lock1", IP_A, 5);

        mockMvc.perform(login("lock1", PASSWORD, IP_A))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                        .isTooManyRequests())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().exists("Retry-After"));

        assertEquals(200, loginStatus("lock1", PASSWORD, IP_B), "换 IP 后正确密码必须可登录（账户未锁）");
    }

    @Test
    @DisplayName("t136 回归：IP 锁的 Retry-After 必须是「分钟级秒数」，不能把 15 分钟算成 900000 秒")
    void ipLockRetryAfterUsesSecondsNotMillis() throws Exception {
        createUser("lock1b", "13800000011", "lock1b@test.com", true, false);
        failTimes("lock1b", IP_A, 5);

        String header = mockMvc.perform(login("lock1b", PASSWORD, IP_A))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                        .isTooManyRequests())
                .andReturn().getResponse().getHeader("Retry-After");
        assertNotNull(header, "Retry-After 必须存在");
        long seconds = Long.parseLong(header.trim());
        // 默认 ip-lock-minutes=15 → 期望 ~900s；单位错误的实现会给到 900000（≈10.4 天）
        assertTrue(seconds > 0 && seconds <= 15 * 60 + 5,
                "Retry-After 应为分钟级秒数（≤905），实际=" + seconds);
    }

    @Test
    @DisplayName("锁 IP 期间从另一个 IP 用错密码 → 升级锁账户（该请求直接 423）")
    void crossIpFailureEscalatesToAccountLock() throws Exception {
        createUser("lock2", "13800000002", "lock2@test.com", true, false);
        failTimes("lock2", IP_A, 5);

        assertEquals(423, loginStatus("lock2", "wrong-password", IP_B));
        assertEquals(423, loginStatus("lock2", PASSWORD, IP_C), "账户锁定期间任何 IP 的正确密码也拒绝");
    }

    @Test
    @DisplayName("30 分钟内来自 3 个不同 IP 均失败 → 账户锁定")
    void distinctIpsEscalate() throws Exception {
        createUser("lock3", "13800000003", "lock3@test.com", true, false);
        assertEquals(401, loginStatus("lock3", "wrong", IP_A));
        assertEquals(401, loginStatus("lock3", "wrong", IP_B));
        assertEquals(423, loginStatus("lock3", "wrong", IP_C));
    }

    @Test
    @DisplayName("锁定时发送安全告警：含 IP、不含密码与验证码；同一锁定只发一次")
    void alertSentOnceWithoutSecrets() throws Exception {
        createUser("lock4", "13800000004", "lock4@test.com", true, true);
        failTimes("lock4", IP_A, 5);
        loginStatus("lock4", "wrong", IP_B);          // 触发锁定
        loginStatus("lock4", PASSWORD, IP_C);          // 已锁定，重复尝试不应再发告警

        assertEquals(2, ALERTS.size(), "手机 + 邮箱各一条告警，且不重复发送");
        String smsAlert = ALERTS.stream().filter(a -> "sms".equals(a[0])).findFirst().orElseThrow()[2];
        assertTrue(smsAlert.contains(IP_A), "告警应包含来源 IP: " + smsAlert);
        assertFalse(smsAlert.contains(PASSWORD), "告警不得包含密码");
        assertTrue(smsAlert.contains("验证码解锁") || smsAlert.contains("sms") || smsAlert.contains("安全告警"));
    }

    // ------------------------------------------------------------------ 账户锁定响应

    @Test
    @DisplayName("423 响应体含 unlockMethods 与脱敏 target，不含完整手机号")
    void lockedResponseShape() throws Exception {
        createUser("lock5", "13800000005", "lock5@test.com", true, true);
        failTimes("lock5", IP_A, 5);
        loginStatus("lock5", "wrong", IP_B);

        String body = mockMvc.perform(login("lock5", PASSWORD, IP_C))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isLocked())
                .andReturn().getResponse().getContentAsString();
        assertTrue(body.contains("\"unlockMethods\""), body);
        assertTrue(body.contains("sms"), body);
        assertTrue(body.contains("138****0005"), "target 必须脱敏: " + body);
        assertFalse(body.contains("13800000005"), "不得回显完整手机号");
    }

    // ------------------------------------------------------------------ OTP 解锁

    @Test
    @DisplayName("OTP 解锁全链路：send-code 200 → 错码 400 → 正确码 200 → 解锁后正确密码可登录")
    void otpUnlockFullFlow() throws Exception {
        createUser("lock6", "13800000006", "lock6@test.com", true, true);
        failTimes("lock6", IP_A, 5);
        assertEquals(423, loginStatus("lock6", "wrong", IP_B));

        String code = unlockCode("lock6");
        assertFalse(code.isBlank(), "dev-mode 应回传 devCode");

        mockMvc.perform(post(UNLOCK_CONFIRM).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "lock6", "target", "13800000006", "code", "000000"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());

        mockMvc.perform(post(UNLOCK_CONFIRM).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "lock6", "target", "13800000006", "code", code))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());

        assertEquals(200, loginStatus("lock6", PASSWORD, IP_C), "解锁后正确密码必须可登录");
    }

    @Test
    @DisplayName("验证码一次性：解锁成功后复用同一验证码 → 400")
    void otpCodeCannotBeReused() throws Exception {
        createUser("lock7", "13800000007", "lock7@test.com", true, false);
        failTimes("lock7", IP_A, 5);
        loginStatus("lock7", "wrong", IP_B);
        String code = unlockCode("lock7");

        mockMvc.perform(post(UNLOCK_CONFIRM).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "lock7", "target", "13800000007", "code", code))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());

        mockMvc.perform(post(UNLOCK_CONFIRM).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "lock7", "target", "13800000007", "code", code))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    @DisplayName("解锁 target 与账号不匹配 → 400（不泄露账号是否存在）")
    void otpTargetMismatch() throws Exception {
        createUser("lock8", "13800000008", "lock8@test.com", true, false);
        createUser("lock8b", "13800000018", "lock8b@test.com", true, false);
        failTimes("lock8", IP_A, 5);
        loginStatus("lock8", "wrong", IP_B);

        mockMvc.perform(post(UNLOCK_CONFIRM).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "lock8", "target", "13800000018", "code", "123456"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
    }

    @Test
    @DisplayName("未绑定任何已验证联系方式 → 解锁码返回 NO_VERIFIED_CONTACT 提示，锁定仍生效")
    void noVerifiedContact() throws Exception {
        createUser("lock9", "13800000009", "lock9@test.com", false, false);
        failTimes("lock9", IP_A, 5);
        assertEquals(423, loginStatus("lock9", "wrong", IP_B));

        String body = mockMvc.perform(post(UNLOCK_SEND).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "lock9"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(body.contains("NO_VERIFIED_CONTACT"), "应提示联系管理员: " + body);
    }

    @Test
    @DisplayName("解锁发码限流：同 username 冷却期内再次请求 → 429")
    void unlockSendCooldown() throws Exception {
        createUser("lock10", "13800000010", "lock10@test.com", true, false);
        unlockCode("lock10");
        mockMvc.perform(post(UNLOCK_SEND).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "lock10"))))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status()
                        .isTooManyRequests());
    }

    // ------------------------------------------------------------------ 管理员解锁

    @Test
    @DisplayName("管理员 unlock → 200、审计 ACCOUNT_UNLOCKED 仅 1 条、解锁后正确密码可登录")
    void adminUnlock() throws Exception {
        User user = createUser("lock11", "13800000011", "lock11@test.com", true, true);
        failTimes("lock11", IP_A, 5);
        assertEquals(423, loginStatus("lock11", "wrong", IP_B));

        mockMvc.perform(post("/api/v1/admin/users/" + user.getId() + "/unlock")
                        .header("Authorization", adminToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());

        assertEquals(1, auditLogRepository.findByAction("ACCOUNT_UNLOCKED").size(), "一次解锁只留 1 条审计");
        assertEquals(200, loginStatus("lock11", PASSWORD, IP_C));
    }

    @Test
    @DisplayName("非 ADMIN 调用 unlock → 403")
    void adminUnlockRequiresAdmin() throws Exception {
        User user = createUser("lock12", "13800000012", "lock12@test.com", true, false);
        mockMvc.perform(post("/api/v1/admin/users/" + user.getId() + "/unlock")
                        .header("Authorization", engineerToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
    }

    // ------------------------------------------------------------------ 其他语义

    @Test
    @DisplayName("登录成功清零账户失败计数与来源 IP（不会因历史失败被提前锁定）")
    void successClearsCounters() throws Exception {
        createUser("lock13", "13800000013", "lock13@test.com", true, false);
        failTimes("lock13", IP_A, 3);
        assertEquals(200, loginStatus("lock13", PASSWORD, IP_B));

        // 清零后：再从新 IP 失败 2 次不应触发「3 个不同 IP」升级
        assertEquals(401, loginStatus("lock13", "wrong", IP_A));
        assertEquals(401, loginStatus("lock13", "wrong", IP_B));
    }

    @Test
    @DisplayName("不泄露账号是否存在：不存在的用户名与存在但密码错返回同形 401")
    void unknownUsernameSameShape() throws Exception {
        createUser("lock14", "13800000014", "lock14@test.com", true, false);
        String existing = mockMvc.perform(login("lock14", "wrong", IP_A))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        String missing = mockMvc.perform(login("no_such_user_x", "wrong", IP_A))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        assertEquals(existing, missing, "两种失败必须完全同形，避免账号枚举");
    }

    @Test
    @DisplayName("锁定动作写 ACCOUNT_LOCKED 审计（含原因与 IP 列表，仅 1 条）")
    void lockAuditWritten() throws Exception {
        createUser("lock15", "13800000015", "lock15@test.com", true, false);
        failTimes("lock15", IP_A, 5);
        loginStatus("lock15", "wrong", IP_B);

        var logs = auditLogRepository.findByAction("ACCOUNT_LOCKED");
        assertEquals(1, logs.size());
        String details = logs.get(0).getDetails();
        assertTrue(details.contains("lock15"), details);
        assertTrue(details.contains("ips="), details);
        assertTrue(details.contains(IP_A), details);
    }

    @Test
    @DisplayName("锁定后继续试错的失败尝试不会重复写锁定审计")
    void repeatedAttemptsDoNotDuplicateAudit() throws Exception {
        createUser("lock16", "13800000016", "lock16@test.com", true, false);
        failTimes("lock16", IP_A, 5);
        loginStatus("lock16", "wrong", IP_B);
        loginStatus("lock16", "wrong", IP_C);
        loginStatus("lock16", PASSWORD, IP_A);
        assertEquals(1, auditLogRepository.findByAction("ACCOUNT_LOCKED").size());
    }
}
