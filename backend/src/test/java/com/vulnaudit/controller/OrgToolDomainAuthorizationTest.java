package com.vulnaudit.controller;

import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.Team;
import com.vulnaudit.entity.TeamMember;
import com.vulnaudit.entity.Tool;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.TeamMemberRepository;
import com.vulnaudit.repository.TeamRepository;
import com.vulnaudit.repository.ToolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 团队读与工具写 2 组鉴权收口测试（t51，缺口清零）。
 *
 * <p>覆盖：
 * <ol>
 *   <li><b>#11 团队读</b>：{@code GET /teams/{id}/members}、{@code /{id}/users} 收敛到「可见团队」
 *       —— 自己领导/所属团队及其子团队可见，管理层全量，其它 403（成员列表属组织架构数据）；</li>
 *   <li><b>#12 工具写</b>：{@code PUT /tools/{id}} 与 {@code POST /tools/{id}/versions} → ADMIN/PROJECT_LEADER/TEAM_LEADER；
 *       {@code DELETE /tools/{id}} → ADMIN/PROJECT_LEADER；工具查询/下载保持全员开放；</li>
 *   <li><b>追加</b>：{@code POST /assets/{id}/tags}（新增标签）改为 {@code canWriteAsset}。</li>
 * </ol>
 */
@DisplayName("团队读与工具写鉴权测试")
public class OrgToolDomainAuthorizationTest extends BaseTest {

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    @Autowired
    private ToolRepository toolRepository;

    @Autowired
    private AssetRepository assetRepository;

    private Team engineerTeam;
    private Team otherTeam;
    private Tool tool;
    private Asset engineerAsset;
    private Asset otherAsset;

    @BeforeEach
    void setUpOrgToolData() {
        String suffix = String.valueOf(System.currentTimeMillis());

        engineerTeam = teamRepository.save(Objects.requireNonNull(Team.builder()
                .name("t51-工程师团队_" + suffix)
                .description("工程师所属团队")
                .leaderId(leaderUserId)
                .createdBy(adminUserId)
                .build()));

        otherTeam = teamRepository.save(Objects.requireNonNull(Team.builder()
                .name("t51-他人团队_" + suffix)
                .description("工程师不所属")
                .leaderId(adminUserId)
                .createdBy(adminUserId)
                .build()));

        // 工程师是 engineerTeam 的成员（非领导），与 otherTeam 无关
        teamMemberRepository.save(Objects.requireNonNull(TeamMember.builder()
                .teamId(engineerTeam.getId())
                .userId(engineerUserId)
                .isLeader(false)
                .status("available")
                .build()));

        tool = toolRepository.save(Objects.requireNonNull(Tool.builder()
                .name("t51-工具_" + suffix)
                .category("分析工具")
                .description("工具域授权用例")
                .isOfficial(false)
                .createdBy(adminUserId)
                .build()));

        engineerAsset = assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name("t51-工程师资产_" + suffix)
                .code("T51-ENG-" + suffix)
                .targetType("Web应用")
                .targetSource("internal")
                .priority("P1")
                .status("active")
                .ownerId(engineerUserId)
                .teamId(engineerTeam.getId())
                .build()));

        otherAsset = assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name("t51-他人资产_" + suffix)
                .code("T51-OTH-" + suffix)
                .targetType("Web应用")
                .targetSource("internal")
                .priority("P2")
                .status("active")
                .ownerId(adminUserId)
                .teamId(otherTeam.getId())
                .build()));
    }

    private MockMultipartFile versionFile() {
        return new MockMultipartFile("file", "v1.zip", "application/zip",
                "t51-tool-version".getBytes(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------------ #11 团队读

    @Test
    @DisplayName("工程师读成员列表：自己所属团队 200、他人团队 403")
    void teamMembers_scopedToVisibleTeams() throws Exception {
        mockMvc.perform(get("/api/v1/teams/" + engineerTeam.getId() + "/members")
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/teams/" + engineerTeam.getId() + "/users")
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/teams/" + otherTeam.getId() + "/members")
                        .header("Authorization", engineerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/teams/" + otherTeam.getId() + "/users")
                        .header("Authorization", engineerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("团队长读成员列表：自己领导团队 200、无关团队 403；管理员全量 200")
    void teamMembers_leaderScopedAndAdminUnrestricted() throws Exception {
        mockMvc.perform(get("/api/v1/teams/" + engineerTeam.getId() + "/members")
                        .header("Authorization", leaderToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/teams/" + otherTeam.getId() + "/members")
                        .header("Authorization", leaderToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/teams/" + engineerTeam.getId() + "/members")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/teams/" + otherTeam.getId() + "/members")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("子团队可读：父团队领导可读其成员列表")
    void teamMembers_childTeamReadableByParentLeader() throws Exception {
        Team child = teamRepository.save(Objects.requireNonNull(Team.builder()
                .name("t51-子团队_" + System.currentTimeMillis())
                .description("engineerTeam 的子团队")
                .parentId(engineerTeam.getId())
                .leaderId(adminUserId)
                .createdBy(adminUserId)
                .build()));

        mockMvc.perform(get("/api/v1/teams/" + child.getId() + "/members")
                        .header("Authorization", leaderToken))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ #12 工具写

    @Test
    @DisplayName("工程师改/删工具与上传版本 → 全部 403")
    void toolWrites_forbiddenForEngineer() throws Exception {
        mockMvc.perform(put("/api/v1/tools/" + tool.getId()).header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"越权改名\",\"category\":\"分析工具\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/tools/" + tool.getId()).header("Authorization", engineerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(multipart("/api/v1/tools/" + tool.getId() + "/versions")
                        .file(versionFile())
                        .param("version", "v1")
                        .param("changelog", "越权上传")
                        .header("Authorization", engineerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("团队长可改工具与上传版本（createdBy 落库）但不能删除；管理员全通")
    void toolWrites_leaderAndAdmin() throws Exception {
        mockMvc.perform(put("/api/v1/tools/" + tool.getId()).header("Authorization", leaderToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"小组长改名\",\"category\":\"分析工具\"}"))
                .andExpect(status().isOk());

        // t23 回归：版本上传仍写 createdBy（真实落盘）
        mockMvc.perform(multipart("/api/v1/tools/" + tool.getId() + "/versions")
                        .file(versionFile())
                        .param("version", "v1")
                        .param("changelog", "小组长上传")
                        .header("Authorization", leaderToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.createdBy").value(leaderUserId.toString()))
                .andExpect(jsonPath("$.data.hasFile").value(true))
                .andExpect(jsonPath("$.data.fileName", notNullValue()));

        mockMvc.perform(delete("/api/v1/tools/" + tool.getId()).header("Authorization", leaderToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/tools/" + tool.getId()).header("Authorization", adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("工具查询与下载对工程师仍 200")
    void toolReads_stillOpenForEngineer() throws Exception {
        mockMvc.perform(get("/api/v1/tools").header("Authorization", engineerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tools/" + tool.getId()).header("Authorization", engineerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tools/" + tool.getId() + "/versions")
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tools/" + tool.getId() + "/download-count")
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tools/" + tool.getId() + "/tags")
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ 追加：资产标签新增

    @Test
    @DisplayName("新增资产标签：工程师给他人资产 403、给自己负责资产 201")
    void addAssetTag_requiresAssetWrite() throws Exception {
        // 该端点是 @RequestParam tag（非 JSON body），成功时返回 201
        mockMvc.perform(post("/api/v1/assets/" + otherAsset.getId() + "/tags")
                        .header("Authorization", engineerToken)
                        .param("tag", "t51-越权标签"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/assets/" + engineerAsset.getId() + "/tags")
                        .header("Authorization", engineerToken)
                        .param("tag", "t51-自有标签"))
                .andExpect(status().isCreated());
    }
}
