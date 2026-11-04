package com.vulnaudit.controller;

import com.vulnaudit.entity.AuditLog;
import com.vulnaudit.entity.User;
import com.vulnaudit.entity.enums.LoginTheme;
import com.vulnaudit.repository.AuditLogRepository;
import com.vulnaudit.service.UserPreferenceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户登录页主题偏好测试（t96 / t100）。
 *
 * <p>覆盖：默认值 pencil、8 个合法值各存一次（库内 + 响应 + {@code /auth/me} 回显）、
 * 大小写与空格归一、非法值 400（中/英两种语言，且文案带完整「支持: …」列表）、
 * 只能改自己（额外 userId 被忽略）、login / me 响应携带 loginTheme、未认证 401、审计落库与去重、
 * 以及「枚举 / 白名单 / 默认值」一致性守护（{@link LoginTheme} 为单一来源）。
 */
@DisplayName("用户登录页主题偏好测试")
public class UserPreferenceTest extends BaseTest {

    private static final String PREFS = "/api/v1/users/me/preferences";

    /** 固定的期望白名单（顺序即展示顺序）：改主题时必须同步改这里，防止悄悄加/删/改名 */
    private static final List<String> EXPECTED_THEMES = List.of(
            "pencil", "geometric", "owl", "pixel-eyes", "aurora", "mono-grid", "orbit", "big-type");

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
    @DisplayName("PUT 非法值 → 400，中文文案含「支持: …」完整列表（默认语言）")
    void invalidThemeRejectedInChinese() throws Exception {
        mockMvc.perform(put(PREFS).header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON).content("{\"loginTheme\":\"neon\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("不支持的登录页主题: neon")))
                .andExpect(jsonPath("$.message", containsString("支持: " + LoginTheme.supportedCodes())));

        assertEquals("pencil", reload(engineerUserId).getLoginTheme(), "非法值不得落库");
    }

    @Test
    @DisplayName("PUT 非法值 + Accept-Language: en → 400，英文文案含 supported 列表（t60 MessageSource）")
    void invalidThemeRejectedInEnglish() throws Exception {
        mockMvc.perform(put(PREFS).header("Authorization", engineerToken)
                        .header("Accept-Language", "en-US")
                        .contentType(APPLICATION_JSON).content("{\"loginTheme\":\"neon\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Unsupported login page theme: neon")))
                .andExpect(jsonPath("$.message", containsString("supported: " + LoginTheme.supportedCodes())));
    }

    // ------------------------------------------------------------------ 8 个主题全量

    @Test
    @DisplayName("8 个合法主题各 PUT 一次均 200，库内更新且 GET /auth/me 回显")
    void allSupportedThemesAccepted() throws Exception {
        assertEquals(8, LoginTheme.codes().size(), "白名单应为 8 项");

        for (String theme : new String[]{"pencil", "geometric", "owl", "pixel-eyes",
                "aurora", "mono-grid", "orbit", "big-type"}) {
            mockMvc.perform(put(PREFS).header("Authorization", engineerToken)
                            .contentType(APPLICATION_JSON).content("{\"loginTheme\":\"" + theme + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.loginTheme").value(theme));

            assertEquals(theme, reload(engineerUserId).getLoginTheme(), "库内应更新为 " + theme);

            // /auth/me 回显当前主题
            mockMvc.perform(get("/api/v1/auth/me").header("Authorization", engineerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.loginTheme").value(theme));
        }

        // 全量 8 次切换后仍只影响自己
        assertEquals("pencil", reload(adminUserId).getLoginTheme(), "不应影响其他用户");
    }

    // ------------------------------------------------------------------ 单一来源守护

    @Test
    @DisplayName("枚举 / 服务层白名单 / 默认值 三处一致，且代码格式合法（mono-grid 用连字符）")
    void enumAndWhitelistStayInSync() {
        // 1) 枚举代码 = 期望的 8 项固定列表（顺序稳定）
        assertEquals(EXPECTED_THEMES, LoginTheme.codes(), "LoginTheme 代码列表应与期望一致");

        // 2) 服务层常量不再手写一份，直接派生自枚举
        assertEquals(LoginTheme.codes(), UserPreferenceService.SUPPORTED_LOGIN_THEMES,
                "服务层白名单应等于 LoginTheme.codes()");
        assertEquals("pencil", UserPreferenceService.DEFAULT_LOGIN_THEME, "默认值保持 pencil");
        assertEquals("pencil", LoginTheme.DEFAULT.getCode(), "枚举默认值应为 pencil");
        assertEquals(LoginTheme.PENCIL, LoginTheme.DEFAULT);

        // 3) 每个代码：唯一、小写+连字符、可正查/反查，isValid 成立
        assertEquals(8, LoginTheme.codes().stream().distinct().count(), "主题代码不得重复");
        for (LoginTheme theme : LoginTheme.values()) {
            String code = theme.getCode();
            assertTrue(code.matches("[a-z]+(-[a-z]+)*"),
                    "主题代码应为小写字母+连字符，实际: " + code);
            assertTrue(code.length() <= LoginTheme.MAX_CODE_LENGTH, "主题代码不得超长: " + code);
            assertTrue(LoginTheme.isValid(code), "isValid 应接受: " + code);
            assertTrue(LoginTheme.isValid(" " + code.toUpperCase() + " "), "大小写/空格应归一: " + code);
            assertEquals(theme, LoginTheme.fromCode(code), "fromCode 应反查回枚举: " + code);
            assertEquals(code, LoginTheme.fromCodeOrDefault(code).getCode());
        }

        // 4) mono-grid 必须是连字符形式（历史坑：写成 mono_grid / monoGrid 会 400）
        assertTrue(LoginTheme.codes().contains("mono-grid"), "必须包含 mono-grid");
        assertFalse(LoginTheme.codes().contains("mono_grid"), "不得出现 mono_grid（下划线错误写法）");

        // 5) 非法值不通过：拼写错误、下划线变体、空值
        for (String invalid : new String[]{"neon", "mono_grid", "monoGrid", "big_type", "", "  ", null}) {
            assertFalse(LoginTheme.isValid(invalid), "非法值不应通过: " + invalid);
            assertNull(LoginTheme.fromCode(invalid), "非法值 fromCode 应为 null: " + invalid);
            assertEquals("pencil", LoginTheme.fromCodeOrDefault(invalid).getCode(),
                    "非法值应回落 pencil: " + invalid);
        }

        // 6) 错误消息里的列表就是同一份数据
        assertEquals("pencil, geometric, owl, pixel-eyes, aurora, mono-grid, orbit, big-type",
                LoginTheme.supportedCodes());
    }

    @Test
    @DisplayName("库内脏数据（不在白名单）读取时回落 pencil，不把非法值回吐")
    void staleStoredThemeFallsBackToPencil() throws Exception {
        User engineer = reload(engineerUserId);
        engineer.setLoginTheme("legacy-neon");
        userRepository.save(engineer);

        mockMvc.perform(get(PREFS).header("Authorization", engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginTheme").value("pencil"));
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
