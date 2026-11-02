package com.vulnaudit.controller;

import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.AssetProjectLink;
import com.vulnaudit.entity.Customer;
import com.vulnaudit.entity.Project;
import com.vulnaudit.entity.ProjectPhase;
import com.vulnaudit.repository.AssetProjectLinkRepository;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.CustomerRepository;
import com.vulnaudit.repository.ProjectPhaseRepository;
import com.vulnaudit.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 资产—项目挂靠 API 测试（t69）
 *
 * <p>覆盖硬约束：建资产必须带 projectId（同事务建挂靠）、项目/客户不存在 404、
 * 期次不属于项目/项目未分期 400、重复挂靠 409、跨客户挂靠允许、
 * 解绑最后一条 409 / 非最后一条 200、列表按 customerId/projectId/phaseId 过滤、
 * 资产详情 projectLinks、{@code /projects/{id}/assets} 按期次过滤与分组。
 */
@DisplayName("资产-项目挂靠 API 测试")
class AssetProjectLinkTest extends BaseTest {

    @Autowired private CustomerRepository customerRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectPhaseRepository projectPhaseRepository;
    @Autowired private AssetProjectLinkRepository linkRepository;
    @Autowired private AssetRepository assetRepository;

    private Customer customerA;
    private Customer customerB;
    private Project projectA;          // customerA，分期
    private Project projectA2;         // customerA，分期（用于「非最后一条」解绑）
    private Project projectB;          // customerB（跨客户）
    private ProjectPhase phaseA1;
    private ProjectPhase phaseA2;

    @BeforeEach
    void setUpProjectFixtures() {
        String suffix = String.valueOf(System.nanoTime());
        customerA = customerRepository.save(Objects.requireNonNull(Customer.builder()
                .code("CUST-T69A-" + suffix).name("T69 客户A").level("A").status("active")
                .createdBy(adminUserId).build()));
        customerB = customerRepository.save(Objects.requireNonNull(Customer.builder()
                .code("CUST-T69B-" + suffix).name("T69 客户B").level("B").status("active")
                .createdBy(adminUserId).build()));

        projectA = projectRepository.save(Objects.requireNonNull(Project.builder()
                .code("PRJ-T69A-" + suffix).customerId(customerA.getId()).name("T69 项目A")
                .type("PENTEST").status("ACTIVE").managerId(adminUserId).isPhased(true)
                .createdBy(adminUserId).build()));
        projectA2 = projectRepository.save(Objects.requireNonNull(Project.builder()
                .code("PRJ-T69A2-" + suffix).customerId(customerA.getId()).name("T69 项目A2")
                .type("PENTEST").status("ACTIVE").managerId(adminUserId).isPhased(false)
                .createdBy(adminUserId).build()));
        projectB = projectRepository.save(Objects.requireNonNull(Project.builder()
                .code("PRJ-T69B-" + suffix).customerId(customerB.getId()).name("T69 项目B")
                .type("CODE_AUDIT").status("ACTIVE").managerId(adminUserId).isPhased(false)
                .createdBy(adminUserId).build()));

        phaseA1 = projectPhaseRepository.save(Objects.requireNonNull(ProjectPhase.builder()
                .projectId(projectA.getId()).phaseNo(1).name("一期").status("ACTIVE").build()));
        phaseA2 = projectPhaseRepository.save(Objects.requireNonNull(ProjectPhase.builder()
                .projectId(projectA.getId()).phaseNo(2).name("二期").status("PLANNED").build()));
    }

    // ------------------------------------------------------------------ 工具方法

    private String createAssetBody(String name, UUID projectId, UUID phaseId) {
        StringBuilder body = new StringBuilder()
                .append("{\"name\":\"").append(name).append("\",")
                .append("\"targetType\":\"web\",\"targetSource\":\"external\",")
                .append("\"description\":\"t69 用例\",\"priority\":\"P2\",\"status\":\"active\",")
                .append("\"teamId\":\"").append(teamId()).append("\",")
                .append("\"projectId\":\"").append(projectId).append("\"");
        if (phaseId != null) {
            body.append(",\"phaseId\":\"").append(phaseId).append("\"");
        }
        return body.append("}").toString();
    }

    private UUID teamId() {
        // BaseTest 未建团队时用随机 UUID：canCreateAssetInTeam 对 admin 放行，AssetService 会校验团队存在
        return ensureTeam();
    }

    private UUID cachedTeamId;

    private UUID ensureTeam() {
        if (cachedTeamId != null) {
            return cachedTeamId;
        }
        cachedTeamId = teamRepository.save(Objects.requireNonNull(
                com.vulnaudit.entity.Team.builder()
                        .name("T69 团队-" + UUID.randomUUID())
                        .leaderId(adminUserId)
                        .createdBy(adminUserId)
                        .build())).getId();
        return cachedTeamId;
    }

    @Autowired private com.vulnaudit.repository.TeamRepository teamRepository;

    private Asset createAssetDirectly(String name) {
        return assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name(name).code("T69-" + UUID.randomUUID().toString().substring(0, 8))
                .targetType("web").targetSource("internal").description("t69")
                .priority("P2").status("active").ownerId(adminUserId).teamId(ensureTeam()).build()));
    }

    private AssetProjectLink linkDirectly(Asset asset, Project project, ProjectPhase phase) {
        return linkRepository.saveAndFlush(Objects.requireNonNull(AssetProjectLink.builder()
                .assetId(asset.getId())
                .projectId(project.getId())
                .phaseId(phase != null ? phase.getId() : null)
                .phaseKey(phase != null ? phase.getId() : AssetProjectLink.PHASE_KEY_NONE)
                .customerId(project.getCustomerId())
                .linkType("TARGET")
                .linkedBy(adminUserId)
                .build()));
    }

    // ------------------------------------------------------------------ 建资产必须挂项目

    @Test
    @DisplayName("建资产带 projectId → 201 且挂靠落库")
    void createAsset_withProject_createsLink() throws Exception {
        String body = createAssetBody("t69-带项目", projectA.getId(), phaseA1.getId());

        String response = mockMvc.perform(post("/api/v1/assets")
                        .header("Authorization", adminToken)
                        .contentType(Objects.requireNonNull(APPLICATION_JSON))
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").exists())
                .andReturn().getResponse().getContentAsString();

        UUID assetId = UUID.fromString(objectMapper.readTree(response).path("data").path("id").asText());
        List<AssetProjectLink> links = linkRepository.findByAssetId(assetId);
        assertEquals(1, links.size(), "创建资产必须同时落一条挂靠");
        AssetProjectLink link = links.get(0);
        assertEquals(projectA.getId(), link.getProjectId());
        assertEquals(phaseA1.getId(), link.getPhaseId());
        assertEquals(customerA.getId(), link.getCustomerId(), "customerId 取自项目");
        assertEquals("TARGET", link.getLinkType(), "缺省挂靠类型 TARGET");
        assertNotNull(link.getLinkedBy());
    }

    @Test
    @DisplayName("建资产不带 projectId → 400")
    void createAsset_withoutProject_400() throws Exception {
        String body = "{\"name\":\"t69-无项目\",\"targetType\":\"web\",\"targetSource\":\"external\","
                + "\"priority\":\"P2\",\"status\":\"active\",\"teamId\":\"" + ensureTeam() + "\"}";

        mockMvc.perform(post("/api/v1/assets")
                        .header("Authorization", adminToken)
                        .contentType(Objects.requireNonNull(APPLICATION_JSON))
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.projectId").exists());
    }

    @Test
    @DisplayName("建资产 projectId 不存在 → 404")
    void createAsset_projectNotFound_404() throws Exception {
        mockMvc.perform(post("/api/v1/assets")
                        .header("Authorization", adminToken)
                        .contentType(Objects.requireNonNull(APPLICATION_JSON))
                        .content(createAssetBody("t69-项目不存在", UUID.randomUUID(), null)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("建资产期次不属于项目 → 400；项目未分期传期次 → 400")
    void createAsset_phaseMismatch_400() throws Exception {
        Project otherProject = projectRepository.save(Objects.requireNonNull(Project.builder()
                .code("PRJ-T69C-" + System.nanoTime()).customerId(customerB.getId()).name("T69 其他项目")
                .type("PENTEST").status("ACTIVE").managerId(adminUserId).isPhased(true).build()));
        ProjectPhase otherPhase = projectPhaseRepository.save(Objects.requireNonNull(ProjectPhase.builder()
                .projectId(otherProject.getId()).phaseNo(1).name("他项目一期").build()));

        // 期次属于 otherProject，却挂到 projectA → 400
        mockMvc.perform(post("/api/v1/assets")
                        .header("Authorization", adminToken)
                        .contentType(Objects.requireNonNull(APPLICATION_JSON))
                        .content(createAssetBody("t69-期次错配", projectA.getId(), otherPhase.getId())))
                .andExpect(status().isBadRequest());

        // projectA2 未启用分期，传期次 → 400
        mockMvc.perform(post("/api/v1/assets")
                        .header("Authorization", adminToken)
                        .contentType(Objects.requireNonNull(APPLICATION_JSON))
                        .content(createAssetBody("t69-未分期", projectA2.getId(), phaseA1.getId())))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ 追加 / 重复 / 跨客户

    @Test
    @DisplayName("重复挂靠同一 (资产,项目,期次) → 409")
    void addLink_duplicate_409() throws Exception {
        Asset asset = createAssetDirectly("t69-重复挂靠");
        linkDirectly(asset, projectA, phaseA1);

        mockMvc.perform(post("/api/v1/assets/" + asset.getId() + "/project-links")
                        .header("Authorization", adminToken)
                        .contentType(Objects.requireNonNull(APPLICATION_JSON))
                        .content("{\"projectId\":\"" + projectA.getId() + "\",\"phaseId\":\""
                                + phaseA1.getId() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("已挂靠")));
    }

    @Test
    @DisplayName("跨客户挂靠允许：同一资产可挂 A、B 两个客户的项目")
    void addLink_crossCustomer_allowed() throws Exception {
        Asset asset = createAssetDirectly("t69-跨客户");
        linkDirectly(asset, projectA, null);

        mockMvc.perform(post("/api/v1/assets/" + asset.getId() + "/project-links")
                        .header("Authorization", adminToken)
                        .contentType(Objects.requireNonNull(APPLICATION_JSON))
                        .content("{\"projectId\":\"" + projectB.getId() + "\",\"linkType\":\"SHARED\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.customerId").value(customerB.getId().toString()))
                .andExpect(jsonPath("$.data.projectName").value("T69 项目B"))
                .andExpect(jsonPath("$.data.linkType").value("SHARED"));

        List<AssetProjectLink> links = linkRepository.findByAssetId(asset.getId());
        assertEquals(2, links.size());
        assertTrue(links.stream().anyMatch(link -> customerA.getId().equals(link.getCustomerId())));
        assertTrue(links.stream().anyMatch(link -> customerB.getId().equals(link.getCustomerId())));
    }

    @Test
    @DisplayName("追加挂靠：资产/项目不存在 → 404，期次不属于项目 → 400")
    void addLink_notFoundAndInvalidPhase() throws Exception {
        Asset asset = createAssetDirectly("t69-追加异常");

        mockMvc.perform(post("/api/v1/assets/" + UUID.randomUUID() + "/project-links")
                        .header("Authorization", adminToken)
                        .contentType(Objects.requireNonNull(APPLICATION_JSON))
                        .content("{\"projectId\":\"" + projectA.getId() + "\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/assets/" + asset.getId() + "/project-links")
                        .header("Authorization", adminToken)
                        .contentType(Objects.requireNonNull(APPLICATION_JSON))
                        .content("{\"projectId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/assets/" + asset.getId() + "/project-links")
                        .header("Authorization", adminToken)
                        .contentType(Objects.requireNonNull(APPLICATION_JSON))
                        .content("{\"projectId\":\"" + projectA2.getId() + "\",\"phaseId\":\""
                                + phaseA1.getId() + "\"}"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ 解绑

    @Test
    @DisplayName("解绑最后一条挂靠 → 409（资产必须至少挂靠一个项目）")
    void unlink_lastLink_409() throws Exception {
        Asset asset = createAssetDirectly("t69-解绑最后一条");
        AssetProjectLink only = linkDirectly(asset, projectA, null);

        mockMvc.perform(delete("/api/v1/assets/" + asset.getId() + "/project-links/" + only.getId())
                        .header("Authorization", adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("至少挂靠一个项目")));

        assertEquals(1, linkRepository.findByAssetId(asset.getId()).size(), "挂靠不应被删除");
    }

    @Test
    @DisplayName("解绑非最后一条 → 200；解绑他人资产的挂靠 → 404")
    void unlink_notLast_200_andWrongAsset_404() throws Exception {
        Asset asset = createAssetDirectly("t69-解绑正常");
        AssetProjectLink first = linkDirectly(asset, projectA, null);
        AssetProjectLink second = linkDirectly(asset, projectB, null);

        // 用另一资产的 id 删 → 404（不泄露挂靠存在性）
        Asset other = createAssetDirectly("t69-他人资产");
        linkDirectly(other, projectA2, null);
        mockMvc.perform(delete("/api/v1/assets/" + other.getId() + "/project-links/" + first.getId())
                        .header("Authorization", adminToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/assets/" + asset.getId() + "/project-links/" + second.getId())
                        .header("Authorization", adminToken))
                .andExpect(status().isOk());

        List<AssetProjectLink> remaining = linkRepository.findByAssetId(asset.getId());
        assertEquals(1, remaining.size());
        assertEquals(first.getId(), remaining.get(0).getId());
    }

    // ------------------------------------------------------------------ 查询

    @Test
    @DisplayName("资产列表按 customerId / projectId / phaseId 过滤正确")
    void listAssets_filterByOwnership() throws Exception {
        Asset inA = createAssetDirectly("t69-客户A项目");
        linkDirectly(inA, projectA, phaseA1);
        Asset inB = createAssetDirectly("t69-客户B项目");
        linkDirectly(inB, projectB, null);

        // customerId=A → 只看到 inA
        mockMvc.perform(get("/api/v1/assets")
                        .param("customerId", customerA.getId().toString())
                        .param("size", "100")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].id",
                        org.hamcrest.Matchers.hasItem(inA.getId().toString())))
                .andExpect(jsonPath("$.data.content[*].id",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(inB.getId().toString()))));

        // projectId=B → 只看到 inB
        mockMvc.perform(get("/api/v1/assets")
                        .param("projectId", projectB.getId().toString())
                        .param("size", "100")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].id",
                        org.hamcrest.Matchers.hasItem(inB.getId().toString())))
                .andExpect(jsonPath("$.data.content[*].id",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(inA.getId().toString()))));

        // phaseId=phaseA2 → 无命中（空页），且不会退化成全量
        mockMvc.perform(get("/api/v1/assets")
                        .param("phaseId", phaseA2.getId().toString())
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("资产列表附带 customers / projects 摘要")
    void listAssets_includesOwnershipBrief() throws Exception {
        Asset asset = createAssetDirectly("t69-摘要");
        linkDirectly(asset, projectA, null);
        linkDirectly(asset, projectB, null);

        mockMvc.perform(get("/api/v1/assets")
                        .param("projectId", projectA.getId().toString())
                        .param("size", "10")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].customers", hasSize(2)))
                .andExpect(jsonPath("$.data.content[0].projects", hasSize(2)))
                .andExpect(jsonPath("$.data.content[0].customers[*].name",
                        org.hamcrest.Matchers.hasItem("T69 客户A")))
                .andExpect(jsonPath("$.data.content[0].projects[*].name",
                        org.hamcrest.Matchers.hasItem("T69 项目B")));
    }

    @Test
    @DisplayName("资产详情 projectLinks 完整（项目/客户/期次名称齐全）")
    void assetDetail_includesProjectLinks() throws Exception {
        Asset asset = createAssetDirectly("t69-详情");
        linkDirectly(asset, projectA, phaseA2);

        mockMvc.perform(get("/api/v1/assets/" + asset.getId())
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectLinks", hasSize(1)))
                .andExpect(jsonPath("$.data.projectLinks[0].projectId").value(projectA.getId().toString()))
                .andExpect(jsonPath("$.data.projectLinks[0].projectName").value("T69 项目A"))
                .andExpect(jsonPath("$.data.projectLinks[0].customerId").value(customerA.getId().toString()))
                .andExpect(jsonPath("$.data.projectLinks[0].customerName").value("T69 客户A"))
                .andExpect(jsonPath("$.data.projectLinks[0].phaseId").value(phaseA2.getId().toString()))
                .andExpect(jsonPath("$.data.projectLinks[0].phaseName").value("二期"))
                .andExpect(jsonPath("$.data.projectLinks[0].linkType").value("TARGET"));
    }

    @Test
    @DisplayName("GET /assets/{id}/project-links 返回项目与客户摘要")
    void listProjectLinks_endpoint() throws Exception {
        Asset asset = createAssetDirectly("t69-挂靠列表");
        linkDirectly(asset, projectA, phaseA1);
        linkDirectly(asset, projectB, null);

        mockMvc.perform(get("/api/v1/assets/" + asset.getId() + "/project-links")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[*].projectName",
                        org.hamcrest.Matchers.containsInAnyOrder("T69 项目A", "T69 项目B")))
                .andExpect(jsonPath("$.data[*].customerName",
                        org.hamcrest.Matchers.hasItem("T69 客户B")));

        // 资产不存在 → 404
        mockMvc.perform(get("/api/v1/assets/" + UUID.randomUUID() + "/project-links")
                        .header("Authorization", adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /projects/{id}/assets 按期次过滤与分组正确")
    void projectAssets_groupByPhase() throws Exception {
        Asset phaseOne = createAssetDirectly("t69-一期资产");
        linkDirectly(phaseOne, projectA, phaseA1);
        Asset phaseTwo = createAssetDirectly("t69-二期资产");
        linkDirectly(phaseTwo, projectA, phaseA2);

        // 全部：2 条，phaseName 分别为 一期/二期（前端可据此分组）
        mockMvc.perform(get("/api/v1/projects/" + projectA.getId() + "/assets")
                        .param("size", "50")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[*].phaseName",
                        org.hamcrest.Matchers.containsInAnyOrder("一期", "二期")));

        // 只过滤一期：1 条且是 phaseOne
        mockMvc.perform(get("/api/v1/projects/" + projectA.getId() + "/assets")
                        .param("phaseId", phaseA1.getId().toString())
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].assetId").value(phaseOne.getId().toString()))
                .andExpect(jsonPath("$.data.content[0].phaseName").value("一期"))
                .andExpect(jsonPath("$.data.content[0].linkType").value("TARGET"));

        // 项目不存在 → 404
        mockMvc.perform(get("/api/v1/projects/" + UUID.randomUUID() + "/assets")
                        .header("Authorization", adminToken))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ 权限

    @Test
    @DisplayName("挂靠写权：工程师改他人资产 403；自己的资产 201")
    void projectLinkAuthorization_engineer() throws Exception {
        Asset adminAsset = createAssetDirectly("t69-管理员资产");
        linkDirectly(adminAsset, projectA, null);

        mockMvc.perform(post("/api/v1/assets/" + adminAsset.getId() + "/project-links")
                        .header("Authorization", engineerToken)
                        .contentType(Objects.requireNonNull(APPLICATION_JSON))
                        .content("{\"projectId\":\"" + projectB.getId() + "\"}"))
                .andExpect(status().isForbidden());

        // 工程师自己的资产（ownerId=engineerUserId）→ 201
        Asset own = assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name("t69-工程师资产").code("T69E-" + UUID.randomUUID().toString().substring(0, 8))
                .targetType("web").targetSource("internal").description("t69")
                .priority("P3").status("active").ownerId(engineerUserId).teamId(ensureTeam()).build()));
        linkDirectly(own, projectA, null);

        mockMvc.perform(post("/api/v1/assets/" + own.getId() + "/project-links")
                        .header("Authorization", engineerToken)
                        .contentType(Objects.requireNonNull(APPLICATION_JSON))
                        .content("{\"projectId\":\"" + projectB.getId() + "\"}"))
                .andExpect(status().isCreated());
    }
}
