package com.vulnaudit.controller;

import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.AssetProjectLink;
import com.vulnaudit.entity.Customer;
import com.vulnaudit.entity.Project;
import com.vulnaudit.entity.ProjectPhase;
import com.vulnaudit.entity.Task;
import com.vulnaudit.entity.Vulnerability;
import com.vulnaudit.repository.AssetProjectLinkRepository;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.CustomerRepository;
import com.vulnaudit.repository.ProjectPhaseRepository;
import com.vulnaudit.repository.ProjectRepository;
import com.vulnaudit.repository.TaskRepository;
import com.vulnaudit.repository.VulnerabilityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 客户/项目统计与仪表盘分布 + 挂靠越权复核（t70）。
 *
 * <p>核心口径（README §36 / 验收报告 §2.10）：
 * <ul>
 *   <li>项目/期次类计数 = 组织结构事实，<b>不随可见性变化</b>；</li>
 *   <li>资产/漏洞/任务类计数与 byPhase 分布 = 按 {@code visibleAssetIds} <b>收敛</b>。</li>
 * </ul>
 */
@DisplayName("客户/项目统计与分布测试")
public class CustomerProjectStatsTest extends BaseTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectPhaseRepository projectPhaseRepository;

    @Autowired
    private AssetProjectLinkRepository linkRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private VulnerabilityRepository vulnerabilityRepository;

    @Autowired
    private TaskRepository taskRepository;

    private Customer customer;
    private Project phasedProject;
    private Project flatProject;
    private ProjectPhase phase1;
    private ProjectPhase phase2;
    private Asset engineerAsset;
    private Asset leaderAsset;
    private Asset adminAsset;

    @BeforeEach
    void setUpStatsData() {
        String suffix = String.valueOf(System.currentTimeMillis());

        customer = customerRepository.save(Objects.requireNonNull(Customer.builder()
                .code("STAT-CUST-" + suffix)
                .name("统计客户_" + suffix)
                .status("active")
                .createdBy(adminUserId)
                .build()));

        phasedProject = projectRepository.save(Objects.requireNonNull(Project.builder()
                .code("STAT-P1-" + suffix)
                .customerId(customer.getId())
                .name("分期项目")
                .type("PENTEST")
                .status("ACTIVE")
                .managerId(adminUserId)
                .isPhased(true)
                .createdBy(adminUserId)
                .build()));

        flatProject = projectRepository.save(Objects.requireNonNull(Project.builder()
                .code("STAT-P2-" + suffix)
                .customerId(customer.getId())
                .name("不分期项目")
                .type("PENTEST")
                .status("DRAFT")
                .managerId(adminUserId)
                .isPhased(false)
                .createdBy(adminUserId)
                .build()));

        phase1 = projectPhaseRepository.save(Objects.requireNonNull(ProjectPhase.builder()
                .projectId(phasedProject.getId()).phaseNo(1).name("一期").status("PLANNED").build()));
        phase2 = projectPhaseRepository.save(Objects.requireNonNull(ProjectPhase.builder()
                .projectId(phasedProject.getId()).phaseNo(2).name("二期").status("PLANNED").build()));

        engineerAsset = saveAsset("STAT-A-ENG-" + suffix, "工程师资产", engineerUserId);
        leaderAsset = saveAsset("STAT-A-LEAD-" + suffix, "组长资产", leaderUserId);
        adminAsset = saveAsset("STAT-A-ADM-" + suffix, "管理员资产", adminUserId);

        // 分期项目：3 个资产（一期 2 个、二期 1 个）；不分期项目：工程师资产 1 个（跨项目挂靠）
        link(engineerAsset, phasedProject, phase1);
        link(leaderAsset, phasedProject, phase1);
        link(adminAsset, phasedProject, phase2);
        link(engineerAsset, flatProject, null);

        saveVulnerability(engineerAsset, "工程漏洞");
        saveVulnerability(adminAsset, "管理漏洞");
        saveTask(engineerAsset, "工程任务");
        saveTask(adminAsset, "管理任务");
    }

    private Asset saveAsset(String code, String name, UUID ownerId) {
        return assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name(name)
                .code(code)
                .targetType("Web应用")
                .targetSource("internal")
                .priority("P1")
                .status("active")
                .ownerId(ownerId)
                .build()));
    }

    private void link(Asset asset, Project project, ProjectPhase phase) {
        linkRepository.save(Objects.requireNonNull(AssetProjectLink.builder()
                .assetId(asset.getId())
                .projectId(project.getId())
                .phaseId(phase == null ? null : phase.getId())
                .phaseKey(phase == null ? null : phase.getId())
                .customerId(customer.getId())
                .linkType("TARGET")
                .linkedBy(adminUserId)
                .build()));
    }

    private void saveVulnerability(Asset asset, String title) {
        vulnerabilityRepository.save(Objects.requireNonNull(Vulnerability.builder()
                .assetId(asset.getId())
                .title(title)
                .type("SQL注入")
                .severity("HIGH")
                .description("统计用例")
                .detail("统计用例")
                .workflowStatus("discovered")
                .customerStatus("pending")
                .disclosureStatus("undisclosed")
                .discovererId(adminUserId)
                .build()));
    }

    private void saveTask(Asset asset, String title) {
        taskRepository.save(Objects.requireNonNull(Task.builder()
                .title(title)
                .assetId(asset.getId())
                .assignedTo(adminUserId)
                .assignedBy(adminUserId)
                .status("pending")
                .build()));
    }

    // ------------------------------------------------------------------ 统计数值

    @Test
    @DisplayName("客户统计（管理员）：项目/资产/漏洞计数与库内一致")
    void customerStats_matchesDatabaseForAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/customers/" + customer.getId() + "/stats")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectCount").value(2))
                .andExpect(jsonPath("$.data.activeProjectCount").value(1))
                .andExpect(jsonPath("$.data.phasedProjectCount").value(1))
                // 挂靠记录 4 条，去重资产 3 个
                .andExpect(jsonPath("$.data.assetCount").value(4))
                .andExpect(jsonPath("$.data.distinctAssetCount").value(3))
                .andExpect(jsonPath("$.data.vulnerabilityCount").value(2))
                .andExpect(jsonPath("$.data.byStatus.ACTIVE").value(1))
                .andExpect(jsonPath("$.data.byStatus.DRAFT").value(1));
    }

    @Test
    @DisplayName("客户统计（工程师）：项目计数不变，资产/漏洞计数按可见范围收敛")
    void customerStats_scopedForEngineer() throws Exception {
        // 工程师只可见自己的 1 个资产（其 1 条漏洞）；项目类计数仍为 2
        mockMvc.perform(get("/api/v1/customers/" + customer.getId() + "/stats")
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectCount").value(2))
                .andExpect(jsonPath("$.data.activeProjectCount").value(1))
                .andExpect(jsonPath("$.data.distinctAssetCount").value(1))
                .andExpect(jsonPath("$.data.vulnerabilityCount").value(1))
                // 记录数按可见资产过滤：工程师资产挂靠 2 条（分期 + 不分期）
                .andExpect(jsonPath("$.data.assetCount").value(2));
    }

    @Test
    @DisplayName("项目统计（管理员）：phaseCount/assetCount/vuln/task 与 byPhase 分布一致")
    void projectStats_matchesDatabaseForAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/projects/" + phasedProject.getId() + "/stats")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phaseCount").value(2))
                .andExpect(jsonPath("$.data.assetCount").value(3))
                .andExpect(jsonPath("$.data.vulnerabilityCount").value(2))
                .andExpect(jsonPath("$.data.taskCount").value(2))
                .andExpect(jsonPath("$.data.byPhase", hasSize(2)))
                .andExpect(jsonPath("$.data.byPhase[0].phaseId").value(phase1.getId().toString()))
                .andExpect(jsonPath("$.data.byPhase[0].assetCount").value(2))
                .andExpect(jsonPath("$.data.byPhase[1].assetCount").value(1));
    }

    @Test
    @DisplayName("项目统计（工程师）：期次数不变，资产/漏洞/任务与分期分布收敛")
    void projectStats_scopedForEngineer() throws Exception {
        mockMvc.perform(get("/api/v1/projects/" + phasedProject.getId() + "/stats")
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.phaseCount").value(2))
                .andExpect(jsonPath("$.data.assetCount").value(1))
                .andExpect(jsonPath("$.data.vulnerabilityCount").value(1))
                .andExpect(jsonPath("$.data.taskCount").value(1))
                .andExpect(jsonPath("$.data.byPhase[0].assetCount").value(1))
                .andExpect(jsonPath("$.data.byPhase[1].assetCount").value(0));
    }

    // ------------------------------------------------------------------ 越权复核

    @Test
    @DisplayName("越权复核①：工程师给他人资产挂靠 → 403（canWriteAsset）")
    void linkForeignAsset_forbidden() throws Exception {
        long before = linkRepository.findByAssetId(adminAsset.getId()).size();

        mockMvc.perform(post("/api/v1/assets/" + adminAsset.getId() + "/project-links")
                        .header("Authorization", engineerToken)
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"projectId\":\"" + flatProject.getId() + "\"}"))
                .andExpect(status().isForbidden());

        org.junit.jupiter.api.Assertions.assertEquals(before,
                linkRepository.findByAssetId(adminAsset.getId()).size(),
                "越权挂靠不应写入");
    }

    @Test
    @DisplayName("越权复核②：GET /projects/{id}/assets 对工程师只返回可见资产")
    void projectAssets_scopedForEngineer() throws Exception {
        mockMvc.perform(get("/api/v1/projects/" + phasedProject.getId() + "/assets")
                        .header("Authorization", adminToken)
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(3)));

        mockMvc.perform(get("/api/v1/projects/" + phasedProject.getId() + "/assets")
                        .header("Authorization", engineerToken)
                        .param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].assetCode").value(engineerAsset.getCode()));
    }

    // ------------------------------------------------------------------ 仪表盘分布

    @Test
    @DisplayName("客户分布：Top N 生效且按资产数倒序；工程师视角资产数收敛")
    void customerDistribution_topNAndScoping() throws Exception {
        // 再造一个客户，保证 Top1 一定是本用例客户（它挂靠了 3 个资产）
        customerRepository.save(Objects.requireNonNull(Customer.builder()
                .code("STAT-CUST-EMPTY-" + System.currentTimeMillis())
                .name("空客户")
                .status("active")
                .createdBy(adminUserId)
                .build()));

        mockMvc.perform(get("/api/v1/dashboard/customer-distribution")
                        .header("Authorization", adminToken).param("topN", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].customerId").value(customer.getId().toString()))
                .andExpect(jsonPath("$.data[0].assetCount").value(3))
                .andExpect(jsonPath("$.data[0].projectCount").value(2));

        mockMvc.perform(get("/api/v1/dashboard/customer-distribution")
                        .header("Authorization", engineerToken).param("topN", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.customerId=='" + customer.getId() + "')].assetCount")
                        .value(org.hamcrest.Matchers.contains(1)));
    }

    @Test
    @DisplayName("项目分布：customerId 过滤生效，工程师视角资产数收敛")
    void projectDistribution_customerFilterAndScoping() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/project-distribution")
                        .header("Authorization", adminToken)
                        .param("customerId", customer.getId().toString())
                        .param("topN", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].projectId").value(phasedProject.getId().toString()))
                .andExpect(jsonPath("$.data[0].assetCount").value(3));

        mockMvc.perform(get("/api/v1/dashboard/project-distribution")
                        .header("Authorization", engineerToken)
                        .param("customerId", customer.getId().toString())
                        .param("topN", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].assetCount").value(1));

        // 其他客户的过滤结果为空
        mockMvc.perform(get("/api/v1/dashboard/project-distribution")
                        .header("Authorization", adminToken)
                        .param("customerId", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    // ------------------------------------------------------------------ 删除一致性

    @Test
    @DisplayName("删除一致性：有挂靠不可删项目/客户；解绑后可删且无残留挂靠")
    void deleteConsistency() throws Exception {
        // 客户下有项目 → 409
        mockMvc.perform(delete("/api/v1/customers/" + customer.getId()).header("Authorization", adminToken))
                .andExpect(status().isConflict());

        // 项目下有挂靠 → 409
        mockMvc.perform(delete("/api/v1/projects/" + flatProject.getId()).header("Authorization", adminToken))
                .andExpect(status().isConflict());

        // 解绑 flatProject 的唯一挂靠后删除项目 → 200，且该项目的挂靠记录为空
        AssetProjectLink link = linkRepository.findByProjectId(flatProject.getId()).get(0);
        mockMvc.perform(delete("/api/v1/assets/" + link.getAssetId() + "/project-links/" + link.getId())
                        .header("Authorization", adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/projects/" + flatProject.getId()).header("Authorization", adminToken))
                .andExpect(status().isOk());

        org.junit.jupiter.api.Assertions.assertTrue(
                linkRepository.findByProjectId(flatProject.getId()).isEmpty(),
                "删除项目后不应有挂靠残留");
    }

    @Test
    @DisplayName("统计对不存在的客户/项目 → 404")
    void statsForMissingResources() throws Exception {
        mockMvc.perform(get("/api/v1/customers/" + UUID.randomUUID() + "/stats")
                        .header("Authorization", adminToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/projects/" + UUID.randomUUID() + "/stats")
                        .header("Authorization", adminToken))
                .andExpect(status().isNotFound());
    }
}
