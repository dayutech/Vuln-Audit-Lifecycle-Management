package com.vulnaudit.controller;

import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.Team;
import com.vulnaudit.entity.TeamMember;
import com.vulnaudit.repository.AssetAccessRepository;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.TeamMemberRepository;
import com.vulnaudit.repository.TeamRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.Objects;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 审批路由与审批范围测试。
 *
 * <p>覆盖 SRS 3.7.3：申请按「直属领导（具备审批范围）→ 项目负责人 → 管理员」路由，
 * 审批人只能审批自己有权处理的申请。
 */
@DisplayName("审批路由与审批范围测试")
public class ApprovalFlowTest extends BaseTest {

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private AssetAccessRepository assetAccessRepository;

    private Team createTeamWithLeader() {
        Team team = teamRepository.save(Objects.requireNonNull(Team.builder()
                .name("审批测试团队_" + System.nanoTime())
                .description("审批路由测试")
                .leaderId(leaderUserId)
                .createdBy(adminUserId)
                .build()));
        teamMemberRepository.save(Objects.requireNonNull(TeamMember.builder()
                .teamId(team.getId())
                .userId(engineerUserId)
                .isLeader(false)
                .build()));
        return team;
    }

    private Asset createAsset(Team team) {
        return assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name("审批测试资产")
                .code("AST-APPROVAL-" + System.nanoTime())
                .targetType("Web应用")
                .targetSource("客户委托")
                .priority("P1")
                .status("active")
                .ownerId(adminUserId)
                .teamId(team.getId())
                .build()));
    }

    @Test
    @DisplayName("访问申请按团队审批范围路由到直属领导，且领导可审批")
    void accessRequest_RoutesToLeader_AndLeaderApproves() throws Exception {
        Team team = createTeamWithLeader();
        Asset asset = createAsset(team);

        // 管理员为组长授予该团队的审批范围
        mockMvc.perform(post("/api/v1/approval-scopes")
                        .header("Authorization", adminToken)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content("{\"approverId\":\"" + leaderUserId + "\",\"scopeType\":\"team\",\"scopeId\":\""
                                + team.getId() + "\"}"))
                .andExpect(status().isCreated());

        // 工程师提交访问申请 → 审批人应被路由为组长
        String body = mockMvc.perform(post("/api/v1/assets/" + asset.getId() + "/access-requests")
                        .header("Authorization", engineerToken)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content("{\"canViewFiles\":true,\"requestNote\":\"需要查看安装包\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.approverId").value(leaderUserId.toString()))
                .andReturn().getResponse().getContentAsString();

        String requestId = body.replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");

        // 组长的待审批列表应包含该申请
        mockMvc.perform(get("/api/v1/assets/my/access-approval-requests")
                        .header("Authorization", leaderToken)
                        .param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        // 组长审批通过
        mockMvc.perform(post("/api/v1/assets/access-requests/" + requestId + "/review")
                        .header("Authorization", leaderToken)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content("{\"approved\":true,\"reviewNote\":\"同意\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("approved"));

        // 审批通过后应产生可用的访问授权
        mockMvc.perform(get("/api/v1/assets/" + asset.getId())
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("无审批范围的工程师不能审批他人申请")
    void reviewAccessRequest_ByEngineer_Forbidden() throws Exception {
        Team team = createTeamWithLeader();
        Asset asset = createAsset(team);

        String body = mockMvc.perform(post("/api/v1/assets/" + asset.getId() + "/access-requests")
                        .header("Authorization", engineerToken)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content("{\"canViewFiles\":true}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String requestId = body.replaceAll("(?s).*\"id\":\"([^\"]+)\".*", "$1");

        // 工程师本人审批自己的申请应被拒绝（既无角色也无范围）
        mockMvc.perform(post("/api/v1/assets/access-requests/" + requestId + "/review")
                        .header("Authorization", engineerToken)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content("{\"approved\":true}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("非管理层只能看到路由给自己的待审批申请")
    void pendingApprovals_ScopedToRoutee() throws Exception {
        Team team = createTeamWithLeader();
        Asset asset = createAsset(team);

        mockMvc.perform(post("/api/v1/assets/" + asset.getId() + "/access-requests")
                        .header("Authorization", engineerToken)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content("{\"canViewFiles\":false}"))
                .andExpect(status().isCreated());

        // 工程师自己：待审批为 0
        mockMvc.perform(get("/api/v1/assets/my/access-approval-requests")
                        .header("Authorization", engineerToken)
                        .param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        // 管理员：全局可见，应为 1
        mockMvc.perform(get("/api/v1/assets/my/access-approval-requests")
                        .header("Authorization", adminToken)
                        .param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }
}
