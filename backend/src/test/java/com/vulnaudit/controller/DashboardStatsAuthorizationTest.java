package com.vulnaudit.controller;

import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.Team;
import com.vulnaudit.entity.Vulnerability;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.TeamRepository;
import com.vulnaudit.repository.VulnerabilityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Objects;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 仪表盘统计端点的授权与数据边界测试（t29）。
 *
 * <p>背景：{@code /dashboard/vulnerabilities/stats*} 此前既无 {@code @PreAuthorize}
 * 也未按可见范围收敛，任何登录用户都能读到全量漏洞分布。本用例验证两层同时生效：
 * <ol>
 *   <li><b>角色门槛</b>：工程师访问三个漏洞统计端点 → 403（权限矩阵 2.13）；</li>
 *   <li><b>数据收敛</b>：team_leader 只统计本团队可见资产（数值小于 admin 全量）；</li>
 *   <li>资产统计（{@code /dashboard/assets/stats*}）、概览、我的仪表盘同样按可见范围收敛。</li>
 * </ol>
 *
 * <p>数据准备：
 * <ul>
 *   <li>teamA（leader = leaderToken 的小组长）→ assetA（负责人 = 小组长）+ 1 条 HIGH/discovered 漏洞；</li>
 *   <li>teamB（leader = 管理员）→ assetB（负责人 = 管理员）+ 2 条漏洞（CRITICAL/in_progress、HIGH/discovered）；</li>
 *   <li>工程师不负责任何资产、也无授权 → 可见集合为空。</li>
 * </ul>
 */
@DisplayName("仪表盘统计授权与数据边界测试")
public class DashboardStatsAuthorizationTest extends BaseTest {

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private VulnerabilityRepository vulnerabilityRepository;

    @Autowired
    private TeamRepository teamRepository;

    private Asset leaderAsset;
    private Asset adminAsset;

    @BeforeEach
    void setUpStatsData() {
        String suffix = String.valueOf(System.currentTimeMillis());

        Team teamA = teamRepository.save(Objects.requireNonNull(Team.builder()
                .name("统计测试-小组A_" + suffix)
                .description("小组长可见范围")
                .leaderId(leaderUserId)
                .createdBy(adminUserId)
                .build()));

        Team teamB = teamRepository.save(Objects.requireNonNull(Team.builder()
                .name("统计测试-小组B_" + suffix)
                .description("小组长不可见范围")
                .leaderId(adminUserId)
                .createdBy(adminUserId)
                .build()));

        leaderAsset = assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name("小组A的资产")
                .code("STAT-A-" + suffix)
                .targetType("Web应用")
                .targetSource("internal")
                .priority("P1")
                .status("active")
                .ownerId(leaderUserId)
                .teamId(teamA.getId())
                .build()));

        adminAsset = assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name("小组B的资产")
                .code("STAT-B-" + suffix)
                .targetType("IoT设备")
                .targetSource("internal")
                .priority("P2")
                .status("active")
                .ownerId(adminUserId)
                .teamId(teamB.getId())
                .build()));

        newVulnerability(leaderAsset.getId(), "小组A漏洞", "HIGH", "discovered", leaderUserId);
        newVulnerability(adminAsset.getId(), "小组B漏洞1", "CRITICAL", "in_progress", adminUserId);
        newVulnerability(adminAsset.getId(), "小组B漏洞2", "HIGH", "discovered", adminUserId);
    }

    private void newVulnerability(java.util.UUID assetId, String title,
                                  String severity, String workflowStatus, java.util.UUID discovererId) {
        vulnerabilityRepository.save(Objects.requireNonNull(Vulnerability.builder()
                .assetId(assetId)
                .title(title)
                .type("SQL注入")
                .severity(severity)
                .description("统计授权用例")
                .detail("统计授权用例详情")
                .workflowStatus(workflowStatus)
                .customerStatus("pending")
                .disclosureStatus("undisclosed")
                .exploitStage("探测")
                .fieldTestStatus("待测试")
                .vendorFixed(false)
                .discovererId(discovererId)
                .build()));
    }

    // ------------------------------------------------------------------ 角色门槛

    @Test
    @DisplayName("工程师访问三个漏洞统计端点均 403（权限矩阵 2.13）")
    void engineer_forbiddenOnVulnerabilityStats() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/vulnerabilities/stats")
                        .header("Authorization", engineerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/dashboard/vulnerabilities/stats/workflow-status")
                        .header("Authorization", engineerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/dashboard/vulnerabilities/stats/severity")
                        .header("Authorization", engineerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("小组长与管理员可访问漏洞统计")
    void manager_canAccessVulnerabilityStats() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/vulnerabilities/stats")
                        .header("Authorization", leaderToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/dashboard/vulnerabilities/stats")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ 数据收敛

    @Test
    @DisplayName("漏洞统计：小组长只统计可见资产，管理员为全量")
    void vulnerabilityStats_scopedForTeamLeader() throws Exception {
        // 小组长：仅 teamA 的 1 条 HIGH/discovered
        mockMvc.perform(get("/api/v1/dashboard/vulnerabilities/stats")
                        .header("Authorization", leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.by_workflow_status.discovered").value(1))
                .andExpect(jsonPath("$.data.by_workflow_status.in_progress").value(0))
                .andExpect(jsonPath("$.data.by_severity.high").value(1))
                .andExpect(jsonPath("$.data.by_severity.critical").value(0));

        // 管理员：全量 3 条
        mockMvc.perform(get("/api/v1/dashboard/vulnerabilities/stats")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.by_workflow_status.discovered").value(2))
                .andExpect(jsonPath("$.data.by_workflow_status.in_progress").value(1))
                .andExpect(jsonPath("$.data.by_severity.high").value(2))
                .andExpect(jsonPath("$.data.by_severity.critical").value(1));
    }

    @Test
    @DisplayName("漏洞统计子端点：小组长数值小于管理员全量")
    void vulnerabilityStatsSubEndpoints_scoped() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/vulnerabilities/stats/workflow-status")
                        .header("Authorization", leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.discovered").value(1))
                .andExpect(jsonPath("$.data.in_progress").value(0));

        mockMvc.perform(get("/api/v1/dashboard/vulnerabilities/stats/workflow-status")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.discovered").value(2))
                .andExpect(jsonPath("$.data.in_progress").value(1));

        mockMvc.perform(get("/api/v1/dashboard/vulnerabilities/stats/severity")
                        .header("Authorization", leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.high").value(1))
                .andExpect(jsonPath("$.data.critical").value(0));

        mockMvc.perform(get("/api/v1/dashboard/vulnerabilities/stats/severity")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.high").value(2))
                .andExpect(jsonPath("$.data.critical").value(1));
    }

    // ------------------------------------------------------------------ 资产统计

    @Test
    @DisplayName("资产统计：按可见范围收敛（小组长 1 条 / 管理员 2 条 / 工程师 0 条）")
    void assetStats_scopedByVisibility() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/assets/stats")
                        .header("Authorization", leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.by_status.active").value(1))
                .andExpect(jsonPath("$.data.by_priority.P1").value(1))
                .andExpect(jsonPath("$.data.by_priority.P2").value(0));

        mockMvc.perform(get("/api/v1/dashboard/assets/stats")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.by_status.active").value(2))
                .andExpect(jsonPath("$.data.by_priority.P1").value(1))
                .andExpect(jsonPath("$.data.by_priority.P2").value(1));

        // 资产统计未加角色门槛（首屏对所有角色展示），但普通用户只能看到自己可见范围的 0 条
        mockMvc.perform(get("/api/v1/dashboard/assets/stats")
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mockMvc.perform(get("/api/v1/dashboard/assets/stats/status")
                        .header("Authorization", leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(1));

        mockMvc.perform(get("/api/v1/dashboard/assets/stats/priority")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.P1").value(1))
                .andExpect(jsonPath("$.data.P2").value(1));
    }

    // ------------------------------------------------------------------ 概览 / 我的仪表盘

    @Test
    @DisplayName("概览与我的仪表盘：不新增门槛但统计按可见范围收敛（不可作为绕过通道）")
    void overviewAndMyDashboard_areScoped() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/overview")
                        .header("Authorization", leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assets.total").value(1))
                .andExpect(jsonPath("$.data.vulnerabilities.total").value(1));

        mockMvc.perform(get("/api/v1/dashboard/overview")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assets.total").value(2))
                .andExpect(jsonPath("$.data.vulnerabilities.total").value(3));

        // 工程师能打开自己的首屏，但看到的是空范围（不泄露他人数据）
        mockMvc.perform(get("/api/v1/dashboard/my")
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overview.vulnerabilities.total").value(0))
                .andExpect(jsonPath("$.data.overview.assets.total").value(0));

        mockMvc.perform(get("/api/v1/dashboard/my")
                        .header("Authorization", leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.overview.vulnerabilities.total").value(1));
    }
}
