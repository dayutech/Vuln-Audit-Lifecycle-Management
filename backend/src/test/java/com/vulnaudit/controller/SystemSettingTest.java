package com.vulnaudit.controller;

import com.vulnaudit.entity.AuditLog;
import com.vulnaudit.entity.SystemSetting;
import com.vulnaudit.repository.AuditLogRepository;
import com.vulnaudit.repository.SystemSettingRepository;
import com.vulnaudit.service.SystemSettingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 系统设置：管理员统一设置登录页缺省主题（t109）
 *
 * <p>覆盖：
 * <ul>
 *   <li>公开端点 {@code GET /auth/login-theme} 未登录可访问，返回缺省 pencil + 8 个主题；</li>
 *   <li>管理员改 {@code owl} 后公开端点立即生效（其他客户端刷新登录页即拿到新缺省）；</li>
 *   <li>非法值 400（中/英，消息含支持列表）且库内不变；非管理员读写 403、匿名读管理员端点 401；</li>
 *   <li>审计 {@code UPDATE_SYSTEM_SETTING} 记录 from→to，且一次修改只有 1 条（拦截器不重复）；</li>
 *   <li>缺行时自愈插入 pencil（幂等）；脏值 {@code neon} 读取回落 pencil 且不抛异常、不静默改库；</li>
 *   <li>V22 迁移在空库与已有数据上均幂等（独立 H2 库实跑两遍）；</li>
 *   <li>管理员缺省值不写入任何用户的 {@code users.login_theme}（用户个人偏好语义不变）。</li>
 * </ul>
 */
@DisplayName("系统设置（登录页缺省主题）测试")
public class SystemSettingTest extends BaseTest {

    private static final String PUBLIC_THEME = "/api/v1/auth/login-theme";
    private static final String ADMIN_THEME = "/api/v1/admin/settings/login-theme";
    private static final String PREFS = "/api/v1/users/me/preferences";

    @Autowired
    private SystemSettingRepository systemSettingRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private SystemSettingService systemSettingService;

    private SystemSetting reloadSetting() {
        return systemSettingRepository.findBySettingKey(SystemSettingService.LOGIN_DEFAULT_THEME_KEY).orElse(null);
    }

    // ------------------------------------------------------------------ 公开端点

    @Test
    @DisplayName("公开端点：未登录可访问，缺省 pencil + 8 个主题（不被 AuthenticationEntryPoint 拦成 401）")
    void publicEndpointAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get(PUBLIC_THEME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.defaultTheme").value("pencil"))
                .andExpect(jsonPath("$.data.themes.length()").value(8))
                .andExpect(jsonPath("$.data.themes[0].code").value("pencil"))
                .andExpect(jsonPath("$.data.themes[3].code").value("pixel-eyes"))
                .andExpect(jsonPath("$.data.themes[7].code").value("big-type"));
    }

    @Test
    @DisplayName("管理员改为 owl → 公开端点立即返回 owl（其他访客刷新登录页即生效）")
    void adminUpdateReflectsOnPublicEndpoint() throws Exception {
        mockMvc.perform(put(ADMIN_THEME).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"defaultTheme\":\"owl\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultTheme").value("owl"))
                .andExpect(jsonPath("$.data.updatedBy").value(adminUsername()))
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());

        mockMvc.perform(get(PUBLIC_THEME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultTheme").value("owl"));

        assertEquals("owl", reloadSetting().getSettingValue(), "库内应更新为 owl");
    }

    // ------------------------------------------------------------------ 校验与权限

    @Test
    @DisplayName("非法值 → 400（中/英，含支持列表），库内不变")
    void invalidValueRejected() throws Exception {
        mockMvc.perform(put(ADMIN_THEME).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"defaultTheme\":\"neon\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("不支持的登录页主题: neon")))
                .andExpect(jsonPath("$.message", containsString("支持: " + String.join(", ",
                        List.of("pencil", "geometric", "owl", "pixel-eyes", "aurora", "mono-grid", "orbit", "big-type")))));

        mockMvc.perform(put(ADMIN_THEME).header("Authorization", adminToken)
                        .header("Accept-Language", "en-US")
                        .contentType(APPLICATION_JSON).content("{\"defaultTheme\":\"mono_grid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Unsupported login page theme: mono_grid")))
                .andExpect(jsonPath("$.message", containsString("supported: ")));

        assertEquals("pencil", reloadSetting().getSettingValue(), "非法值不得落库");
        mockMvc.perform(get(PUBLIC_THEME))
                .andExpect(jsonPath("$.data.defaultTheme").value("pencil"));
    }

    @Test
    @DisplayName("非管理员读写 403；匿名读管理员端点 401")
    void nonAdminForbidden() throws Exception {
        mockMvc.perform(get(ADMIN_THEME).header("Authorization", engineerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(ADMIN_THEME).header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON).content("{\"defaultTheme\":\"owl\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(ADMIN_THEME).header("Authorization", leaderToken)
                        .contentType(APPLICATION_JSON).content("{\"defaultTheme\":\"owl\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(ADMIN_THEME))
                .andExpect(status().isUnauthorized());

        assertEquals("pencil", reloadSetting().getSettingValue(), "无权限调用不应改库");
    }

    // ------------------------------------------------------------------ 审计

    @Test
    @DisplayName("审计：UPDATE_SYSTEM_SETTING 记录 from→to，且一次修改只有 1 条（拦截器不重复）")
    void auditRecordsFromToOnce() throws Exception {
        mockMvc.perform(put(ADMIN_THEME).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"defaultTheme\":\"geometric\"}"))
                .andExpect(status().isOk());

        List<AuditLog> logs = auditLogRepository.findByAction(SystemSettingService.ACTION_UPDATE_SYSTEM_SETTING);
        assertEquals(1, logs.size(), "一次修改应恰好 1 条 UPDATE_SYSTEM_SETTING");
        AuditLog log = logs.get(0);
        assertEquals(SystemSettingService.RESOURCE_TYPE_SYSTEM_SETTING, log.getResourceType());
        assertEquals(adminUserId, log.getUserId(), "审计应归属真实操作者");
        assertNotNull(log.getDetails());
        assertTrue(log.getDetails().contains("from=pencil -> to=geometric"),
                "details 应记录 from→to，实际: " + log.getDetails());

        List<AuditLog> interceptorRows = auditLogRepository.findAll().stream()
                .filter(row -> row.getAction() != null && row.getAction().contains("AdminSettingsController"))
                .toList();
        assertTrue(interceptorRows.isEmpty(),
                "该端点不应再由拦截器写重复审计，实际 " + interceptorRows.size() + " 条");
    }

    // ------------------------------------------------------------------ 自愈 / 脏值

    @Test
    @DisplayName("缺行时自愈插入 pencil（幂等：连续两次只插一次）")
    void selfHealsMissingRow() throws Exception {
        systemSettingRepository.deleteAll();
        assertNull(reloadSetting(), "前置：行已删除");

        // 公开端点在缺行时也能正常工作（读取路径自愈）
        mockMvc.perform(get(PUBLIC_THEME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultTheme").value("pencil"));

        SystemSetting healed = reloadSetting();
        assertNotNull(healed, "读取应自愈插入缺省行");
        assertEquals("pencil", healed.getSettingValue());
        assertNull(healed.getUpdatedBy(), "系统自愈插入没有操作者");

        // 幂等：再跑一次启动自愈不应重复插入
        boolean insertedAgain = systemSettingService.ensureDefaults();
        assertFalse(insertedAgain, "已存在时 ensureDefaults 应返回 false");
        assertEquals(1, systemSettingRepository.count(), "system_settings 只应有 1 行");
    }

    @Test
    @DisplayName("脏值 neon：公开端点回落 pencil 且不抛异常、不静默改库")
    void dirtyValueFallsBackToPencil() throws Exception {
        SystemSetting setting = reloadSetting();
        setting.setSettingValue("neon");
        systemSettingRepository.save(setting);

        mockMvc.perform(get(PUBLIC_THEME))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultTheme").value("pencil"));

        mockMvc.perform(get(ADMIN_THEME).header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultTheme").value("pencil"));

        assertEquals("neon", reloadSetting().getSettingValue(),
                "读取只做兜底，不应静默改写管理员数据（便于运维发现脏值）");
    }

    // ------------------------------------------------------------------ 迁移

    @Test
    @DisplayName("V22 迁移：空库执行成功、已有数据再执行幂等、唯一键兜底")
    void migrationIsIdempotentOnEmptyAndExistingData() throws Exception {
        String ddl = Files.readString(Path.of("src/main/resources/db/migration/V22__system_settings.sql"));
        // 独立 H2 库实跑迁移脚本：MODE=MySQL 让 H2 接受 MariaDB 的 DATETIME(6) 写法，
        // 从而校验的就是「生产用的那份 DDL」，而不是为测试改写的版本。
        String url = "jdbc:h2:mem:v22_" + System.nanoTime() + ";MODE=MySQL;DB_CLOSE_DELAY=-1";

        try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
            // 1) 空库：建表 + 唯一索引
            runDdl(connection, ddl);
            assertTrue(tableExists(connection, "SYSTEM_SETTINGS"), "空库执行后应存在 system_settings");

            // 2) 已有数据：插一行后重复执行同一份 DDL（幂等，不丢数据）
            execute(connection, "INSERT INTO system_settings (id, setting_key, setting_value, updated_at) "
                    + "VALUES ('11111111-1111-1111-1111-111111111111', 'login.defaultTheme', 'owl', CURRENT_TIMESTAMP)");
            runDdl(connection, ddl);

            assertEquals(1, count(connection, "system_settings"), "重复执行 DDL 不应影响已有行");
            assertEquals("owl", queryString(connection,
                    "SELECT setting_value FROM system_settings WHERE setting_key = 'login.defaultTheme'"));

            // 3) 唯一键：同 key 再插应失败（防多实例重复建行）
            assertThrows(SQLException.class, () -> execute(connection,
                    "INSERT INTO system_settings (id, setting_key, setting_value, updated_at) "
                            + "VALUES ('22222222-2222-2222-2222-222222222222', 'login.defaultTheme', 'aurora', CURRENT_TIMESTAMP)"));
        }
    }

    // ------------------------------------------------------------------ 与用户偏好的边界

    @Test
    @DisplayName("管理员缺省值不写用户偏好：用户自己的选择保持不变")
    void adminDefaultDoesNotTouchUserPreference() throws Exception {
        mockMvc.perform(put(PREFS).header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON).content("{\"loginTheme\":\"owl\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(put(ADMIN_THEME).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"defaultTheme\":\"aurora\"}"))
                .andExpect(status().isOk());

        // 全站缺省变化
        mockMvc.perform(get(PUBLIC_THEME))
                .andExpect(jsonPath("$.data.defaultTheme").value("aurora"));

        // 用户个人偏好不受影响（不会被写成 aurora）
        mockMvc.perform(get(PREFS).header("Authorization", engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loginTheme").value("owl"));
        assertEquals("owl", userRepository.findById(engineerUserId).orElseThrow().getLoginTheme());

        // 另一位用户仍是「未选择」（NULL = 跟随系统默认，t112）：缺省值只存在 system_settings，未落任何用户行
        assertNull(userRepository.findById(adminUserId).orElseThrow().getLoginTheme());
    }

    // ------------------------------------------------------------------ 内部工具

    private String adminUsername() {
        return userRepository.findById(adminUserId).orElseThrow().getUsername();
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

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (ResultSet rs = connection.getMetaData().getTables(null, null, tableName, null)) {
            return rs.next();
        }
    }

    private long count(Connection connection, String tableName) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private String queryString(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }
}
