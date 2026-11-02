package com.vulnaudit.controller;

import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.AssetProjectLink;
import com.vulnaudit.entity.Customer;
import com.vulnaudit.entity.Project;
import com.vulnaudit.entity.ProjectPhase;
import com.vulnaudit.entity.Team;
import com.vulnaudit.entity.Vulnerability;
import com.vulnaudit.repository.AssetProjectLinkRepository;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.CustomerRepository;
import com.vulnaudit.repository.ProjectPhaseRepository;
import com.vulnaudit.repository.ProjectRepository;
import com.vulnaudit.repository.TeamRepository;
import com.vulnaudit.repository.VulnerabilityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 客户详情聚合接口测试（t76）
 *
 * <p>验证 {@code GET /customers/{id}/summary}：一次请求拿到 customer / stats / projects / recentAssets。
 * 重点：
 * <ul>
 *   <li>{@code projects[].phaseCount / assetCount} 与库内一致（group by 聚合，非循环查询）；</li>
 *   <li>工程师与管理员看到的 {@code stats.assetCount} / {@code recentAssets} 不同（按 visibleAssetIds 收敛），
 *       而 {@code projects} 列表两人一致（组织维度不收敛）；</li>
 *   <li>空客户返回空数组不报错；客户不存在 404；{@code recentAssets} 上限 10。</li>
 * </ul>
 */
@DisplayName("客户详情聚合接口测试")
class CustomerSummaryTest extends BaseTest {

    @Autowired private CustomerRepository customerRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectPhaseRepository projectPhaseRepository;
    @Autowired private AssetProjectLinkRepository linkRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private VulnerabilityRepository vulnerabilityRepository;
    @Autowired private TeamRepository teamRepository;

    private Team team;
    private Customer customer;
    private Project projectA;
    private Project projectB;
    private Asset adminOnlyAsset;
    private Asset engineerAsset;

    @BeforeEach
    void setUpSummaryData() {
        String suffix = String.valueOf(System.nanoTime());
        team = teamRepository.save(Objects.requireNonNull(Team.builder()
                .name("T76 团队-" + suffix).leaderId(leaderUserId).createdBy(adminUserId).build()));

        customer = customerRepository.save(Objects.requireNonNull(Customer.builder()
                .code("CUST-T76-" + suffix).name("T76 客户").status("active")
                .createdBy(adminUserId).build()));

        projectA = projectRepository.save(Objects.requireNonNull(Project.builder()
                .code("PRJ-T76A-" + suffix).customerId(customer.getId()).name("T76 项目A")
                .type("PENTEST").status("ACTIVE").managerId(adminUserId).isPhased(true)
                .createdBy(adminUserId).build()));
        projectB = projectRepository.save(Objects.requireNonNull(Project.builder()
                .code("PRJ-T76B-" + suffix).customerId(customer.getId()).name("T76 项目B")
                .type("CODE_AUDIT").status("DRAFT").managerId(leaderUserId).isPhased(false)
                .createdBy(adminUserId).build()));

        // 项目A 两个期次，项目B 一个期次
        projectPhaseRepository.save(Objects.requireNonNull(ProjectPhase.builder()
                .projectId(projectA.getId()).phaseNo(1).name("一期").build()));
        projectPhaseRepository.save(Objects.requireNonNull(ProjectPhase.builder()
                .projectId(projectA.getId()).phaseNo(2).name("二期").build()));
        projectPhaseRepository.save(Objects.requireNonNull(ProjectPhase.builder()
                .projectId(projectB.getId()).phaseNo(1).name("B 一期").build()));

        // 管理员负责的资产（工程师不可见）+ 工程师负责的资产（工程师可见）
        adminOnlyAsset = assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name("T76 管理员资产").code("T76A-" + UUID.randomUUID().toString().substring(0, 8))
                .targetType("web").targetSource("external").description("t76")
                .priority("P1").status("active").ownerId(adminUserId).teamId(team.getId()).build()));
        engineerAsset = assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name("T76 工程师资产").code("T76E-" + UUID.randomUUID().toString().substring(0, 8))
                .targetType("api").targetSource("internal").description("t76")
                .priority("P2").status("active").ownerId(engineerUserId).teamId(team.getId()).build()));

        // A 项目：两个资产（一个按期次）；B 项目：一个资产
        link(adminOnlyAsset, projectA, null);
        link(engineerAsset, projectA, projectPhaseRepository
                .findByProjectIdAndPhaseNo(projectA.getId(), 2).orElseThrow());
        link(engineerAsset, projectB, null);

        // 漏洞：仅管理员资产下有 1 个（工程师视角应为 0）
        vulnerabilityRepository.save(Objects.requireNonNull(Vulnerability.builder()
                .assetId(adminOnlyAsset.getId()).title("T76 漏洞").type("XSS").severity("HIGH")
                .description("t76").detail("t76").workflowStatus("discovered").customerStatus("pending")
                .disclosureStatus("undisclosed").exploitStage("探测").fieldTestStatus("待测试")
                .vendorFixed(false).vulnerabilityNo("VUL-T76-" + System.nanoTime())
                .discovererId(adminUserId).build()));
    }

    private void link(Asset asset, Project project, ProjectPhase phase) {
        linkRepository.saveAndFlush(Objects.requireNonNull(AssetProjectLink.builder()
                .assetId(asset.getId()).projectId(project.getId())
                .phaseId(phase != null ? phase.getId() : null)
                .phaseKey(phase != null ? phase.getId() : AssetProjectLink.PHASE_KEY_NONE)
                .customerId(customer.getId()).linkType("TARGET").linkedBy(adminUserId).build()));
    }

    // ------------------------------------------------------------------ 结构

    @Test
    @DisplayName("一次请求返回完整结构：customer / stats / projects / recentAssets")
    void summary_returnsFullStructure() throws Exception {
        mockMvc.perform(get("/api/v1/customers/" + customer.getId() + "/summary")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customer.id").value(customer.getId().toString()))
                .andExpect(jsonPath("$.data.customer.name").value("T76 客户"))
                .andExpect(jsonPath("$.data.stats.projectCount").value(2))
                .andExpect(jsonPath("$.data.stats.activeProjectCount").value(1))
                .andExpect(jsonPath("$.data.stats.phasedProjectCount").value(1))
                .andExpect(jsonPath("$.data.stats.assetCount").value(2))
                .andExpect(jsonPath("$.data.stats.vulnerabilityCount").value(1))
                .andExpect(jsonPath("$.data.projects", hasSize(2)))
                .andExpect(jsonPath("$.data.projects[*].name", hasItem("T76 项目A")))
                .andExpect(jsonPath("$.data.recentAssets", hasSize(2)))
                .andExpect(jsonPath("$.data.recentAssets[*].name", hasItem("T76 工程师资产")));
    }

    @Test
    @DisplayName("projects[].phaseCount / assetCount 与库内一致（group by 聚合）")
    void summary_projectCountsMatchDatabase() throws Exception {
        // 库内口径：项目A 2 期 / 2 资产（去重），项目B 1 期 / 1 资产
        long phaseA = projectPhaseRepository.findByProjectIdOrderByPhaseNo(projectA.getId()).size();
        long phaseB = projectPhaseRepository.findByProjectIdOrderByPhaseNo(projectB.getId()).size();
        long linkA = linkRepository.findByProjectId(projectA.getId()).stream()
                .map(AssetProjectLink::getAssetId).distinct().count();
        long linkB = linkRepository.findByProjectId(projectB.getId()).stream()
                .map(AssetProjectLink::getAssetId).distinct().count();

        String body = mockMvc.perform(get("/api/v1/customers/" + customer.getId() + "/summary")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.JsonNode projects = objectMapper.readTree(body).path("data").path("projects");
        assertEquals(2, projects.size());

        for (com.fasterxml.jackson.databind.JsonNode node : projects) {
            UUID projectId = UUID.fromString(node.path("id").asText());
            long expectedPhase = projectId.equals(projectA.getId()) ? phaseA : phaseB;
            long expectedAssets = projectId.equals(projectA.getId()) ? linkA : linkB;
            assertEquals(expectedPhase, node.path("phaseCount").asLong(),
                    "phaseCount 应与库内一致: " + node.path("name").asText());
            assertEquals(expectedAssets, node.path("assetCount").asLong(),
                    "assetCount 应与库内一致: " + node.path("name").asText());
        }
        // 经理名解析：项目B 的经理是 team_leader
        // managerName 优先取 fullName（BaseTest 的组长 fullName=「组长」）
        assertTrue(body.contains("组长"), "projects[].managerName 应有值");
    }

    // ------------------------------------------------------------------ 可见性边界

    @Test
    @DisplayName("工程师视角：recentAssets 只含可见资产，stats.assetCount 小于 admin；projects 列表不变")
    void summary_engineerSeesOnlyVisibleAssets() throws Exception {
        String adminBody = mockMvc.perform(get("/api/v1/customers/" + customer.getId() + "/summary")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String engineerBody = mockMvc.perform(get("/api/v1/customers/" + customer.getId() + "/summary")
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.JsonNode adminData = objectMapper.readTree(adminBody).path("data");
        com.fasterxml.jackson.databind.JsonNode engineerData = objectMapper.readTree(engineerBody).path("data");

        // 管理员：2 个资产、1 个漏洞；工程师：1 个资产、0 个漏洞
        assertEquals(2, adminData.path("stats").path("assetCount").asLong());
        assertEquals(1, engineerData.path("stats").path("assetCount").asLong(),
                "工程师 assetCount 必须按可见范围收敛");
        assertEquals(1, adminData.path("stats").path("vulnerabilityCount").asLong());
        assertEquals(0, engineerData.path("stats").path("vulnerabilityCount").asLong(),
                "不可见资产下的漏洞不应计入");

        assertEquals(1, engineerData.path("recentAssets").size(), "工程师只看到自己可见的那一个资产");
        assertEquals(engineerAsset.getId().toString(),
                engineerData.path("recentAssets").get(0).path("id").asText());
        assertTrue(engineerBody.contains("T76 工程师资产"));
        assertTrue(!engineerBody.contains("T76 管理员资产"), "不可见资产不应出现在 recentAssets");

        // 项目列表与统计不受可见性影响（组织维度）
        assertEquals(2, engineerData.path("projects").size());
        assertEquals(2, engineerData.path("stats").path("projectCount").asLong());
    }

    @Test
    @DisplayName("空客户：无项目无资产 → 空数组不报错")
    void summary_emptyCustomer() throws Exception {
        Customer empty = customerRepository.save(Objects.requireNonNull(Customer.builder()
                .code("CUST-T76-EMPTY-" + System.nanoTime()).name("T76 空客户").status("active")
                .createdBy(adminUserId).build()));

        mockMvc.perform(get("/api/v1/customers/" + empty.getId() + "/summary")
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stats.projectCount").value(0))
                .andExpect(jsonPath("$.data.stats.assetCount").value(0))
                .andExpect(jsonPath("$.data.stats.vulnerabilityCount").value(0))
                .andExpect(jsonPath("$.data.projects", hasSize(0)))
                .andExpect(jsonPath("$.data.recentAssets", hasSize(0)));
    }

    @Test
    @DisplayName("客户不存在 → 404")
    void summary_notFound() throws Exception {
        mockMvc.perform(get("/api/v1/customers/" + UUID.randomUUID() + "/summary")
                        .header("Authorization", adminToken))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ recentAssets 细节

    @Test
    @DisplayName("recentAssets 最多 10 条，且 projectNames 只含本客户下的项目")
    void summary_recentAssetsLimitAndProjectNames() throws Exception {
        Customer big = customerRepository.save(Objects.requireNonNull(Customer.builder()
                .code("CUST-T76-BIG-" + System.nanoTime()).name("T76 大客户").status("active")
                .createdBy(adminUserId).build()));
        Project bigProject = projectRepository.save(Objects.requireNonNull(Project.builder()
                .code("PRJ-T76-BIG-" + System.nanoTime()).customerId(big.getId()).name("T76 大项目")
                .type("PENTEST").status("ACTIVE").managerId(adminUserId).isPhased(false).build()));

        for (int i = 0; i < 12; i++) {
            Asset asset = assetRepository.save(Objects.requireNonNull(Asset.builder()
                    .name("T76 批量资产-" + i).code("T76B-" + i + "-" + UUID.randomUUID().toString().substring(0, 6))
                    .targetType("web").targetSource("external").description("t76")
                    .priority("P3").status("active").ownerId(adminUserId).teamId(team.getId()).build()));
            linkRepository.saveAndFlush(Objects.requireNonNull(AssetProjectLink.builder()
                    .assetId(asset.getId()).projectId(bigProject.getId()).phaseId(null)
                    .phaseKey(AssetProjectLink.PHASE_KEY_NONE).customerId(big.getId())
                    .linkType("TARGET").linkedBy(adminUserId).build()));
        }

        String body = mockMvc.perform(get("/api/v1/customers/" + big.getId() + "/summary")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stats.assetCount").value(12))
                .andExpect(jsonPath("$.data.recentAssets", hasSize(10)))
                .andReturn().getResponse().getContentAsString();

        com.fasterxml.jackson.databind.JsonNode recentAssets =
                objectMapper.readTree(body).path("data").path("recentAssets");
        for (com.fasterxml.jackson.databind.JsonNode asset : recentAssets) {
            assertEquals(1, asset.path("projectNames").size(), "每个资产只挂本项目");
            assertEquals("T76 大项目", asset.path("projectNames").get(0).asText());
        }
    }

    @Test
    @DisplayName("读权限开放：团队长也能读（与 /customers/{id} 一致），无需管理角色")
    void summary_readableByTeamLeader() throws Exception {
        mockMvc.perform(get("/api/v1/customers/" + customer.getId() + "/summary")
                        .header("Authorization", leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customer.id").value(customer.getId().toString()))
                .andExpect(jsonPath("$.data.projects[*].managerName", everyItem(not(org.hamcrest.Matchers.empty()))));
    }

    @Test
    @DisplayName("stats.phasedProjectCount 只数 isPhased=true 的项目")
    void summary_phasedProjectCount() throws Exception {
        mockMvc.perform(get("/api/v1/customers/" + customer.getId() + "/summary")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                // setUp 里 projectA 是分期项目、projectB 不是 → 只数 1
                .andExpect(jsonPath("$.data.stats.phasedProjectCount").value(1))
                .andExpect(jsonPath("$.data.projects[?(@.isPhased == true)]", hasSize(1)));
    }
}
