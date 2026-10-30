package com.vulnaudit.controller;

import com.vulnaudit.entity.AssetVersion;
import com.vulnaudit.entity.Environment;
import com.vulnaudit.entity.Team;
import com.vulnaudit.repository.AssetVersionRepository;
import com.vulnaudit.repository.EnvironmentRepository;
import com.vulnaudit.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Objects;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 高危鉴权缺口收紧测试（t40）：团队 CRUD / 环境 CRUD 与部署 / 公告写。
 *
 * <p>此前这三组写端点<b>既无角色注解也无数据级守卫</b>，任何登录用户都能建团队、删环境、
 * 触发部署、发全员公告（验收报告 §2.3.2 的高 severity 缺口）。本轮按权限矩阵补齐两层：
 * <ol>
 *   <li>角色门槛：团队 CRUD/成员管理 → ADMIN/PROJECT_LEADER；环境 CRUD/部署 → ADMIN/PROJECT_LEADER/TEAM_LEADER；
 *       公告写 → ADMIN/PROJECT_LEADER；</li>
 *   <li>数据级守卫：团队改/删/成员增删额外要求 {@code @accessGuard.canManageTeam(#id)}，
 *       团队长只能管理自己领导的团队。</li>
 * </ol>
 *
 * <p>同时验证「查询类端点保持开放」：工程师仍可读团队列表/详情/成员、环境列表/详情、公告列表，
 * 否则前端团队树与环境页签会白屏。
 */
@DisplayName("高危写端点授权测试")
public class PrivilegedWriteAuthorizationTest extends BaseTest {

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private AssetVersionRepository assetVersionRepository;

    @Autowired
    private EnvironmentRepository environmentRepository;

    private Team ownTeam;
    private Team otherTeam;
    private Environment testEnvironment;
    private String teamBody;
    private String envBody;

    @BeforeEach
    void setUpPrivilegedData() {
        String suffix = String.valueOf(System.currentTimeMillis());

        ownTeam = teamRepository.save(Objects.requireNonNull(Team.builder()
                .name("写授权-小组长团队_" + suffix)
                .description("小组长可管理")
                .leaderId(leaderUserId)
                .createdBy(adminUserId)
                .build()));

        otherTeam = teamRepository.save(Objects.requireNonNull(Team.builder()
                .name("写授权-他人团队_" + suffix)
                .description("小组长不可管理")
                .leaderId(adminUserId)
                .createdBy(adminUserId)
                .build()));

        teamBody = "{\"name\":\"新建团队_" + suffix + "\",\"description\":\"写授权用例\",\"createdBy\":\""
                + adminUserId + "\"}";

        UUID assetId = UUID.randomUUID();
        UUID templateId = UUID.randomUUID();
        AssetVersion version = assetVersionRepository.save(Objects.requireNonNull(AssetVersion.builder()
                .assetId(assetId)
                .versionNo(1)
                .name("v1")
                .isDefault(true)
                .isLatest(true)
                .status("active")
                .createdBy(adminUserId)
                .build()));

        testEnvironment = environmentRepository.save(Objects.requireNonNull(Environment.builder()
                .name("写授权-测试环境_" + suffix)
                .assetId(assetId)
                .assetVersionId(version.getId())
                .templateId(templateId)
                .config("{}")
                .status("created")
                .version(1)
                .createdBy(adminUserId)
                .build()));

        envBody = "{\"name\":\"新建环境_" + suffix + "\",\"assetId\":\"" + assetId
                + "\",\"templateId\":\"" + templateId + "\",\"config\":\"{}\",\"createdBy\":\""
                + adminUserId + "\"}";
    }

    // ------------------------------------------------------------------ 团队写

    @Test
    @DisplayName("工程师建/改/删团队与加成员 → 全部 403")
    void teamWrites_forbiddenForEngineer() throws Exception {
        mockMvc.perform(post("/api/v1/teams").header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON).content(teamBody))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/teams/" + ownTeam.getId()).header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON).content(teamBody))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/teams/" + ownTeam.getId()).header("Authorization", engineerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/teams/" + ownTeam.getId() + "/members")
                        .header("Authorization", engineerToken)
                        .param("userId", engineerUserId.toString())
                        .param("role", "member"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/teams/" + ownTeam.getId() + "/members/" + engineerUserId)
                        .header("Authorization", engineerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("团队长管理自己团队 → 200；管理他人团队 → 403（数据级守卫）")
    void teamWrites_teamLeaderScopedToOwnTeam() throws Exception {
        mockMvc.perform(put("/api/v1/teams/" + ownTeam.getId()).header("Authorization", leaderToken)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"写授权-改名_" + ownTeam.getId() + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/teams/" + otherTeam.getId()).header("Authorization", leaderToken)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"越权改名\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/teams/" + ownTeam.getId() + "/members")
                        .header("Authorization", leaderToken)
                        .param("userId", engineerUserId.toString())
                        .param("role", "member"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/teams/" + ownTeam.getId() + "/members/" + engineerUserId)
                        .header("Authorization", leaderToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/teams/" + otherTeam.getId() + "/members")
                        .header("Authorization", leaderToken)
                        .param("userId", engineerUserId.toString())
                        .param("role", "member"))
                .andExpect(status().isForbidden());

        // 团队长不能建团队（矩阵：仅 admin/project_leader）
        mockMvc.perform(post("/api/v1/teams").header("Authorization", leaderToken)
                        .contentType(APPLICATION_JSON).content(teamBody))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("管理员团队写操作 → 201/200")
    void teamWrites_allowedForAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/teams").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content(teamBody))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/v1/teams/" + otherTeam.getId()).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"管理员改名_" + otherTeam.getId() + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/teams/" + otherTeam.getId()).header("Authorization", adminToken))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ 环境写

    @Test
    @DisplayName("工程师建/改/删环境与部署/启动/停止/快照 → 全部 403")
    void environmentWrites_forbiddenForEngineer() throws Exception {
        mockMvc.perform(post("/api/v1/environments").header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON).content(envBody))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/environments/" + testEnvironment.getId())
                        .header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"越权改名\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/environments/" + testEnvironment.getId())
                        .header("Authorization", engineerToken))
                .andExpect(status().isForbidden());

        for (String action : new String[]{"deploy", "start", "stop", "snapshots"}) {
            // snapshots 需要 name 参数：缺参会在方法级守卫之前被 400 拦掉，这里补上以验证真正的 403
            mockMvc.perform(post("/api/v1/environments/" + testEnvironment.getId() + "/" + action)
                            .header("Authorization", engineerToken)
                            .param("name", "越权快照"))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    @DisplayName("团队长可管理环境（角色门槛 ADMIN/PROJECT_LEADER/TEAM_LEADER）")
    void environmentWrites_allowedForTeamLeader() throws Exception {
        mockMvc.perform(post("/api/v1/environments").header("Authorization", leaderToken)
                        .contentType(APPLICATION_JSON).content(envBody))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/environments/" + testEnvironment.getId())
                        .header("Authorization", leaderToken)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"小组长改名\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("管理员删除环境 → 200")
    void environmentWrites_allowedForAdmin() throws Exception {
        mockMvc.perform(delete("/api/v1/environments/" + testEnvironment.getId())
                        .header("Authorization", adminToken))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ 公告写

    @Test
    @DisplayName("工程师发/改/删公告 → 全部 403")
    void announcementWrites_forbiddenForEngineer() throws Exception {
        String body = "{\"title\":\"越权公告\",\"content\":\"不应创建\",\"type\":\"system\"}";

        mockMvc.perform(post("/api/v1/notifications/announcements")
                        .header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/notifications/announcements/" + UUID.randomUUID())
                        .header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/notifications/announcements/" + UUID.randomUUID())
                        .header("Authorization", engineerToken))
                .andExpect(status().isForbidden());

        // 团队长同样不能发全员公告（矩阵：仅 admin/project_leader）
        mockMvc.perform(post("/api/v1/notifications/announcements")
                        .header("Authorization", leaderToken)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("管理员发公告 → 201，且公告列表可读")
    void announcementWrites_allowedForAdmin() throws Exception {
        String body = "{\"title\":\"写授权公告\",\"content\":\"管理员发布\",\"type\":\"system\"}";

        mockMvc.perform(post("/api/v1/notifications/announcements")
                        .header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/notifications/announcements")
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());
    }

    // ------------------------------------------------------------------ 查询保持开放

    @Test
    @DisplayName("查询类端点对工程师仍 200（团队 / 环境 / 公告）")
    void readEndpoints_stillOpenForEngineer() throws Exception {
        mockMvc.perform(get("/api/v1/teams").header("Authorization", engineerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/teams/all").header("Authorization", engineerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/teams/roots").header("Authorization", engineerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/teams/" + ownTeam.getId()).header("Authorization", engineerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/teams/" + ownTeam.getId() + "/members")
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/teams/" + ownTeam.getId() + "/tree")
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/environments/" + testEnvironment.getId())
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/environments/" + testEnvironment.getId() + "/snapshots")
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/notifications/announcements")
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/notifications/announcements/active")
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("团队长与管理员查询同样 200（不因写门槛波及读）")
    void readEndpoints_stillOpenForManagers() throws Exception {
        mockMvc.perform(get("/api/v1/teams").header("Authorization", leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());

        mockMvc.perform(get("/api/v1/teams").header("Authorization", adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/environments").header("Authorization", leaderToken))
                .andExpect(status().isOk());
    }
}
