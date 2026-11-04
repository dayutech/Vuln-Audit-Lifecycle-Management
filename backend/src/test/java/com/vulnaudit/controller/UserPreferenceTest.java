package com.vulnaudit.controller;

import com.vulnaudit.entity.AuditLog;
import com.vulnaudit.entity.User;
import com.vulnaudit.repository.AuditLogRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户登录页主题偏好测试（t96）。
 *
 * <p>覆盖：默认值 pencil、合法值更新（库内 + 响应）、非法值 400（中/英两种语言）、
 * 只能改自己（额外 userId 被忽略）、login / me 响应携带 loginTheme、未认证 401、审计落库。
 */
@DisplayName("用户登录页主题偏好测试")
public class UserPreferenceTest extends BaseTest {

    private static final String PREFS = "/api/v1/users/me/preferences";

    @Autowired
    private AuditLogRepository auditLogRepository;

    private User reload(java.util.UUID userId) {
        return userRepository.findById(userId).orElseThrow();
    }

    private String loginBody(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    // ------------------------------------------------------------------ 读取默认值

    @Test
    @DisplayName("GET 默认 pencil；库内新建用户默认值也是 pencil")
    void defaultThemeIsPencil() throws Exception {
        mockMvc.perform(get(PREFS).header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginTheme").value("pencil"));

        assertEquals("pencil", reload(engineerUserId).getLoginTheme());
    }

    // ------------------------------------------------------------------ 更新

    @Test
    @DisplayName("PUT 合法值 owl → 200，响应与库内均更新")
    void updateThemeSucceeds() throws Exception {
        mockMvc.perform(put(PREFS).header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON).content("{\"loginTheme\":\"owl\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginTheme").value("owl"));

        assertEquals("owl", reload(engineerUserId).getLoginTheme(), "库内应更新为 owl");
        assertEquals("pencil", reload(adminUserId).getLoginTheme(), "不应影响其他用户");
    }

    @Test
    @DisplayName("PUT 大写/带空格自动归一化（OWL → owl）")
    void updateThemeNormalized() throws Exception {
        mockMvc.perform(put(PREFS).header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON).content("{\"loginTheme\":\" OWL \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginTheme").value("owl"));
    }

    @Test
    @DisplayName("PUT 非法值 → 400 且中文文案（默认语言）")
    void invalidThemeRejectedInChinese() throws Exception {
        mockMvc.perform(put(PREFS).header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON).content("{\"loginTheme\":\"neon\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("不支持的登录页主题: neon")));

        assertEquals("pencil", reload(engineerUserId).getLoginTheme(), "非法值不得落库");
    }

    @Test
    @DisplayName("PUT 非法值 + Accept-Language: en → 400 且英文文案（t60 MessageSource）")
    void invalidThemeRejectedInEnglish() throws Exception {
        mockMvc.perform(put(PREFS).header("Authorization", engineerToken)
                        .header("Accept-Language", "en-US")
                        .contentType(APPLICATION_JSON).content("{\"loginTheme\":\"neon\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Unsupported login page theme: neon")));
    }

    // ------------------------------------------------------------------ 只能改自己

    @Test
    @DisplayName("只能改自己：请求体/查询参数带 userId 都被忽略")
    void cannotUpdateOtherUserPreference() throws Exception {
        mockMvc.perform(put(PREFS).header("Authorization", engineerToken)
                        .param("userId", adminUserId.toString())
                        .contentType(APPLICATION_JSON)
                        .content("{\"loginTheme\":\"pixel-eyes\",\"userId\":\"" + adminUserId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginTheme").value("pixel-eyes"));

        assertEquals("pixel-eyes", reload(engineerUserId).getLoginTheme(), "应改到自己");
        assertEquals("pencil", reload(adminUserId).getLoginTheme(), "管理员偏好不应被改动");
        assertNotEquals("pixel-eyes", reload(adminUserId).getLoginTheme());
    }

    // ------------------------------------------------------------------ 登录 / me 响应

    @Test
    @DisplayName("login 与 /auth/me 响应均含 loginTheme（登录后无需再查一次）")
    void loginAndMeExposeTheme() throws Exception {
        // 用本用例自建的用户（口令由同一个 passwordEncoder 生成），避免依赖夹具口令
        String suffix = String.valueOf(System.nanoTime());
        String prefPassword = "pref12345";
        User prefUser = userRepository.save(java.util.Objects.requireNonNull(User.builder()
                .username("prefuser_" + suffix)
                .passwordHash(passwordEncoder.encode(prefPassword))
                .email("pref_" + suffix + "@test.com")
                .fullName("偏好用户")
                .status("active")
                .build()));
        String prefToken = generateTestToken(prefUser.getUsername(), "ROLE_VULNERABILITY_ENGINEER");

        mockMvc.perform(put(PREFS).header("Authorization", prefToken)
                        .contentType(APPLICATION_JSON).content("{\"loginTheme\":\"geometric\"}"))
                .andExpect(status().isOk());

        org.springframework.test.web.servlet.MvcResult loginResult = mockMvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(APPLICATION_JSON).content(loginBody(prefUser.getUsername(), prefPassword)))
                .andReturn();
        assertEquals(200, loginResult.getResponse().getStatus(),
                "登录应成功，实际响应体: " + loginResult.getResponse().getContentAsString());
        String loginBody = loginResult.getResponse().getContentAsString();
        assertTrue(loginBody.contains("\"loginTheme\":\"geometric\""),
                "登录响应应含 loginTheme: " + loginBody);
        assertTrue(loginBody.contains("\"token\""), "登录响应应含 token: " + loginBody);

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", prefToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginTheme").value("geometric"));
    }

    // ------------------------------------------------------------------ 未认证 / 审计

    @Test
    @DisplayName("未认证访问偏好接口 → 401")
    void unauthenticatedRejected() throws Exception {
        mockMvc.perform(get(PREFS)).andExpect(status().isUnauthorized());
        mockMvc.perform(put(PREFS).contentType(APPLICATION_JSON).content("{\"loginTheme\":\"owl\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("修改偏好写审计：action=UPDATE_LOGIN_THEME、resourceType=user、resourceId=本人")
    void updateWritesAudit() throws Exception {
        mockMvc.perform(put(PREFS).header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON).content("{\"loginTheme\":\"owl\"}"))
                .andExpect(status().isOk());

        List<AuditLog> logs = auditLogRepository.findByAction("UPDATE_LOGIN_THEME");
        assertTrue(logs.stream().anyMatch(log -> engineerUserId.equals(log.getResourceId())
                        && "user".equals(log.getResourceType())
                        && log.getDetails() != null
                        && log.getDetails().contains("owl")),
                "应记录 UPDATE_LOGIN_THEME 审计（含新主题）");
    }

    @Test
    @DisplayName("相同主题重复 PUT 不重复写审计（幂等，不产生噪声）")
    void sameThemeDoesNotWriteAuditTwice() throws Exception {
        mockMvc.perform(put(PREFS).header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON).content("{\"loginTheme\":\"pencil\"}"))
                .andExpect(status().isOk());

        long count = auditLogRepository.findByAction("UPDATE_LOGIN_THEME").stream()
                .filter(log -> engineerUserId.equals(log.getResourceId()))
                .count();
        assertEquals(0, count, "值未变化时不应写审计");
    }

    @Test
    @DisplayName("一次改偏好只产生 1 条审计（拦截器不再重复写 PUT UserController.updateMyPreferences）")
    void singleAuditRowPerUpdate() throws Exception {
        mockMvc.perform(put(PREFS).header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON).content("{\"loginTheme\":\"geometric\"}"))
                .andExpect(status().isOk());

        List<AuditLog> semantic = auditLogRepository.findByAction("UPDATE_LOGIN_THEME").stream()
                .filter(log -> engineerUserId.equals(log.getResourceId()))
                .toList();
        assertEquals(1, semantic.size(), "应有且仅有 1 条 UPDATE_LOGIN_THEME");
        assertTrue(semantic.get(0).getDetails() != null
                        && semantic.get(0).getDetails().contains("geometric"),
                "语义化审计 details 应含新主题");

        List<AuditLog> interceptorRows = auditLogRepository.findAll().stream()
                .filter(log -> log.getAction() != null
                        && log.getAction().contains("UserController.updateMyPreferences"))
                .toList();
        assertTrue(interceptorRows.isEmpty(),
                "偏好端点不应再由拦截器写重复审计，实际 " + interceptorRows.size() + " 条");
    }
}
