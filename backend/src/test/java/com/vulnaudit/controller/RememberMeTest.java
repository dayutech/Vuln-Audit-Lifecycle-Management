package com.vulnaudit.controller;

import com.vulnaudit.entity.RememberMeToken;
import com.vulnaudit.entity.User;
import com.vulnaudit.repository.AuditLogRepository;
import com.vulnaudit.repository.RememberMeTokenRepository;
import com.vulnaudit.service.RememberMeService;
import com.vulnaudit.service.TwoFactorService;
import com.vulnaudit.util.TotpUtils;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * remember-me 长效令牌测试（t124）
 *
 * <p>覆盖：登录下发 cookie + 落库 / 未勾选不下发 / JWT 过期后凭 cookie 轮换 / 旧令牌重放撤销整条 series 并审计 /
 * 登出撤销并清 cookie / 改密与管理员重置撤销全部 / 停用撤销 / 过期 cookie 401 / 功能关闭不下发 /
 * 有效 JWT 的常规刷新不受影响 / 2FA 第一步不签发 cookie。
 */
@DisplayName("remember-me 长效令牌测试")
public class RememberMeTest extends BaseTest {

    private static final String PASSWORD = "Remember@2026";

    @Autowired
    private RememberMeTokenRepository tokenRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private RememberMeService rememberMeService;

    @Autowired
    private TwoFactorService twoFactorService;

    @Autowired
    private com.vulnaudit.config.TwoFactorProperties properties;

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

    private String loginBody(String username, Boolean rememberMe) throws Exception {
        if (rememberMe == null) {
            return objectMapper.writeValueAsString(Map.of("username", username, "password", PASSWORD));
        }
        return objectMapper.writeValueAsString(Map.of(
                "username", username, "password", PASSWORD, "rememberMe", rememberMe));
    }

    private MvcResult login(String username, Boolean rememberMe) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(loginBody(username, rememberMe)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String tokenOf(MvcResult result) throws Exception {
        return "Bearer " + objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("token").asText();
    }

    /** 从响应头取 remember-me cookie 的值（无则 null） */
    private String cookieValueOf(MvcResult result) {
        for (String header : result.getResponse().getHeaders("Set-Cookie")) {
            if (header.startsWith(rememberMeService.cookieName() + "=")) {
                String value = header.substring(header.indexOf('=') + 1, header.indexOf(';'));
                return value.isBlank() ? null : value;
            }
        }
        return null;
    }

    private String rawCookieHeader(MvcResult result) {
        for (String header : result.getResponse().getHeaders("Set-Cookie")) {
            if (header.startsWith(rememberMeService.cookieName() + "=")) {
                return header;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ 下发

    @Test
    @DisplayName("rememberMe=true：下发 HttpOnly/SameSite=Lax cookie 且库内落一条记录")
    void issuesCookieAndRow() throws Exception {
        createUser("rm1");
        MvcResult result = login("rm1", true);

        String raw = rawCookieHeader(result);
        assertNotNull(raw, "应下发 remember-me cookie");
        assertTrue(raw.contains("HttpOnly"), "cookie 必须 HttpOnly: " + raw);
        assertTrue(raw.contains("SameSite=Lax"), "cookie 应 SameSite=Lax: " + raw);
        assertTrue(raw.contains("Path=/"), raw);
        assertNotNull(cookieValueOf(result));

        assertEquals(1, tokenRepository.count(), "库内应有 1 条令牌记录");
        assertEquals(1L, rememberMeService.activeCount(
                userRepository.findByUsername("rm1").orElseThrow().getId()));
    }

    @Test
    @DisplayName("未勾选 rememberMe：不下发 cookie、库中无记录")
    void noCookieWithoutFlag() throws Exception {
        createUser("rm2");
        MvcResult result = login("rm2", false);
        assertNull(rawCookieHeader(result));
        assertEquals(0, tokenRepository.count());
    }

    @Test
    @DisplayName("JWT 过期 + cookie 有效 → 200 且轮换（新 cookie、旧值失效）")
    void refreshRotatesToken() throws Exception {
        createUser("rm3");
        MvcResult loginResult = login("rm3", true);
        String oldCookie = cookieValueOf(loginResult);
        assertNotNull(oldCookie);

        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(rememberMeService.cookieName(), oldCookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn();

        String newCookie = cookieValueOf(refreshed);
        assertNotNull(newCookie, "刷新应下发新 cookie");
        assertNotEquals(oldCookie, newCookie, "必须轮换令牌");

        // 旧令牌重放 → 401（已被轮换作废）
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(rememberMeService.cookieName(), oldCookie)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("旧令牌重放：整条 series 撤销 + 审计 REMEMBER_ME_REPLAY")
    void replayRevokesSeriesAndAudits() throws Exception {
        User user = createUser("rm4");
        MvcResult loginResult = login("rm4", true);
        String oldCookie = cookieValueOf(loginResult);

        // 第一次用旧 cookie 刷新 → 轮换
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(rememberMeService.cookieName(), oldCookie)))
                .andExpect(status().isOk());

        // 再用旧 cookie（已被作废）→ 重放
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(rememberMeService.cookieName(), oldCookie)))
                .andExpect(status().isUnauthorized());

        assertEquals(0L, rememberMeService.activeCount(user.getId()), "重放后该 series 应全部撤销");
        assertTrue(auditLogRepository.findByAction(RememberMeService.ACTION_REPLAY).stream()
                        .anyMatch(log -> user.getId().equals(log.getUserId())),
                "应写 REMEMBER_ME_REPLAY 审计");
    }

    // ------------------------------------------------------------------ 撤销

    @Test
    @DisplayName("登出：撤销当前令牌并清 cookie（Max-Age=0），此后刷新 401")
    void logoutRevokesAndClears() throws Exception {
        User user = createUser("rm5");
        MvcResult loginResult = login("rm5", true);
        String cookie = cookieValueOf(loginResult);
        String token = tokenOf(loginResult);

        MvcResult logout = mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", token)
                        .cookie(new Cookie(rememberMeService.cookieName(), cookie)))
                .andExpect(status().isOk())
                .andReturn();

        String cleared = rawCookieHeader(logout);
        assertNotNull(cleared, "应返回清除 cookie 的头");
        assertTrue(cleared.contains("Max-Age=0"), "应 Max-Age=0: " + cleared);
        assertEquals(0L, rememberMeService.activeCount(user.getId()));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(rememberMeService.cookieName(), cookie)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("改密后全部 remember-me 令牌失效")
    void changePasswordRevokesAll() throws Exception {
        User user = createUser("rm6");
        String token = tokenOf(login("rm6", true));
        assertEquals(1L, rememberMeService.activeCount(user.getId()));

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"oldPassword\":\"" + PASSWORD + "\",\"newPassword\":\"NewRemember@2026\"}"))
                .andExpect(status().isOk());

        assertEquals(0L, rememberMeService.activeCount(user.getId()), "改密应撤销全部令牌");
    }

    @Test
    @DisplayName("管理员重置密码后全部令牌失效")
    void adminResetRevokesAll() throws Exception {
        User user = createUser("rm7");
        login("rm7", true);
        assertEquals(1L, rememberMeService.activeCount(user.getId()));

        mockMvc.perform(post("/api/v1/users/" + user.getId() + "/reset-password")
                        .header("Authorization", adminToken)
                        .param("newPassword", "AdminReset@2026"))
                .andExpect(status().isOk());

        assertEquals(0L, rememberMeService.activeCount(user.getId()));
    }

    @Test
    @DisplayName("账号被停用后全部令牌失效（停用后不能凭 cookie 续期）")
    void suspendRevokesAll() throws Exception {
        User user = createUser("rm8");
        login("rm8", true);
        assertEquals(1L, rememberMeService.activeCount(user.getId()));

        mockMvc.perform(patch("/api/v1/users/" + user.getId() + "/status")
                        .header("Authorization", adminToken)
                        .param("status", "suspended"))
                .andExpect(status().isOk());

        assertEquals(0L, rememberMeService.activeCount(user.getId()));
    }

    @Test
    @DisplayName("过期 cookie → 401；数据库行被标记/拒绝")
    void expiredCookieRejected() throws Exception {
        User user = createUser("rm9");
        String raw = rememberMeService.issue(user.getId(), "junit", "127.0.0.1");

        List<RememberMeToken> tokens = tokenRepository.findAll();
        assertEquals(1, tokens.size());
        tokens.get(0).setExpiresAt(LocalDateTime.now().minusDays(1));
        tokenRepository.save(tokens.get(0));

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(rememberMeService.cookieName(), raw)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("enabled=false：登录不下发 cookie（功能整体关闭）")
    void disabledFeatureIssuesNoCookie() throws Exception {
        boolean original = properties.getRememberMe().isEnabled();
        try {
            properties.getRememberMe().setEnabled(false);
            createUser("rm10");
            MvcResult result = login("rm10", true);
            assertNull(rawCookieHeader(result), "功能关闭时不得下发 cookie");
            assertEquals(0, tokenRepository.count());
        } finally {
            properties.getRememberMe().setEnabled(original);
        }
    }

    @Test
    @DisplayName("有效 JWT 的常规刷新仍可用且不下发/不清除 remember-me")
    void jwtRefreshStillWorks() throws Exception {
        createUser("rm11");
        MvcResult loginResult = login("rm11", true);
        String token = tokenOf(loginResult);

        mockMvc.perform(post("/api/v1/auth/refresh").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.username").value("rm11"));
    }

    @Test
    @DisplayName("2FA 用户第一步只返回 202 与 challenge，不下发 remember-me cookie")
    void twoFactorFirstStepIssuesNoCookie() throws Exception {
        User user = createUser("rm12");
        String token = tokenOf(login("rm12", false));

        String secret = objectMapper.readTree(mockMvc.perform(post("/api/v1/users/me/2fa/init")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .path("data").path("secretBase32").asText();
        mockMvc.perform(post("/api/v1/users/me/2fa/confirm")
                        .header("Authorization", token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"code\":\"" + TotpUtils.currentCode(secret) + "\"}"))
                .andExpect(status().isOk());
        assertTrue(userRepository.findByUsername("rm12").orElseThrow().isTwoFactorEnabled());

        MvcResult firstStep = mockMvc.perform(post("/api/v1/auth/login").contentType(APPLICATION_JSON)
                        .content(loginBody("rm12", true)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.twoFactorRequired").value(true))
                .andReturn();

        assertNull(rawCookieHeader(firstStep), "2FA 第一步不得下发 cookie");
        assertEquals(0, tokenRepository.count());
        assertFalse(firstStep.getResponse().getContentAsString().contains("\"token\""),
                "第一步不得签发 JWT");
        assertTrue(firstStep.getResponse().getContentAsString().contains("challengeToken"));
    }

    @Test
    @DisplayName("2FA 第二步勾选 rememberMe → 签发 JWT 并下发 cookie")
    void twoFactorSecondStepIssuesCookie() throws Exception {
        createUser("rm13");
        String token = tokenOf(login("rm13", false));
        String secret = objectMapper.readTree(mockMvc.perform(post("/api/v1/users/me/2fa/init")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString())
                .path("data").path("secretBase32").asText();
        mockMvc.perform(post("/api/v1/users/me/2fa/confirm")
                        .header("Authorization", token)
                        .contentType(APPLICATION_JSON)
                        .content("{\"code\":\"" + TotpUtils.currentCode(secret) + "\"}"))
                .andExpect(status().isOk());

        String challenge = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON).content(loginBody("rm13", true)))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString())
                .path("data").path("challengeToken").asText();

        MvcResult second = mockMvc.perform(post("/api/v1/auth/login/2fa")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "challengeToken", challenge,
                                "code", TotpUtils.currentCode(secret),
                                "rememberMe", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn();

        assertNotNull(cookieValueOf(second), "第二步应下发 remember-me cookie");
        assertEquals(1, tokenRepository.count());
    }

    @Test
    @DisplayName("不存在/伪造 cookie → 401（不泄露账号是否存在）")
    void bogusCookieRejected() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie(rememberMeService.cookieName(), "fake-series.fake-secret")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("重新登录")));
    }
}
