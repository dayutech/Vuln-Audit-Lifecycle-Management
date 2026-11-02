package com.vulnaudit.controller;

import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.AssetProjectLink;
import com.vulnaudit.entity.Customer;
import com.vulnaudit.entity.Project;
import com.vulnaudit.entity.ProjectPhase;
import com.vulnaudit.entity.Team;
import com.vulnaudit.repository.AssetProjectLinkRepository;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.CustomerRepository;
import com.vulnaudit.repository.ProjectPhaseRepository;
import com.vulnaudit.repository.ProjectRepository;
import com.vulnaudit.repository.TeamRepository;
import com.vulnaudit.service.ImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CSV 导入资产必须挂项目（t78）
 *
 * <p>背景：t69 的「建资产必须挂项目」只加在 DTO 层，批量导入走 service 层绕过了它 ——
 * 导入出来的资产没有任何 {@code AssetProjectLink}，破坏「资产必须至少挂靠一个项目」的不变式，
 * 也会让 t75 的「无挂靠资产数 = 0」断言失败。
 *
 * <p>本用例验证：
 * <ol>
 *   <li>带 projectCode → 挂到该项目；<b>客户与项目不匹配</b> / 项目不存在 / 期次不存在 → 该行 failedRows 且不落库；</li>
 *   <li>未带 projectCode → 挂到 CUST-DEFAULT 的 PRJ-HISTORY（linkType=SHARED），
 *       并在 {@code result.notes()} 里给出提示（成功行，不得静默）；</li>
 *   <li>导入完成后「无挂靠资产」计数为 0（含失败行不留残留）。</li>
 * </ol>
 */
@DisplayName("CSV 导入资产挂项目测试")
class ImportAssetProjectTest extends BaseTest {

    @Autowired private ImportService importService;
    @Autowired private AssetRepository assetRepository;
    @Autowired private AssetProjectLinkRepository linkRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectPhaseRepository projectPhaseRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Team team;
    private Customer customerA;
    private Customer customerB;
    private Project projectA;          // customerA，分期，有 1 期
    private Project projectB;          // customerB，不分期
    private Project historyProject;    // PRJ-HISTORY（默认客户下）

    @BeforeEach
    void setUpImportProjectData() {
        String suffix = String.valueOf(System.nanoTime());
        team = teamRepository.save(Objects.requireNonNull(Team.builder()
                .name("T78 导入团队-" + suffix).leaderId(adminUserId).createdBy(adminUserId).build()));

        customerA = customerRepository.save(Objects.requireNonNull(Customer.builder()
                .code("CUST-T78A-" + suffix).name("T78 客户A").status("active")
                .createdBy(adminUserId).build()));
        customerB = customerRepository.save(Objects.requireNonNull(Customer.builder()
                .code("CUST-T78B-" + suffix).name("T78 客户B").status("active")
                .createdBy(adminUserId).build()));

        projectA = projectRepository.save(Objects.requireNonNull(Project.builder()
                .code("PRJ-T78A-" + suffix).customerId(customerA.getId()).name("T78 项目A")
                .type("PENTEST").status("ACTIVE").managerId(adminUserId).isPhased(true)
                .createdBy(adminUserId).build()));
        projectB = projectRepository.save(Objects.requireNonNull(Project.builder()
                .code("PRJ-T78B-" + suffix).customerId(customerB.getId()).name("T78 项目B")
                .type("CODE_AUDIT").status("ACTIVE").managerId(adminUserId).isPhased(false)
                .createdBy(adminUserId).build()));
        projectPhaseRepository.save(Objects.requireNonNull(ProjectPhase.builder()
                .projectId(projectA.getId()).phaseNo(1).name("一期").build()));

        // 模拟 V18 迁移的默认客户 + 历史项目（测试库 ddl-auto=create-drop，迁移不执行）
        Customer defaultCustomer = customerRepository.findByCode("CUST-DEFAULT").orElseGet(() ->
                customerRepository.save(Objects.requireNonNull(Customer.builder()
                        .code("CUST-DEFAULT").name("历史数据").status("active")
                        .createdBy(adminUserId).build())));
        historyProject = projectRepository.findByCode("PRJ-HISTORY").orElseGet(() ->
                projectRepository.save(Objects.requireNonNull(Project.builder()
                        .code("PRJ-HISTORY").customerId(defaultCustomer.getId()).name("历史项目")
                        .type("OTHER").status("ACTIVE").managerId(adminUserId).isPhased(false)
                        .createdBy(adminUserId).build())));
    }

    // ------------------------------------------------------------------ 工具方法

    /** 构造 CSV：表头与模板一致（新增 customerCode / projectCode / phaseNo） */
    private byte[] csv(String... rows) {
        StringBuilder sb = new StringBuilder(
                "name,targetType,targetSource,priority,status,teamCode,customerCode,projectCode,phaseNo,description\n");
        for (String row : rows) {
            sb.append(row).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String row(String name, String customerCode, String projectCode, String phaseNo) {
        return name + ",Web应用,客户委托,P2,active," + team.getName() + ","
                + (customerCode == null ? "" : customerCode) + ","
                + (projectCode == null ? "" : projectCode) + ","
                + (phaseNo == null ? "" : phaseNo) + ",t78";
    }

    private Optional<Asset> findAssetByName(String name) {
        return assetRepository.findAll().stream().filter(a -> name.equals(a.getName())).findFirst();
    }

    /** 无挂靠资产计数（t75 会用的同一条不变式断言） */
    private int assetsWithoutLink() {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from assets a where not exists "
                        + "(select 1 from asset_project_links l where l.asset_id = a.id)", Integer.class);
        return count == null ? 0 : count;
    }

    // ------------------------------------------------------------------ 用例

    @Test
    @DisplayName("带 projectCode + customerCode + phaseNo → 成功且挂靠正确")
    void importWithProjectCode_linksCorrectly() {
        var result = importService.importAssets(csv(
                row("t78-带项目", customerA.getCode(), projectA.getCode(), "1")),
                "t78.csv", adminUserId, null);

        assertEquals(1, result.totalRows());
        assertEquals(1, result.successRows(), "应导入成功：" + result.failedRows());
        assertEquals(0, result.failedRows().size());
        assertEquals(0, result.notes().size(), "显式指定项目时不该有「已挂历史项目」提示");

        Asset asset = findAssetByName("t78-带项目").orElseThrow();
        List<AssetProjectLink> links = linkRepository.findByAssetId(asset.getId());
        assertEquals(1, links.size());
        assertEquals(projectA.getId(), links.get(0).getProjectId());
        assertEquals(customerA.getId(), links.get(0).getCustomerId());
        assertNotNull(links.get(0).getPhaseId(), "应挂到一期");
        assertEquals("TARGET", links.get(0).getLinkType());
    }

    @Test
    @DisplayName("projectCode 不存在 → failedRows「项目不存在」且不落库")
    void importWithUnknownProject_fails() {
        var result = importService.importAssets(csv(
                row("t78-项目不存在", customerA.getCode(), "PRJ-NOT-EXIST-XYZ", null)),
                "t78.csv", adminUserId, null);

        assertEquals(0, result.successRows());
        assertEquals(1, result.failedRows().size());
        assertTrue(result.failedRows().get(0).reason().contains("项目不存在"),
                "原因应含「项目不存在」：" + result.failedRows().get(0).reason());
        assertTrue(findAssetByName("t78-项目不存在").isEmpty(), "失败行不得留下资产");
        assertEquals(0, assetsWithoutLink());
    }

    @Test
    @DisplayName("customerCode 与项目不匹配 → failedRows「客户与项目不匹配」且不落库")
    void importWithMismatchedCustomer_fails() {
        // projectA 属于 customerA，却声明 customerB
        var result = importService.importAssets(csv(
                row("t78-客户错配", customerB.getCode(), projectA.getCode(), null)),
                "t78.csv", adminUserId, null);

        assertEquals(0, result.successRows());
        assertEquals(1, result.failedRows().size());
        assertTrue(result.failedRows().get(0).reason().contains("客户与项目不匹配"),
                "原因应含「客户与项目不匹配」：" + result.failedRows().get(0).reason());
        assertTrue(findAssetByName("t78-客户错配").isEmpty());
        assertEquals(0, assetsWithoutLink());
    }

    @Test
    @DisplayName("未带 projectCode → 成功并挂到 PRJ-HISTORY，且 notes 有提示（不得静默）")
    void importWithoutProject_fallsBackToHistoryWithNote() {
        var result = importService.importAssets(csv(
                row("t78-未指定项目", null, null, null)),
                "t78.csv", adminUserId, null);

        assertEquals(1, result.successRows(), "未指定项目属成功行：" + result.failedRows());
        assertEquals(0, result.failedRows().size());
        assertEquals(1, result.notes().size(), "必须给出「已挂靠历史项目」提示");
        assertTrue(result.notes().get(0).message().contains("历史项目"));

        Asset asset = findAssetByName("t78-未指定项目").orElseThrow();
        List<AssetProjectLink> links = linkRepository.findByAssetId(asset.getId());
        assertEquals(1, links.size());
        assertEquals(historyProject.getId(), links.get(0).getProjectId());
        assertEquals("SHARED", links.get(0).getLinkType(), "兜底挂靠用 SHARED");
        assertNull(links.get(0).getPhaseId());
        assertEquals(0, assetsWithoutLink());
    }

    @Test
    @DisplayName("带 phaseNo 但期次不存在 → failedRows；项目未分期却给 phaseNo → failedRows")
    void importWithInvalidPhase_fails() {
        var missingPhase = importService.importAssets(csv(
                row("t78-期次不存在", customerA.getCode(), projectA.getCode(), "9")),
                "t78.csv", adminUserId, null);
        assertEquals(0, missingPhase.successRows());
        assertTrue(missingPhase.failedRows().get(0).reason().contains("期次不存在"),
                "原因应含「期次不存在」：" + missingPhase.failedRows().get(0).reason());

        var notPhased = importService.importAssets(csv(
                row("t78-未分期带期次", customerB.getCode(), projectB.getCode(), "1")),
                "t78.csv", adminUserId, null);
        assertEquals(0, notPhased.successRows());
        assertTrue(notPhased.failedRows().get(0).reason().contains("未启用分期"),
                "原因应含「未启用分期」：" + notPhased.failedRows().get(0).reason());

        assertTrue(findAssetByName("t78-期次不存在").isEmpty());
        assertTrue(findAssetByName("t78-未分期带期次").isEmpty());
        assertEquals(0, assetsWithoutLink());
    }

    @Test
    @DisplayName("混合批次：成功行全部有挂靠，失败行零残留（全库无挂靠资产 = 0）")
    void importMixedBatch_neverLeavesAssetWithoutLink() {
        var result = importService.importAssets(csv(
                row("t78-成功A", customerA.getCode(), projectA.getCode(), "1"),
                row("t78-失败-项目不存在", null, "PRJ-NOPE", null),
                row("t78-成功-历史", null, null, null),
                row("t78-成功B", customerB.getCode(), projectB.getCode(), null)),
                "t78.csv", adminUserId, null);

        assertEquals(4, result.totalRows());
        assertEquals(3, result.successRows(), "3 成功 1 失败：" + result.failedRows());
        assertEquals(1, result.failedRows().size());
        assertEquals(1, result.notes().size(), "只有「未指定项目」那一行有提示");

        assertEquals(0, assetsWithoutLink(), "导入后不得存在无挂靠资产");
        for (String name : List.of("t78-成功A", "t78-成功-历史", "t78-成功B")) {
            Asset asset = findAssetByName(name).orElseThrow();
            assertEquals(1, linkRepository.findByAssetId(asset.getId()).size(), name + " 应有挂靠");
        }
    }

    @Test
    @DisplayName("只给 phaseNo 不给 projectCode → failedRows（不猜项目）")
    void importWithPhaseButNoProject_fails() {
        var result = importService.importAssets(csv(
                row("t78-只有期次", customerA.getCode(), null, "1")),
                "t78.csv", adminUserId, null);

        assertEquals(0, result.successRows());
        assertTrue(result.failedRows().get(0).reason().contains("必须同时提供 projectCode"),
                "原因应提示需要 projectCode：" + result.failedRows().get(0).reason());
        assertTrue(findAssetByName("t78-只有期次").isEmpty());
        assertEquals(0, assetsWithoutLink());
    }

    @Test
    @DisplayName("导入模板已含 customerCode / projectCode / phaseNo（且带 BOM 由控制器负责）")
    void assetTemplate_containsProjectColumns() {
        String template = importService.assetTemplate();
        assertTrue(template.startsWith("name,targetType,targetSource"), template);
        assertTrue(template.contains("customerCode"), template);
        assertTrue(template.contains("projectCode"), template);
        assertTrue(template.contains("phaseNo"), template);
    }

    @Test
    @DisplayName("历史项目缺失时按需自动补齐（幂等），仍然满足「无挂靠资产 = 0」")
    void importWithoutProject_whenHistoryMissing_selfHeals() {
        // 删掉历史项目与默认客户，模拟「迁移未执行 / 被误删」的环境
        UUID historyId = historyProject.getId();
        UUID defaultCustomerId = historyProject.getCustomerId();
        projectRepository.deleteById(historyId);
        customerRepository.deleteById(defaultCustomerId);
        assertTrue(projectRepository.findByCode("PRJ-HISTORY").isEmpty());

        var result = importService.importAssets(csv(
                row("t78-无历史项目", null, null, null)),
                "t78.csv", adminUserId, null);

        assertEquals(1, result.successRows(), "缺失时按需补齐：不阻断导入：" + result.failedRows());
        assertEquals(1, result.notes().size(), "仍应提示「已挂靠历史项目」");
        assertEquals(0, assetsWithoutLink());

        // 补齐后的客户/项目 code 与迁移一致，且只补一次
        Customer recreatedCustomer = customerRepository.findByCode("CUST-DEFAULT").orElseThrow();
        Project recreatedProject = projectRepository.findByCode("PRJ-HISTORY").orElseThrow();
        assertEquals(recreatedCustomer.getId(), recreatedProject.getCustomerId());
        long historyLinks = linkRepository.findByProjectId(recreatedProject.getId()).size();
        assertEquals(1, historyLinks, "本次导入只挂了一条");

        // 再导一次：复用同一个历史项目，不重复创建
        importService.importAssets(csv(row("t78-无历史项目2", null, null, null)),
                "t78.csv", adminUserId, null);
        assertEquals(1, customerRepository.findByCode("CUST-DEFAULT").stream().count());
        assertEquals(1, projectRepository.findByCode("PRJ-HISTORY").stream().count());
        assertEquals(2, linkRepository.findByProjectId(recreatedProject.getId()).size());
    }

    @Test
    @DisplayName("导入的资产能在 /assets/{id}/project-links 与客户筛选中查到（端到端连通）")
    void importedAsset_visibleViaApi() throws Exception {
        var result = importService.importAssets(csv(
                row("t78-接口校验", customerA.getCode(), projectA.getCode(), null)),
                "t78.csv", adminUserId, null);
        assertEquals(1, result.successRows(), result.failedRows().toString());

        Asset asset = findAssetByName("t78-接口校验").orElseThrow();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/assets/" + asset.getId() + "/project-links")
                        .header("Authorization", adminToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data[0].projectId").value(projectA.getId().toString()));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/assets")
                        .param("customerId", customerA.getId().toString())
                        .param("size", "50")
                        .header("Authorization", adminToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.data.content[*].id",
                                org.hamcrest.Matchers.hasItem(asset.getId().toString())));
    }
}
