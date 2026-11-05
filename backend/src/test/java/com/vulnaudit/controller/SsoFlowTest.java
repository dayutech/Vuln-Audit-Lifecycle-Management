package com.vulnaudit.controller;

import com.vulnaudit.entity.SsoBinding;
import com.vulnaudit.entity.SsoPendingBinding;
import com.vulnaudit.entity.SsoProvider;
import com.vulnaudit.entity.User;
import com.vulnaudit.repository.AuditLogRepository;
import com.vulnaudit.repository.RegistrationRequestRepository;
import com.vulnaudit.repository.SsoBindingRepository;
import com.vulnaudit.repository.SsoPendingBindingRepository;
import com.vulnaudit.repository.SsoProviderRepository;
import com.vulnaudit.service.sso.SsoClient;
import com.vulnaudit.service.sso.SsoClientFactory;
import com.vulnaudit.service.sso.SsoUserInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import java.time.LocalDateTime;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SSO 登录 / 待审批 / 绑定解绑全链路测试（t104）
 *
 * <p>用 {@link FakeSsoClientFactory} 替换真实 HTTP 客户端：{@code exchange(code)} 直接把 code 当作
 * externalId 返回，因此用例用不同的 code 模拟不同第三方账号，不触网。
 */
@DisplayName("SSO 登录与绑定全链路测试")
@Import(SsoFlowTest.FakeSsoConfig.class)
public class SsoFlowTest extends BaseTest {

    private static final String PROVIDERS = "/api/v1/auth/sso/providers";
    private static final String CALLBACK = "/api/v1/auth/sso/github/callback";
    private static final String ADMIN_TOGGLE = "/api/v1/admin/sso/providers/github";
    private static final String MY_BINDINGS = "/api/v1/users/me/sso-bindings";

    @TestConfiguration
    static class FakeSsoConfig {
        @Bean
        @Primary
        SsoClientFactory fakeSsoClientFactory() {
            return provider -> new SsoClient() {
                @Override
                public String authorizeUrl(String state, String redirectUri) {
                    return "https://fake-" + provider.getCode() + ".test/authorize?state=" + state;
                }

                @Override
                public SsoUserInfo exchange(String code) {
                    return new SsoUserInfo(provider.getCode(), code, code + "@example.com", code);
                }
            };
        }
    }

    @Autowired
    private SsoProviderRepository ssoProviderRepository;

    @Autowired
    private SsoBindingRepository ssoBindingRepository;

    @Autowired
    private SsoPendingBindingRepository ssoPendingBindingRepository;

    @Autowired
    private RegistrationRequestRepository registrationRequestRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @BeforeEach
    void setUpSso() {
        // 提供商由启动器初始化；这里按用例需要调整「开启 / 配凭据」
        configure("github", true, true);
        configure("google", false, false);
        configure("microsoft", false, false);
        configure("wechat", false, false);
    }

    private void configure(String code, boolean enabled, boolean withCredentials) {
        SsoProvider provider = ssoProviderRepository.findByCode(code).orElseThrow();
        provider.setEnabled(enabled);
        provider.setClientId(withCredentials ? "client-" + code : null);
        provider.setClientSecret(withCredentials ? "secret-" + code : null);
        ssoProviderRepository.save(provider);
    }

    private String authorizeState() throws Exception {
        String body = mockMvc.perform(get("/api/v1/auth/sso/github/authorize-url")
                        .param("redirectUri", "http://localhost:8089/sso/callback"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("state").asText();
    }

    private String callback(String code, String state) throws Exception {
        return mockMvc.perform(post(CALLBACK).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", code, "state", state))))
                .andReturn().getResponse().getContentAsString();
    }

    // ------------------------------------------------------------------ 提供商

    @Test
    @DisplayName("providers 只返回「已开启且已配置」的提供商")
    void providersOnlyUsable() throws Exception {
        configure("google", true, false); // 开启但未配凭据 → 不应出现
        configure("microsoft", true, true); // 开启且已配置 → 应出现

        mockMvc.perform(get(PROVIDERS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[?(@.code=='github')]").exists())
                .andExpect(jsonPath("$.data[?(@.code=='microsoft')]").exists());
    }

    @Test
    @DisplayName("未配置凭据的提供商：authorize-url → 503「尚未完成配置」")
    void unconfiguredProviderReturns503() throws Exception {
        configure("google", true, false);
        mockMvc.perform(get("/api/v1/auth/sso/google/authorize-url"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message", containsString("尚未完成配置")));
    }

    @Test
    @DisplayName("未开启的提供商：authorize-url → 400")
    void disabledProviderReturns400() throws Exception {
        configure("google", false, true);
        mockMvc.perform(get("/api/v1/auth/sso/google/authorize-url"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("未开放")));
    }

    @Test
    @DisplayName("已开启且已配置：authorize-url 返回 url 与一次性 state")
    void authorizeUrlOk() throws Exception {
        String state = authorizeState();
        assertNotNull(state);
        assertFalse(state.isBlank());
    }

    // ------------------------------------------------------------------ state 校验

    @Test
    @DisplayName("state 无效 → 400；同一 state 重放 → 400（一次性）")
    void invalidAndReplayedState() throws Exception {
        mockMvc.perform(post(CALLBACK).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "alice", "state", "bogus"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("登录状态已失效")));

        String state = authorizeState();
        mockMvc.perform(post(CALLBACK).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "alice", "state", state))))
                .andExpect(status().isAccepted());

        mockMvc.perform(post(CALLBACK).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "alice", "state", state))))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ 首次登录 → 审批

    @Test
    @DisplayName("首次回调：202、不签发 token、产生 PENDING 申请 + PENDING 绑定 + pending_approval 占位用户")
    void firstCallbackCreatesPending() throws Exception {
        String state = authorizeState();
        String body = mockMvc.perform(post(CALLBACK).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "alice", "state", state))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.data.login").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertFalse(body.contains("\"token\""), "首次登录不得签发 token: " + body);

        var request = registrationRequestRepository.findByUsername("alice").stream().findFirst().orElseThrow();
        assertEquals("SSO", request.getSource());
        assertEquals("github", request.getProviderCode());
        assertEquals("alice", request.getExternalId());

        SsoPendingBinding pending = ssoPendingBindingRepository
                .findByProviderCodeAndExternalId("github", "alice").orElseThrow();
        assertEquals("PENDING", pending.getStatus());

        User placeholder = userRepository.findByUsername("alice").orElseThrow();
        assertEquals("pending_approval", placeholder.getStatus());
    }

    @Test
    @DisplayName("重复首次回调（同 externalId）仍 202，不重复建申请")
    void repeatedFirstCallbackIsIdempotent() throws Exception {
        callback("alice", authorizeState());
        long before = registrationRequestRepository.findByUsername("alice").size();

        callback("alice", authorizeState());
        assertEquals(before, registrationRequestRepository.findByUsername("alice").size());
    }

    @Test
    @DisplayName("审批通过后同一 SSO 回调 → 200 且签发 token（可直接访问受保护接口）")
    void approveThenCallbackIssuesToken() throws Exception {
        callback("bob", authorizeState());
        String requestId = registrationRequestRepository.findByUsername("bob").stream()
                .findFirst().orElseThrow().getId().toString();

        mockMvc.perform(post("/api/v1/admin/registration-requests/" + requestId + "/approve")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk());

        assertEquals("active", userRepository.findByUsername("bob").orElseThrow().getStatus());
        assertFalse(ssoBindingRepository.findByProviderCodeAndExternalId("github", "bob").isEmpty(),
                "审批通过后应激活 SSO 绑定");

        String body = mockMvc.perform(post(CALLBACK).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "bob", "state", authorizeState()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("LOGIN"))
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(body).path("data").path("login").path("token").asText();
        assertFalse(token.isBlank(), "应签发本地 JWT");

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("bob"));
    }

    @Test
    @DisplayName("已绑定但账号非 ACTIVE（暂停）→ 403")
    void boundButNotActiveReturns403() throws Exception {
        User user = userRepository.findById(engineerUserId).orElseThrow();
        user.setStatus("suspended");
        userRepository.save(user);
        ssoBindingRepository.save(SsoBinding.builder()
                .userId(user.getId())
                .providerCode("github")
                .externalId("carol")
                .displayName("carol")
                .boundAt(LocalDateTime.now())
                .build());

        mockMvc.perform(post(CALLBACK).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "carol", "state", authorizeState()))))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ 绑定 / 解绑

    @Test
    @DisplayName("已登录用户绑定：start 返回授权地址 → 回调 200 BOUND → 列表可见；重复绑定 409")
    void bindAndDuplicate() throws Exception {
        String startBody = mockMvc.perform(post(MY_BINDINGS + "/github/start")
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String state = objectMapper.readTree(startBody).path("data").path("state").asText();

        mockMvc.perform(post(CALLBACK).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "dave", "state", state))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("BOUND"));

        mockMvc.perform(get(MY_BINDINGS).header("Authorization", engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].code").value("github"))
                .andExpect(jsonPath("$.data[0].externalId").value("dave"));

        mockMvc.perform(post(MY_BINDINGS + "/github/start").header("Authorization", engineerToken))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("解绑：密码错误 403；密码正确成功后列表为空，且该身份回调回到 202")
    void unbindRequiresPassword() throws Exception {
        String state = objectMapper.readTree(mockMvc.perform(post(MY_BINDINGS + "/github/start")
                        .header("Authorization", engineerToken))
                .andReturn().getResponse().getContentAsString()).path("data").path("state").asText();
        mockMvc.perform(post(CALLBACK).contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("code", "erin", "state", state))))
                .andExpect(status().isOk());

        mockMvc.perform(delete(MY_BINDINGS + "/github")
                        .header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"password\":\"wrong-password\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message", containsString("当前密码不正确")));

        mockMvc.perform(delete(MY_BINDINGS + "/github")
                        .header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"password\":\"engineer123\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get(MY_BINDINGS).header("Authorization", engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        // 解绑后该第三方身份再登录 → 回到「待审批」
        mockMvc.perform(post(CALLBACK).contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("code", "erin", "state", authorizeState()))))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("PENDING_APPROVAL"));
    }

    // ------------------------------------------------------------------ 管理员开关

    @Test
    @DisplayName("非管理员改开关 → 403；管理员开关生效并写 UPDATE_SSO_PROVIDER 审计（仅 1 条）")
    void toggleRequiresAdminAndWritesAudit() throws Exception {
        mockMvc.perform(patch(ADMIN_TOGGLE).header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch(ADMIN_TOGGLE).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));

        assertFalse(ssoProviderRepository.findByCode("github").orElseThrow().isEnabled());

        var logs = auditLogRepository.findByAction("UPDATE_SSO_PROVIDER");
        assertEquals(1, logs.size(), "一次开关应只产生 1 条审计（拦截器已跳过）");
        assertTrue(logs.get(0).getDetails().contains("provider=github"));
        assertTrue(logs.get(0).getDetails().contains("enabled=false"));
    }

    @Test
    @DisplayName("关闭后 providers 列表不再包含该方式，authorize-url 400")
    void disabledProviderDisappears() throws Exception {
        mockMvc.perform(patch(ADMIN_TOGGLE).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(get(PROVIDERS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(get("/api/v1/auth/sso/github/authorize-url"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("未知 provider：authorize-url 400")
    void unknownProviderReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/auth/sso/gitlab/authorize-url"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("不支持的登录方式")));
    }
}
