package com.vulnaudit.controller;

import com.vulnaudit.config.VerificationProperties;
import com.vulnaudit.entity.RegistrationRequest;
import com.vulnaudit.entity.VerificationCode;
import com.vulnaudit.entity.enums.RegistrationStatus;
import com.vulnaudit.entity.enums.VerificationChannel;
import com.vulnaudit.entity.enums.VerificationPurpose;
import com.vulnaudit.repository.AuditLogRepository;
import com.vulnaudit.repository.RegistrationRequestRepository;
import com.vulnaudit.repository.VerificationCodeRepository;
import com.vulnaudit.service.VerificationCodeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 自助注册 → 管理员审批 → 登录拦截 全链路测试（t102）
 *
 * <p>覆盖：手机号/邮箱格式、验证码必填/错误/过期/已消费、邮箱未验证、重复用户名/手机号/邮箱（409）、
 * 注册成功 201 且 pending_approval 无法登录（403）、审批通过后可登录且 phone_verified=true、
 * 拒绝后登录 403、非管理员审批 403、5 分钟内重复发码 429、dev-mode 关闭时响应不含 devCode、
 * 列表可见与重复审批 409、审计落库。
 */
@DisplayName("自助注册与审批全链路测试")
public class RegistrationFlowTest extends BaseTest {

    private static final String SEND_CODE = "/api/v1/auth/register/send-code";
    private static final String REGISTER = "/api/v1/auth/register";
    private static final String ADMIN_REQUESTS = "/api/v1/admin/registration-requests";

    private static final String PHONE = "13800001111";
    private static final String PASSWORD = "Passw0rd!";

    @Autowired
    private RegistrationRequestRepository registrationRequestRepository;

    @Autowired
    private VerificationCodeRepository verificationCodeRepository;

    @Autowired
    private VerificationCodeService verificationCodeService;

    @Autowired
    private VerificationProperties verificationProperties;

    @Autowired
    private AuditLogRepository auditLogRepository;

    // ------------------------------------------------------------------ 工具

    private String sendCode(String channel, String target, String purpose) throws Exception {
        String body = mockMvc.perform(post(SEND_CODE).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "channel", channel, "target", target, "purpose", purpose))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String devCode = objectMapper.readTree(body).path("data").path("devCode").asText(null);
        assertNotNull(devCode, "测试环境 dev-mode 必须回传 devCode: " + body);
        return devCode;
    }

    private String registerJson(String username, String phone, String code,
                                String email, String emailCode) throws Exception {
        var payload = new java.util.HashMap<String, Object>();
        payload.put("username", username);
        payload.put("password", PASSWORD);
        payload.put("phone", phone);
        payload.put("code", code);
        if (email != null) {
            payload.put("email", email);
        }
        if (emailCode != null) {
            payload.put("emailCode", emailCode);
        }
        return objectMapper.writeValueAsString(payload);
    }

    private String register(String username, String phone, String code,
                            String email, String emailCode) throws Exception {
        return mockMvc.perform(post(REGISTER).contentType(APPLICATION_JSON)
                        .content(registerJson(username, phone, code, email, emailCode)))
                .andReturn().getResponse().getContentAsString();
    }

    private RegistrationRequest latestRequest(String username) {
        return registrationRequestRepository.findByUsername(username).stream()
                .max(java.util.Comparator.comparing(RegistrationRequest::getCreatedAt))
                .orElseThrow();
    }

    private int loginStatus(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "password", password))))
                .andReturn().getResponse().getStatus();
    }

    private String loginBody(String username, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username, "password", password))))
                .andReturn().getResponse().getContentAsString();
    }

    // ------------------------------------------------------------------ 手机号 / 邮箱格式

    @Test
    @DisplayName("手机号格式非法（非 11 位 / 号段非法 / 非数字）→ 400")
    void invalidPhoneFormatRejected() throws Exception {
        for (String bad : new String[]{"12345", "12345678901", "2380000111a", "+8613800001111"}) {
            mockMvc.perform(post(SEND_CODE).contentType(APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "channel", "sms", "target", bad, "purpose", "REGISTER"))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message", containsString("手机号格式不正确")));
        }
    }

    @Test
    @DisplayName("邮箱格式非法 → 400")
    void invalidEmailFormatRejected() throws Exception {
        mockMvc.perform(post(SEND_CODE).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "channel", "email", "target", "not-an-email", "purpose", "REGISTER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("邮箱格式不正确")));
    }

    @Test
    @DisplayName("未知用途 / 未知渠道 → 400")
    void unknownPurposeOrChannelRejected() throws Exception {
        mockMvc.perform(post(SEND_CODE).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "channel", "sms", "target", PHONE, "purpose", "SOMETHING"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("暂不支持该验证码用途")));

        mockMvc.perform(post(SEND_CODE).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "channel", "carrier-pigeon", "target", PHONE, "purpose", "REGISTER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("不支持的验证码渠道")));
    }

    // ------------------------------------------------------------------ 验证码

    @Test
    @DisplayName("未发送验证码直接注册 → 400")
    void registerWithoutCodeRejected() throws Exception {
        mockMvc.perform(post(REGISTER).contentType(APPLICATION_JSON)
                        .content(registerJson("newbie1", "13800002222", "123456", null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("请先获取验证码")));
    }

    @Test
    @DisplayName("验证码错误 → 400，且不落库、不建占位用户")
    void wrongCodeRejected() throws Exception {
        sendCode("sms", PHONE, "REGISTER");
        mockMvc.perform(post(REGISTER).contentType(APPLICATION_JSON)
                        .content(registerJson("newbie2", PHONE, "000000", null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("验证码错误")));

        assertTrue(registrationRequestRepository.findByUsername("newbie2").isEmpty(), "失败不应留下申请");
        assertTrue(userRepository.findByUsername("newbie2").isEmpty(), "失败不应留下占位用户");
    }

    @Test
    @DisplayName("验证码过期 → 400")
    void expiredCodeRejected() throws Exception {
        sendCode("sms", PHONE, "REGISTER");
        VerificationCode code = verificationCodeRepository
                .findFirstByTargetAndPurposeAndConsumedAtIsNullOrderByCreatedAtDesc(PHONE, VerificationPurpose.REGISTER)
                .orElseThrow();
        code.setExpiresAt(LocalDateTime.now().minusMinutes(1));
        verificationCodeRepository.save(code);

        mockMvc.perform(post(REGISTER).contentType(APPLICATION_JSON)
                        .content(registerJson("newbie3", PHONE, "123456", null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("验证码已过期")));
    }

    @Test
    @DisplayName("验证码已消费 → 400（一次性）")
    void consumedCodeRejected() throws Exception {
        String devCode = sendCode("sms", PHONE, "REGISTER");
        // 先用服务层消费掉该验证码
        verificationCodeService.verifyAndConsume(PHONE, VerificationChannel.SMS,
                VerificationPurpose.REGISTER, devCode);

        mockMvc.perform(post(REGISTER).contentType(APPLICATION_JSON)
                        .content(registerJson("newbie4", PHONE, devCode, null, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("请先获取验证码")));
    }

    @Test
    @DisplayName("填写邮箱但未提供/未通过邮箱验证码 → 400")
    void emailWithoutVerificationRejected() throws Exception {
        String phoneCode = sendCode("sms", PHONE, "REGISTER");

        mockMvc.perform(post(REGISTER).contentType(APPLICATION_JSON)
                        .content(registerJson("newbie5", PHONE, phoneCode, "newbie5@test.com", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("必须同时通过邮箱验证码")));

        mockMvc.perform(post(REGISTER).contentType(APPLICATION_JSON)
                        .content(registerJson("newbie5", PHONE, phoneCode, "newbie5@test.com", "000000")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("请先获取验证码")));
    }

    @Test
    @DisplayName("同一 target 5 分钟内重复发码 → 429")
    void resendWithinIntervalRejected() throws Exception {
        sendCode("sms", PHONE, "REGISTER");
        mockMvc.perform(post(SEND_CODE).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "channel", "sms", "target", PHONE, "purpose", "REGISTER"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message", containsString("过于频繁")));
    }

    @Test
    @DisplayName("dev-mode 关闭时响应不含 devCode")
    void devModeOffHidesDevCode() throws Exception {
        boolean original = verificationProperties.isDevMode();
        try {
            verificationProperties.setDevMode(false);
            mockMvc.perform(post(SEND_CODE).contentType(APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of(
                                    "channel", "sms", "target", "13800003333", "purpose", "REGISTER"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.devCode").doesNotExist())
                    .andExpect(jsonPath("$.data.expiresInSeconds").value(300));
        } finally {
            verificationProperties.setDevMode(original);
        }
    }

    // ------------------------------------------------------------------ 唯一性

    @Test
    @DisplayName("用户名/手机号/邮箱与已有用户或待审批申请重复 → 409")
    void duplicatesRejected() throws Exception {
        var admin = userRepository.findById(adminUserId).orElseThrow();

        // 1) 用户名与既有用户重复
        String code1 = sendCode("sms", PHONE, "REGISTER");
        mockMvc.perform(post(REGISTER).contentType(APPLICATION_JSON)
                        .content(registerJson(admin.getUsername(), PHONE, code1, null, null)))
                .andExpect(status().isConflict());

        // 2) 邮箱与既有用户重复
        String code2 = sendCode("sms", "13800004444", "REGISTER");
        mockMvc.perform(post(REGISTER).contentType(APPLICATION_JSON)
                        .content(registerJson("newbie6", "13800004444", code2, admin.getEmail(), null)))
                .andExpect(status().isConflict());

        // 3) 先成功注册一个，再用同一手机号申请（唯一性在验证码校验之前拦下）→ 409
        String code3 = sendCode("sms", "13800005555", "REGISTER");
        assertEquals(201, mockMvc.perform(post(REGISTER).contentType(APPLICATION_JSON)
                        .content(registerJson("newbie7", "13800005555", code3, null, null)))
                .andReturn().getResponse().getStatus());

        mockMvc.perform(post(REGISTER).contentType(APPLICATION_JSON)
                        .content(registerJson("newbie8", "13800005555", "123456", null, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("手机号已被注册")));
    }

    // ------------------------------------------------------------------ 主链路

    @Test
    @DisplayName("注册成功 201：库中 PENDING + 占位用户 pending_approval，且**无法登录**（403）")
    void registerThenCannotLogin() throws Exception {
        String devCode = sendCode("sms", PHONE, "REGISTER");
        mockMvc.perform(post(REGISTER).contentType(APPLICATION_JSON)
                        .content(registerJson("selfreg1", PHONE, devCode, null, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.username").value("selfreg1"))
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.phoneVerified").value(true));

        RegistrationRequest request = latestRequest("selfreg1");
        assertEquals(RegistrationStatus.PENDING, request.getStatus());
        assertEquals(PHONE, request.getPhone());
        assertFalse(request.isEmailVerified());

        var placeholder = userRepository.findByUsername("selfreg1").orElseThrow();
        assertEquals("pending_approval", placeholder.getStatus());
        assertTrue(placeholder.isPhoneVerified());

        // 待审批登录 → 403，且不签发 token
        String body = loginBody("selfreg1", PASSWORD);
        assertTrue(body.contains("账号待管理员审批"), "应返回待审批文案: " + body);
        assertFalse(body.contains("\"token\""), "待审批用户不得拿到 token: " + body);
    }

    @Test
    @DisplayName("管理员审批通过 → 用户 ACTIVE、phone_verified=true，可正常登录；重复审批 409")
    void approveThenLogin() throws Exception {
        String devCode = sendCode("sms", PHONE, "REGISTER");
        assertEquals(201, mockMvc.perform(post(REGISTER).contentType(APPLICATION_JSON)
                        .content(registerJson("selfreg2", PHONE, devCode, null, null)))
                .andReturn().getResponse().getStatus());
        RegistrationRequest request = latestRequest("selfreg2");
        assertEquals(403, loginStatus("selfreg2", PASSWORD), "审批前不可登录");

        // 列表可见（PENDING）
        mockMvc.perform(get(ADMIN_REQUESTS).param("status", "PENDING").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].username").value("selfreg2"));

        mockMvc.perform(post(ADMIN_REQUESTS + "/" + request.getId() + "/approve")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginEnabled").value(true));

        var user = userRepository.findByUsername("selfreg2").orElseThrow();
        assertEquals("active", user.getStatus());
        assertTrue(user.isPhoneVerified(), "审批通过后应标记手机已验证");
        assertEquals(PHONE, user.getPhone());
        assertEquals(RegistrationStatus.APPROVED, latestRequest("selfreg2").getStatus());

        assertEquals(200, loginStatus("selfreg2", PASSWORD), "审批通过后应可登录");

        // 重复审批 → 409
        mockMvc.perform(post(ADMIN_REQUESTS + "/" + request.getId() + "/approve")
                        .header("Authorization", adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("管理员拒绝 → 登录 403「注册申请已被拒绝」，申请状态 REJECTED，审计落库")
    void rejectThenLogin() throws Exception {
        String devCode = sendCode("sms", PHONE, "REGISTER");
        assertEquals(201, mockMvc.perform(post(REGISTER).contentType(APPLICATION_JSON)
                        .content(registerJson("selfreg3", PHONE, devCode, null, null)))
                .andReturn().getResponse().getStatus());
        RegistrationRequest request = latestRequest("selfreg3");

        mockMvc.perform(post(ADMIN_REQUESTS + "/" + request.getId() + "/reject")
                        .header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"note\":\"手机号与工号不匹配\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.reviewNote").value("手机号与工号不匹配"));

        // t111（captain 口径）：拒绝即释放唯一键 → 占位用户被删除（或用户名被归档改写），
        // 用**原用户名**登录就是「用户不存在」，返回 **401 通用文案**，而不再返回 403「注册申请已被拒绝」——
        // 403 会向攻击者确认「该账号确实申请过、只是被拒」，属于账号枚举信息泄露。
        assertEquals(401, loginStatus("selfreg3", PASSWORD));
        String body = loginBody("selfreg3", PASSWORD);
        assertTrue(body.contains("用户名或密码错误"), "应返回通用文案: " + body);
        assertFalse(body.contains("注册申请已被拒绝"), "不得泄露「曾被拒绝」这一事实: " + body);

        var placeholder = userRepository.findByUsername("selfreg3");
        assertTrue(placeholder.isEmpty(), "从未登录且无关联的占位用户应被直接删除（释放唯一键）");

        assertTrue(auditLogRepository.findByAction("REGISTRATION_REJECT").stream()
                        .anyMatch(log -> log.getDetails() != null && log.getDetails().contains("selfreg3")),
                "拒绝应写 REGISTRATION_REJECT 审计");
    }

    @Test
    @DisplayName("非管理员调用审批/列表 → 403")
    void nonAdminRejected() throws Exception {
        String devCode = sendCode("sms", PHONE, "REGISTER");
        assertEquals(201, mockMvc.perform(post(REGISTER).contentType(APPLICATION_JSON)
                        .content(registerJson("selfreg4", PHONE, devCode, null, null)))
                .andReturn().getResponse().getStatus());
        RegistrationRequest request = latestRequest("selfreg4");

        mockMvc.perform(get(ADMIN_REQUESTS).header("Authorization", engineerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(ADMIN_REQUESTS + "/" + request.getId() + "/approve")
                        .header("Authorization", engineerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(ADMIN_REQUESTS + "/" + request.getId() + "/reject")
                        .header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON).content("{\"note\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("审批通过写 REGISTRATION_APPROVE 审计（含申请人），且只 1 条")
    void approveWritesSingleAudit() throws Exception {
        String devCode = sendCode("sms", PHONE, "REGISTER");
        assertEquals(201, mockMvc.perform(post(REGISTER).contentType(APPLICATION_JSON)
                        .content(registerJson("selfreg5", PHONE, devCode, null, null)))
                .andReturn().getResponse().getStatus());
        RegistrationRequest request = latestRequest("selfreg5");

        mockMvc.perform(post(ADMIN_REQUESTS + "/" + request.getId() + "/approve")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk());

        List<com.vulnaudit.entity.AuditLog> logs = auditLogRepository.findByAction("REGISTRATION_APPROVE");
        assertEquals(1, logs.size(), "一次审批应只产生 1 条语义化审计（拦截器已跳过）");
        assertTrue(logs.get(0).getDetails().contains("selfreg5"));
        assertEquals(adminUserId, logs.get(0).getUserId());
    }

    @Test
    @DisplayName("带邮箱注册：邮箱也验证通过后 email_verified=true")
    void registerWithEmailVerified() throws Exception {
        String phoneCode = sendCode("sms", PHONE, "REGISTER");
        String emailCode = sendCode("email", "SelfReg6@Test.com", "REGISTER");

        mockMvc.perform(post(REGISTER).contentType(APPLICATION_JSON)
                        .content(registerJson("selfreg6", PHONE, phoneCode, "SelfReg6@Test.com", emailCode)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.emailVerified").value(true));

        RegistrationRequest request = latestRequest("selfreg6");
        assertTrue(request.isEmailVerified());
        assertEquals("selfreg6@test.com", request.getEmail(), "邮箱应归一化为小写");
    }
}
