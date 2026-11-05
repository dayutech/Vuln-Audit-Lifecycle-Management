package com.vulnaudit.controller;

import com.vulnaudit.entity.AuditLog;
import com.vulnaudit.entity.SystemSetting;
import com.vulnaudit.entity.User;
import com.vulnaudit.repository.AuditLogRepository;
import com.vulnaudit.repository.SystemSettingRepository;
import com.vulnaudit.service.SystemSettingService;
import com.vulnaudit.service.impl.UserPreferenceServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户登录页主题「第三态」测试（t112）
 *
 * <p>背景：t96 的 {@code users.login_theme} 只有两态（pencil…具体值），「跟随系统默认」只能把
 * 当时的缺省值写死进用户行 —— 管理员之后改缺省，这类用户跟随不上（t110 自报的局限）。
 *
 * <p>本测试锁定三态语义：
 * <ul>
 *   <li>{@code login_theme=NULL} = <b>跟随系统默认</b>（响应 {@code loginTheme:null, followDefault:true}）；</li>
 *   <li>具体 code = 用户指定（{@code followDefault:false}），管理员改缺省不影响；</li>
 *   <li>{@code loginTheme} 与 {@code followDefault} 二选一，同传/都不传 → 400；</li>
 *   <li>登录链路（login / me / refresh）对 NULL 用户返回 {@code loginTheme:null} + {@code followDefault:true}，
 *       **不回填**管理员缺省；</li>
 *   <li>V23 迁移在「空表」与「已有数据」上均幂等，且不修改既有具体值。</li>
 * </ul>
 */
@DisplayName("用户登录页主题三态（跟随默认）测试")
public class LoginThemeThirdStateTest extends BaseTest {

    private static final String PREFS = "/api/v1/users/me/preferences";
    private static final String ADMIN_THEME = "/api/v1/admin/settings/login-theme";
    private static final String PUBLIC_THEME = "/api/v1/auth/login-theme";

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private SystemSettingRepository systemSettingRepository;

    private User reload(java.util.UUID userId) {
        return userRepository.findById(userId).orElseThrow();
    }

    private String loginBody(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    // ------------------------------------------------------------------ 三态写入

    @Test
    @DisplayName("PUT followDefault=true → 库中 NULL，响应与 GET 均为 loginTheme=null / followDefault=true")
    void followDefaultClearsToNull() throws Exception {
        mockMvc.perform(put(PREFS).header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON).content("{\"loginTheme\":\"owl\"}"))
                .andExpect(status().isOk());
        assertEquals("owl", reload(engineerUserId).getLoginTheme());

        mockMvc.perform(put(PREFS).header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON).content("{\"followDefault\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.followDefault").value(true))
                .andExpect(jsonPath("$.data.loginTheme").value(org.hamcrest.Matchers.nullValue()));

        assertNull(reload(engineerUserId).getLoginTheme(), "库内应为 NULL（跟随系统默认）");

        mockMvc.perform(get(PREFS).header("Authorization", engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.followDefault").value(true))
                .andExpect(jsonPath("$.data.loginTheme").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    @DisplayName("PUT 具体 code → 写入且 followDefault=false；再次 GET 一致")
    void specificThemeWritesAndFollowFalse() throws Exception {
        mockMvc.perform(put(PREFS).header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON).content("{\"loginTheme\":\"pixel-eyes\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginTheme").value("pixel-eyes"))
                .andExpect(jsonPath("$.data.followDefault").value(false));

        assertEquals("pixel-eyes", reload(engineerUserId).getLoginTheme(), "库内应为具体值");

        mockMvc.perform(get(PREFS).header("Authorization", engineerToken))
                .andExpect(jsonPath("$.data.loginTheme").value("pixel-eyes"))
                .andExpect(jsonPath("$.data.followDefault").value(false));
    }

    // ------------------------------------------------------------------ 二选一校验

    @Test
    @DisplayName("loginTheme 与 followDefault 同传 → 400（中/英），库内不变")
    void bothFieldsRejected() throws Exception {
        mockMvc.perform(put(PREFS).header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON).content("{\"loginTheme\":\"owl\",\"followDefault\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("只能二选一")));

        mockMvc.perform(put(PREFS).header("Authorization", engineerToken)
                        .header("Accept-Language", "en-US")
                        .contentType(APPLICATION_JSON).content("{\"loginTheme\":\"owl\",\"followDefault\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("not both")));

        assertNull(reload(engineerUserId).getLoginTheme(), "被拒绝的请求不得改库");
    }

    @Test
    @DisplayName("两者都不传 → 400（中/英），库内不变")
    void neitherFieldRejected() throws Exception {
        mockMvc.perform(put(PREFS).header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("必须指定 loginTheme")));

        mockMvc.perform(put(PREFS).header("Authorization", engineerToken)
                        .header("Accept-Language", "en-US")
                        .contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("loginTheme is required")));

        assertNull(reload(engineerUserId).getLoginTheme(), "被拒绝的请求不得改库");
    }

    // ------------------------------------------------------------------ 登录链路

    @Test
    @DisplayName("login / refresh 对跟随默认用户返回 loginTheme=null + followDefault=true（不回填管理员缺省）")
    void loginAndRefreshExposeNull() throws Exception {
        String suffix = String.valueOf(System.nanoTime());
        String password = "third12345";
        User user = userRepository.save(java.util.Objects.requireNonNull(User.builder()
                .username("third_" + suffix)
                .passwordHash(passwordEncoder.encode(password))
                .email("third_" + suffix + "@test.com")
                .fullName("三态用户")
                .status("active")
                .build()));
        assertNull(user.getLoginTheme(), "新建用户默认跟随系统默认（NULL）");

        String token = generateTestToken(user.getUsername(), "ROLE_VULNERABILITY_ENGINEER");
        // 先写一个具体主题，再清回跟随默认，覆盖「有值 → NULL」路径
        mockMvc.perform(put(PREFS).header("Authorization", token)
                        .contentType(APPLICATION_JSON).content("{\"loginTheme\":\"owl\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put(PREFS).header("Authorization", token)
                        .contentType(APPLICATION_JSON).content("{\"followDefault\":true}"))
                .andExpect(status().isOk());

        // 管理员缺省改成 orbit —— 不影响「跟随默认」用户自身仍为 NULL
        mockMvc.perform(put(ADMIN_THEME).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"defaultTheme\":\"orbit\"}"))
                .andExpect(status().isOk());

        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(APPLICATION_JSON).content(loginBody(user.getUsername(), password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginTheme").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.followDefault").value(true))
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("\"loginTheme\":\"orbit\""), "不得把管理员缺省值回填到登录响应: " + body);

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.followDefault").value(true))
                .andExpect(jsonPath("$.data.loginTheme").value(org.hamcrest.Matchers.nullValue()));

        mockMvc.perform(post("/api/v1/auth/refresh").header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.followDefault").value(true))
                .andExpect(jsonPath("$.data.loginTheme").value(org.hamcrest.Matchers.nullValue()));
    }

    // ------------------------------------------------------------------ 与管理员缺省解耦

    @Test
    @DisplayName("管理员改缺省（orbit）后，跟随默认用户仍是 NULL（偏好不被写成缺省值）")
    void adminDefaultChangeKeepsUserNull() throws Exception {
        mockMvc.perform(put(PREFS).header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON).content("{\"followDefault\":true}"))
                .andExpect(status().isOk());
        assertNull(reload(engineerUserId).getLoginTheme());

        mockMvc.perform(put(ADMIN_THEME).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"defaultTheme\":\"orbit\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get(PUBLIC_THEME))
                .andExpect(jsonPath("$.data.defaultTheme").value("orbit"));
        mockMvc.perform(get(PREFS).header("Authorization", engineerToken))
                .andExpect(jsonPath("$.data.followDefault").value(true))
                .andExpect(jsonPath("$.data.loginTheme").value(org.hamcrest.Matchers.nullValue()));

        assertNull(reload(engineerUserId).getLoginTheme(), "管理员缺省变化不得写进用户行");
        assertNull(reload(adminUserId).getLoginTheme(), "管理员自己的偏好也仍是 NULL（未选择）");
    }

    // ------------------------------------------------------------------ 历史值兼容

    @Test
    @DisplayName("历史具体值（pencil）保持为「用户指定」，可再被清回跟随默认")
    void legacyConcreteValueKept() throws Exception {
        User engineer = reload(engineerUserId);
        engineer.setLoginTheme("pencil");   // 模拟 V19 时代写入的历史值
        userRepository.save(engineer);

        mockMvc.perform(get(PREFS).header("Authorization", engineerToken))
                .andExpect(jsonPath("$.data.loginTheme").value("pencil"))
                .andExpect(jsonPath("$.data.followDefault").value(false));

        mockMvc.perform(put(PREFS).header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON).content("{\"followDefault\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginTheme").value(org.hamcrest.Matchers.nullValue()));
        assertNull(reload(engineerUserId).getLoginTheme());
    }

    @Test
    @DisplayName("审计 details 用 <follow-default> 标注 NULL：from=owl -> to=<follow-default>，且仅 1 条")
    void auditUsesFollowDefaultMarker() throws Exception {
        mockMvc.perform(put(PREFS).header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON).content("{\"loginTheme\":\"owl\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(put(PREFS).header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON).content("{\"followDefault\":true}"))
                .andExpect(status().isOk());

        List<AuditLog> logs = auditLogRepository.findByAction("UPDATE_LOGIN_THEME").stream()
                .filter(log -> engineerUserId.equals(log.getResourceId()))
                .toList();
        assertEquals(2, logs.size(), "owl 与「清回默认」各 1 条");
        assertTrue(logs.stream().anyMatch(log -> log.getDetails() != null
                        && log.getDetails().contains("to=" + UserPreferenceServiceImpl.FOLLOW_DEFAULT_MARKER)),
                "清回默认应记录 <follow-default> 标记");
    }

    // ------------------------------------------------------------------ V23 迁移

    @Test
    @DisplayName("V23：空表与已有数据上均幂等；既有具体值不变；改后允许写入 NULL")
    void migrationIsIdempotentAndKeepsValues() throws Exception {
        String ddl = Files.readString(
                Path.of("src/main/resources/db/migration/V23__user_login_theme_follow_default.sql"));
        String url = "jdbc:h2:mem:v23_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            // 1) 空表场景：先造一个与 V19 一致的 NOT NULL 结构，再跑 V23
            execute(connection, "CREATE TABLE users (id VARCHAR(36) NOT NULL PRIMARY KEY, "
                    + "login_theme VARCHAR(32) NOT NULL DEFAULT 'pencil')");
            assertFalse(isNullable(connection), "前置：login_theme 应为 NOT NULL");
            // 迁移前先放两行「历史数据」：u-1 显式 owl、u-2 走当时的默认值 pencil
            execute(connection, "INSERT INTO users (id, login_theme) VALUES ('u-1', 'owl')");
            execute(connection, "INSERT INTO users (id) VALUES ('u-2')");

            runDdl(connection, ddl);
            assertTrue(isNullable(connection), "V23 后 login_theme 应允许 NULL");

            // 2) 已有数据场景：具体值不被改写
            assertEquals("owl", queryString(connection, "SELECT login_theme FROM users WHERE id = 'u-1'"),
                    "已有具体值不得被迁移改写");
            assertEquals("pencil", queryString(connection, "SELECT login_theme FROM users WHERE id = 'u-2'"),
                    "默认插入的历史行保持原值");

            // 3) 迁移后可写入 NULL（= 跟随默认），且可重复执行
            execute(connection, "INSERT INTO users (id, login_theme) VALUES ('u-3', NULL)");
            assertNull(queryString(connection, "SELECT login_theme FROM users WHERE id = 'u-3'"),
                    "NULL 应可正常写入（跟随系统默认）");
            runDdl(connection, ddl);
            assertEquals(3, count(connection, "users"), "重复执行 V23 不应影响数据");
        }
    }

    // ------------------------------------------------------------------ 内部工具

    private boolean isNullable(Connection connection) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        try (ResultSet rs = meta.getColumns(null, null, "USERS", "LOGIN_THEME")) {
            assertTrue(rs.next(), "应能查到 users.login_theme 列");
            return rs.getInt("NULLABLE") == DatabaseMetaData.columnNullable;
        }
    }

    private void runDdl(Connection connection, String ddl) throws SQLException {
        StringBuilder current = new StringBuilder();
        for (String rawLine : ddl.split("\n")) {
            String line = rawLine.trim();
            if (line.startsWith("--") || line.isEmpty()) {
                continue;
            }
            current.append(rawLine).append('\n');
            if (line.endsWith(";")) {
                String statement = current.toString().trim();
                execute(connection, statement.substring(0, statement.length() - 1));
                current.setLength(0);
            }
        }
        if (!current.toString().isBlank()) {
            execute(connection, current.toString().trim());
        }
    }

    private void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private String queryString(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private long count(Connection connection, String tableName) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            rs.next();
            return rs.getLong(1);
        }
    }
}
