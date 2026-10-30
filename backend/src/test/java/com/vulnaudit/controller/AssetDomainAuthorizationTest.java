package com.vulnaudit.controller;

import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.AssetAccess;
import com.vulnaudit.entity.AssetGroup;
import com.vulnaudit.entity.AssetTag;
import com.vulnaudit.entity.AssetVersion;
import com.vulnaudit.entity.Team;
import com.vulnaudit.repository.AssetAccessRepository;
import com.vulnaudit.repository.AssetGroupRepository;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.AssetTagRepository;
import com.vulnaudit.repository.AssetVersionRepository;
import com.vulnaudit.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Objects;
import java.util.UUID;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 资产域 6 组鉴权缺口收口测试（t49）。
 *
 * <p>覆盖验收报告 §2.3.2 的资产域 6 组缺口：
 * <ol>
 *   <li>{@code POST /assets}：角色门槛 + 团队归属（team_leader 只能在自己领导/所属团队下建资产）；</li>
 *   <li>{@code POST /assets/{assetId}/versions}、{@code PUT /versions/{id}/default|latest}：
 *       {@code canWriteAsset}（写 ⊂ 读）；</li>
 *   <li>{@code GET /assets/{id}/access-requests}：{@code canReadAsset} <b>且</b> 仅负责人/管理层/审批人可见明细；</li>
 *   <li>{@code DELETE /assets/{assetId}/tags/{tagId}}：{@code canWriteAsset}；</li>
 *   <li>分组写（创建/改/删/组内加减）：角色门槛 ADMIN/PROJECT_LEADER/TEAM_LEADER，<b>查询保持全员开放</b>。</li>
 * </ol>
 */
@DisplayName("资产域鉴权测试")
public class AssetDomainAuthorizationTest extends BaseTest {

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private AssetVersionRepository assetVersionRepository;

    @Autowired
    private AssetTagRepository assetTagRepository;

    @Autowired
    private AssetGroupRepository assetGroupRepository;

    @Autowired
    private AssetAccessRepository assetAccessRepository;

    @Autowired
    private TeamRepository teamRepository;

    private Team ownTeam;
    private Team otherTeam;
    private Asset engineerAsset;
    private Asset leaderAsset;
    private Asset adminAsset;
    private AssetVersion engineerVersion;
    private AssetVersion adminVersion;
    private AssetTag engineerTag;
    private AssetTag adminTag;
    private AssetGroup group;

    @BeforeEach
    void setUpAssetDomainData() {
        String suffix = String.valueOf(System.currentTimeMillis());

        ownTeam = teamRepository.save(Objects.requireNonNull(Team.builder()
                .name("资产授权-小组长团队_" + suffix)
                .description("lead by leader")
                .leaderId(leaderUserId)
                .createdBy(adminUserId)
                .build()));

        otherTeam = teamRepository.save(Objects.requireNonNull(Team.builder()
                .name("资产授权-他人团队_" + suffix)
                .description("lead by admin")
                .leaderId(adminUserId)
                .createdBy(adminUserId)
                .build()));

        engineerAsset = assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name("工程师负责资产_" + suffix)
                .code("T49-ENG-" + suffix)
                .targetType("Web应用")
                .targetSource("internal")
                .priority("P1")
                .status("active")
                .ownerId(engineerUserId)
                .teamId(ownTeam.getId())
                .build()));

        leaderAsset = assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name("小组长负责资产_" + suffix)
                .code("T49-LEAD-" + suffix)
                .targetType("Web应用")
                .targetSource("internal")
                .priority("P2")
                .status("active")
                .ownerId(leaderUserId)
                .teamId(ownTeam.getId())
                .build()));

        adminAsset = assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name("管理员负责资产_" + suffix)
                .code("T49-ADM-" + suffix)
                .targetType("Web应用")
                .targetSource("internal")
                .priority("P3")
                .status("active")
                .ownerId(adminUserId)
                .teamId(otherTeam.getId())
                .build()));

        engineerVersion = assetVersionRepository.save(Objects.requireNonNull(AssetVersion.builder()
                .assetId(engineerAsset.getId())
                .versionNo(1)
                .name("v1")
                .isDefault(true)
                .isLatest(true)
                .status("active")
                .createdBy(engineerUserId)
                .build()));

        adminVersion = assetVersionRepository.save(Objects.requireNonNull(AssetVersion.builder()
                .assetId(adminAsset.getId())
                .versionNo(1)
                .name("v1")
                .isDefault(true)
                .isLatest(true)
                .status("active")
                .createdBy(adminUserId)
                .build()));

        engineerTag = assetTagRepository.save(Objects.requireNonNull(AssetTag.builder()
                .assetId(engineerAsset.getId())
                .tag("t49-eng-tag")
                .createdBy(engineerUserId)
                .build()));

        adminTag = assetTagRepository.save(Objects.requireNonNull(AssetTag.builder()
                .assetId(adminAsset.getId())
                .tag("t49-admin-tag")
                .createdBy(adminUserId)
                .build()));

        group = assetGroupRepository.save(Objects.requireNonNull(AssetGroup.builder()
                .name("t49-分组_" + suffix)
                .type("project")
                .description("资产域授权用例")
                .build()));

        // 工程师是「管理员资产」申请的审批人：仍不能越过读范围看到明细（AND 语义）
        assetAccessRepository.save(Objects.requireNonNull(AssetAccess.builder()
                .assetId(adminAsset.getId())
                .userId(leaderUserId)
                .approverId(engineerUserId)
                .status("pending")
                .requestNote("越权可见性用例")
                .build()));
    }

    private String assetBody(String name, UUID teamId) {
        return "{\"name\":\"" + name + "\",\"targetType\":\"Web应用\",\"targetSource\":\"internal\","
                + "\"priority\":\"P2\",\"status\":\"active\""
                + (teamId == null ? "" : ",\"teamId\":\"" + teamId + "\"")
                + "}";
    }

    // ------------------------------------------------------------------ 工程师 6 组全 403

    @Test
    @DisplayName("工程师 6 组写操作 → 全部 403")
    void engineerWrites_forbidden() throws Exception {
        // 1) 建资产（角色门槛）
        mockMvc.perform(post("/api/v1/assets").header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON).content(assetBody("越权资产", ownTeam.getId())))
                .andExpect(status().isForbidden());

        // 2) 给他人资产加版本
        mockMvc.perform(post("/api/v1/assets/" + adminAsset.getId() + "/versions")
                        .header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"v2\"}"))
                .andExpect(status().isForbidden());

        // 3) 默认版本 / 最新版本
        mockMvc.perform(put("/api/v1/assets/" + adminAsset.getId() + "/versions/" + adminVersion.getId() + "/default")
                        .header("Authorization", engineerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/assets/" + adminAsset.getId() + "/versions/" + adminVersion.getId() + "/latest")
                        .header("Authorization", engineerToken))
                .andExpect(status().isForbidden());

        // 4) 访问申请明细
        mockMvc.perform(get("/api/v1/assets/" + adminAsset.getId() + "/access-requests")
                        .header("Authorization", engineerToken))
                .andExpect(status().isForbidden());

        // 5) 删除他人资产标签
        mockMvc.perform(delete("/api/v1/assets/" + adminAsset.getId() + "/tags/" + adminTag.getId())
                        .header("Authorization", engineerToken))
                .andExpect(status().isForbidden());

        // 6) 分组写
        mockMvc.perform(post("/api/v1/asset-groups").header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"越权分组\",\"type\":\"project\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/asset-groups/" + group.getId()).header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"越权改分组\",\"type\":\"project\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/asset-groups/" + group.getId()).header("Authorization", engineerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/asset-groups/" + group.getId() + "/assets")
                        .header("Authorization", engineerToken)
                        .param("assetId", engineerAsset.getId().toString()))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/asset-groups/" + group.getId() + "/assets/" + engineerAsset.getId())
                        .header("Authorization", engineerToken))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ 团队长：团队归属

    @Test
    @DisplayName("团队长建资产：本团队 201、他人团队 403、未指定团队 400（DTO 必填先于鉴权）")
    void teamLeaderCreateAsset_scopedToOwnTeams() throws Exception {
        mockMvc.perform(post("/api/v1/assets").header("Authorization", leaderToken)
                        .contentType(APPLICATION_JSON).content(assetBody("小组长新建资产", ownTeam.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/assets").header("Authorization", leaderToken)
                        .contentType(APPLICATION_JSON).content(assetBody("跨团队资产", otherTeam.getId())))
                .andExpect(status().isForbidden());

        // teamId 为 @NotNull：缺参在参数校验阶段就被拦下（400），早于方法级守卫；
        // 两条路径都拒绝，non-management 不存在「不填团队即绕过」的可能。
        mockMvc.perform(post("/api/v1/assets").header("Authorization", leaderToken)
                        .contentType(APPLICATION_JSON).content(assetBody("无团队资产", null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("团队长写资产：本团队资产可写、他人团队资产 403")
    void teamLeaderWrites_scopedBeyondOwnTeam() throws Exception {
        mockMvc.perform(post("/api/v1/assets/" + leaderAsset.getId() + "/versions")
                        .header("Authorization", leaderToken)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"v2\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/assets/" + adminAsset.getId() + "/versions")
                        .header("Authorization", leaderToken)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"越权版本\"}"))
                .andExpect(status().isForbidden());

        // 管理层可看申请明细（读范围内的资产）
        mockMvc.perform(get("/api/v1/assets/" + leaderAsset.getId() + "/access-requests")
                        .header("Authorization", leaderToken))
                .andExpect(status().isOk());

        // 读范围之外（他人团队资产）→ 403
        mockMvc.perform(get("/api/v1/assets/" + adminAsset.getId() + "/access-requests")
                        .header("Authorization", leaderToken))
                .andExpect(status().isForbidden());

        // 分组写：team_leader 允许
        mockMvc.perform(post("/api/v1/asset-groups").header("Authorization", leaderToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"小组长分组-" + UUID.randomUUID() + "\",\"type\":\"project\"}"))
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------ 负责人可写自己的资产

    @Test
    @DisplayName("资产负责人（工程师）可写自己的资产：版本/默认版本/标签/申请明细")
    void assetOwner_canWriteOwnAsset() throws Exception {
        mockMvc.perform(post("/api/v1/assets/" + engineerAsset.getId() + "/versions")
                        .header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"v2\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/v1/assets/" + engineerAsset.getId() + "/versions/"
                        + engineerVersion.getId() + "/default")
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/assets/" + engineerAsset.getId() + "/versions/"
                        + engineerVersion.getId() + "/latest")
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/assets/" + engineerAsset.getId() + "/tags/" + engineerTag.getId())
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk());

        // 负责人可见自己资产的申请明细
        mockMvc.perform(get("/api/v1/assets/" + engineerAsset.getId() + "/access-requests")
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ 管理员全通

    @Test
    @DisplayName("管理员 6 组写操作全部通过")
    void adminWrites_allowed() throws Exception {
        mockMvc.perform(post("/api/v1/assets").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content(assetBody("管理员新建资产", otherTeam.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/assets/" + adminAsset.getId() + "/versions")
                        .header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"v2\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/v1/assets/" + adminAsset.getId() + "/versions/"
                        + adminVersion.getId() + "/default")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/assets/" + adminAsset.getId() + "/tags/" + adminTag.getId())
                        .header("Authorization", adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/assets/" + adminAsset.getId() + "/access-requests")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/asset-groups").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"管理员分组-" + UUID.randomUUID() + "\",\"type\":\"project\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(put("/api/v1/asset-groups/" + group.getId()).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"管理员改名-" + UUID.randomUUID() + "\",\"type\":\"project\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/asset-groups/" + group.getId() + "/assets")
                        .header("Authorization", adminToken)
                        .param("assetId", engineerAsset.getId().toString()))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/asset-groups/" + group.getId() + "/assets/" + engineerAsset.getId())
                        .header("Authorization", adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(delete("/api/v1/asset-groups/" + group.getId()).header("Authorization", adminToken))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ 查询不受影响

    @Test
    @DisplayName("分组查询对工程师仍 200（前端分组页依赖）")
    void groupQueries_stillOpenForEngineer() throws Exception {
        mockMvc.perform(get("/api/v1/asset-groups").header("Authorization", engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());

        mockMvc.perform(get("/api/v1/asset-groups/" + group.getId()).header("Authorization", engineerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/asset-groups/" + group.getId() + "/assets")
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/asset-groups/type/project").header("Authorization", engineerToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("访问申请明细：审批人身份不越过读范围（AND 语义）")
    void accessRequestDetails_approverStillNeedsReadScope() throws Exception {
        // 工程师是该资产申请的 approver，但对该资产没有读权限 → 仍然 403
        mockMvc.perform(get("/api/v1/assets/" + adminAsset.getId() + "/access-requests")
                        .header("Authorization", engineerToken))
                .andExpect(status().isForbidden());

        // 负责人读自己的资产 → 200
        mockMvc.perform(get("/api/v1/assets/" + engineerAsset.getId() + "/access-requests")
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk());
    }
}
