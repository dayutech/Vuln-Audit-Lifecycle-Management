package com.vulnaudit.controller;

import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.Role;
import com.vulnaudit.entity.User;
import com.vulnaudit.entity.UserRole;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.RoleRepository;
import com.vulnaudit.repository.UserRepository;
import com.vulnaudit.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 仪表盘导出端点的授权测试（t31）。
 *
 * <p>背景：{@code GET /api/v1/dashboard/export} 此前只要求登录，任何用户都能一次性导出
 * <b>全量</b>资产/漏洞明细 CSV。权限矩阵 2.13 限定该能力为 admin / project_leader。
 *
 * <p>覆盖：
 * <ol>
 *   <li>工程师 → 403；team_leader → 403（导出是全量，不能放宽给小组长）；</li>
 *   <li>admin / project_leader → 200，返回带 UTF-8 BOM 的非空 CSV；</li>
 *   <li>导出类型非法时返回 400（授权通过后才进入参数校验）。</li>
 * </ol>
 */
@DisplayName("仪表盘导出授权测试")
public class DashboardExportAuthorizationTest extends BaseTest {

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String projectLeaderToken;

    @BeforeEach
    void setUpProjectLeader() {
        String suffix = String.valueOf(System.currentTimeMillis());

        // 导出的 CSV 需要有真实数据，才能断言「非空且含业务内容」
        assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name("导出用例资产_" + suffix)
                .code("EXP-" + suffix)
                .targetType("Web应用")
                .targetSource("客户委托")
                .priority("P1")
                .status("active")
                .ownerId(adminUserId)
                .build()));

        Role projectLeaderRole = roleRepository.findByCode("project_leader")
                .orElseGet(() -> roleRepository.save(Objects.requireNonNull(Role.builder()
                        .name("项目负责人")
                        .code("project_leader")
                        .description("导出授权用例")
                        .isSystem(true)
                        .build())));

        User projectLeader = userRepository.save(Objects.requireNonNull(User.builder()
                .username("pl_" + suffix)
                .passwordHash(passwordEncoder.encode("plPass123"))
                .email("pl_" + suffix + "@test.com")
                .fullName("项目负责人")
                .status("active")
                .build()));

        userRoleRepository.save(Objects.requireNonNull(UserRole.builder()
                .userId(projectLeader.getId())
                .roleId(projectLeaderRole.getId())
                .assignedBy(adminUserId)
                .assignedAt(LocalDateTime.now())
                .build()));

        projectLeaderToken = Objects.requireNonNull(
                generateTestToken(projectLeader.getUsername(), "ROLE_PROJECT_LEADER"));
    }

    @Test
    @DisplayName("工程师导出 → 403")
    void export_forbiddenForEngineer() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/export")
                        .header("Authorization", engineerToken)
                        .param("type", "assets"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("小组长导出 → 403（导出为全量，不能放宽到 team_leader）")
    void export_forbiddenForTeamLeader() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/export")
                        .header("Authorization", leaderToken)
                        .param("type", "assets"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/dashboard/export")
                        .header("Authorization", leaderToken)
                        .param("type", "vulnerabilities"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("管理员导出 → 200 且 CSV 非空（含 BOM 与表头）")
    void export_allowedForAdmin() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/dashboard/export")
                        .header("Authorization", adminToken)
                        .param("type", "assets"))
                .andExpect(status().isOk())
                .andReturn();

        String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        byte[] bytes = result.getResponse().getContentAsByteArray();

        assertTrue(bytes.length > 3, "导出内容不应为空");
        assertEquals((byte) 0xEF, bytes[0], "CSV 应带 UTF-8 BOM");
        assertEquals((byte) 0xBB, bytes[1]);
        assertEquals((byte) 0xBF, bytes[2]);
        assertTrue(content.contains("资产编号"), "首行应为表头，实际: " + content.substring(0, Math.min(60, content.length())));
        assertTrue(content.contains("导出用例资产") || content.contains("EXP-"),
                "应包含实际资产数据，实际: " + content);
    }

    @Test
    @DisplayName("项目负责人导出 → 200 且漏洞 CSV 非空")
    void export_allowedForProjectLeader() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/dashboard/export")
                        .header("Authorization", projectLeaderToken)
                        .param("type", "vulnerabilities"))
                .andExpect(status().isOk())
                .andReturn();

        String content = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(content.startsWith("\uFEFF"), "CSV 应带 UTF-8 BOM");
        assertTrue(content.contains("漏洞编号"), "首行应为漏洞表头");
    }

    @Test
    @DisplayName("授权通过后非法导出类型仍返回 400")
    void export_invalidType_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/export")
                        .header("Authorization", adminToken)
                        .param("type", "unknown-type"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("未登录访问导出 → 401/403（不能匿名导出）")
    void export_requiresAuthentication() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/dashboard/export")
                        .param("type", "assets"))
                .andReturn();
        int status = result.getResponse().getStatus();
        assertTrue(status == 401 || status == 403, "匿名导出应被拒绝，实际状态码: " + status);
    }
}
