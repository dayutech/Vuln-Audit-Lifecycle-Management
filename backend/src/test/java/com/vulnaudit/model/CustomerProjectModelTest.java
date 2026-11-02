package com.vulnaudit.model;

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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 客户 / 项目 / 期次 / 资产挂靠 领域模型测试（t67）
 *
 * <p>覆盖三件事：
 * <ol>
 *   <li><b>迁移效果</b>：把 {@code db/migration/V18__customer_project_phase_and_asset_links.sql}
 *       在测试库上原样执行（测试环境 Flyway 关闭，故这里手工跑真实脚本，而不是重写一份 SQL）；</li>
 *   <li><b>回填正确性</b>：默认客户 CUST-DEFAULT、历史项目 PRJ-HISTORY 存在，存量资产全部有挂靠；</li>
 *   <li><b>幂等性</b>：脚本重复执行不产生重复行；并与唯一索引一起构成双保险。</li>
 * </ol>
 *
 * <p>另含仓储查询与 NULL 安全唯一约束（phase_key 方案）的断言。
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("客户/项目/期次/挂靠 领域模型测试")
class CustomerProjectModelTest {

    private static final String MIGRATION =
            "db/migration/V18__customer_project_phase_and_asset_links.sql";

    @Autowired private DataSource dataSource;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AssetRepository assetRepository;
    @Autowired private CustomerRepository customerRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectPhaseRepository projectPhaseRepository;
    @Autowired private AssetProjectLinkRepository assetProjectLinkRepository;

    @BeforeEach
    void cleanModelTables() {
        wipeModelData();
    }

    /**
     * 迁移用例不���事务（脚本用独立连接执行，必须有已提交数据才看得见），
     * 因此在每个用例前后显式清库，避免残留污染其它 suite。
     */
    @AfterEach
    void cleanUpAfterTest() {
        wipeModelData();
    }

    private void wipeModelData() {
        jdbcTemplate.update("DELETE FROM asset_project_links");
        jdbcTemplate.update("DELETE FROM project_phases");
        jdbcTemplate.update("DELETE FROM projects");
        jdbcTemplate.update("DELETE FROM customers");
        jdbcTemplate.update("DELETE FROM assets WHERE code LIKE 'T67-%'");
    }

    private void runMigration() {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator(
                new ClassPathResource(MIGRATION));
        populator.setSqlScriptEncoding(StandardCharsets.UTF_8.name());
        populator.setContinueOnError(false);
        populator.execute(Objects.requireNonNull(dataSource));
    }

    private Asset newAsset(String suffix) {
        return assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name("t67 资产-" + suffix)
                .code("T67-" + suffix + "-" + UUID.randomUUID().toString().substring(0, 8))
                .targetType("web").targetSource("internal").description("t67")
                .priority("P3").status("active").ownerId(UUID.randomUUID())
                .build()));
    }

    // ------------------------------------------------------------------ 迁移

    @Test
    @DisplayName("空库执行迁移：四张表与默认客户/历史项目就位，且可重复执行")
    void migrationOnEmptyDatabase_isIdempotent() {
        runMigration();
        runMigration();     // 幂等：第二次不报错、不重复

        assertEquals(1, countBy("select count(*) from customers where code = 'CUST-DEFAULT'"),
                "默认客户应只有一条");
        assertEquals(1, countBy("select count(*) from projects where code = 'PRJ-HISTORY'"),
                "历史项目应只有一条");

        Customer customer = customerRepository.findByCode("CUST-DEFAULT").orElseThrow();
        assertEquals("历史数据", customer.getName());
        assertEquals("active", customer.getStatus());

        Project project = projectRepository.findByCode("PRJ-HISTORY").orElseThrow();
        assertEquals(customer.getId(), project.getCustomerId(), "历史项目应挂在默认客户下");
        assertEquals("ACTIVE", project.getStatus());
        assertNotNull(project.getManagerId(), "managerId 非空（取首个 admin，兜底首个用户/哨兵）");
        assertEquals(Boolean.FALSE, project.getIsPhased());
    }

    @Test
    @DisplayName("存量数据执行迁移：全部资产各有一条历史挂靠，重复执行不重复")
    void migrationWithExistingAssets_backfillsEveryAsset() {
        Asset a1 = newAsset("a1");
        Asset a2 = newAsset("a2");
        Asset a3 = newAsset("a3");

        runMigration();
        runMigration();     // 幂等

        Project history = projectRepository.findByCode("PRJ-HISTORY").orElseThrow();
        Customer customer = customerRepository.findByCode("CUST-DEFAULT").orElseThrow();

        List<AssetProjectLink> links = assetProjectLinkRepository.findByProjectId(history.getId());
        assertEquals(3, links.size(), "三个存量资产各回填一条，重复执行不新增");

        for (Asset asset : List.of(a1, a2, a3)) {
            List<AssetProjectLink> ofAsset = assetProjectLinkRepository.findByAssetId(asset.getId());
            assertEquals(1, ofAsset.size(), "每个资产恰好一条历史挂靠");
            AssetProjectLink link = ofAsset.get(0);
            assertEquals(history.getId(), link.getProjectId());
            assertEquals(customer.getId(), link.getCustomerId(), "冗余 customerId 指向默认客户");
            assertEquals("SHARED", link.getLinkType());
            assertEquals(null, link.getPhaseId(), "历史回填为项目级（无期次）");
            assertEquals(AssetProjectLink.PHASE_KEY_NONE, link.getPhaseKey(), "phase_key 为哨兵值");
        }
    }

    @Test
    @DisplayName("回填与业务新增共存：迁移不覆盖已有挂靠，只补缺失的")
    void migrationDoesNotDuplicateExistingLinks() {
        Asset existing = newAsset("exist");
        Asset fresh = newAsset("fresh");

        runMigration();
        Project history = projectRepository.findByCode("PRJ-HISTORY").orElseThrow();
        assertEquals(2, assetProjectLinkRepository.countByProjectId(history.getId()));

        // 再次执行：既不做全表重插，也不报唯一键冲突
        runMigration();
        assertEquals(2, assetProjectLinkRepository.countByProjectId(history.getId()),
                "重复执行后仍为 2 条");

        // 新增一个资产后再跑：只补新资产那一条
        Asset later = newAsset("later");
        runMigration();
        assertEquals(3, assetProjectLinkRepository.countByProjectId(history.getId()));
        assertTrue(assetProjectLinkRepository
                        .existsByAssetIdAndProjectIdAndPhaseId(later.getId(), history.getId(), null),
                "新资产被补齐");
        assertTrue(assetProjectLinkRepository
                        .existsByAssetIdAndProjectIdAndPhaseId(existing.getId(), history.getId(), null),
                "老资产仍在");
        assertTrue(assetProjectLinkRepository
                        .existsByAssetIdAndProjectIdAndPhaseId(fresh.getId(), history.getId(), null),
                "老资产仍在");
    }

    // ------------------------------------------------------------------ 实体与仓储

    @Test
    @DisplayName("客户/项目/期次 CRUD 与按 customer、manager 查询")
    @Transactional
    void customerProjectPhaseQueries() {
        Customer customer = customerRepository.save(Objects.requireNonNull(Customer.builder()
                .code("CUST-T67-1").name("某银行").industry("金融").level("A")
                .contactName("张三").contactPhone("13800000000").contactEmail("zs@example.com")
                .status("active").description("t67 测试客户").build()));
        assertNotNull(customer.getCreatedAt());
        assertNotNull(customer.getUpdatedAt());

        UUID manager = UUID.randomUUID();
        Project p1 = projectRepository.save(Objects.requireNonNull(Project.builder()
                .code("PRJ-T67-1").customerId(customer.getId()).name("一期渗透")
                .type("PENTEST").status("ACTIVE").managerId(manager)
                .startDate(LocalDate.now()).isPhased(true).contractNo("HT-001").build()));
        Project p2 = projectRepository.save(Objects.requireNonNull(Project.builder()
                .code("PRJ-T67-2").customerId(customer.getId()).name("代码审计")
                .type("CODE_AUDIT").status("DRAFT").managerId(UUID.randomUUID()).build()));

        assertEquals(2, projectRepository.findByCustomerId(customer.getId()).size());
        assertEquals(1, projectRepository.findByManagerId(manager).size());
        assertEquals(p1.getId(), projectRepository.findByManagerId(manager).get(0).getId());
        assertTrue(projectRepository.findByCustomerIdAndStatus(customer.getId(), "DRAFT").stream()
                .anyMatch(p -> p.getId().equals(p2.getId())));

        ProjectPhase ph2 = projectPhaseRepository.save(Objects.requireNonNull(ProjectPhase.builder()
                .projectId(p1.getId()).phaseNo(2).name("二期").status("PLANNED").build()));
        ProjectPhase ph1 = projectPhaseRepository.save(Objects.requireNonNull(ProjectPhase.builder()
                .projectId(p1.getId()).phaseNo(1).name("一期").status("DONE").build()));

        List<ProjectPhase> phases = projectPhaseRepository.findByProjectIdOrderByPhaseNo(p1.getId());
        assertEquals(2, phases.size());
        assertEquals(1, phases.get(0).getPhaseNo(), "按期次号升序");
        assertEquals(2, phases.get(1).getPhaseNo());
        assertTrue(projectPhaseRepository.findByProjectIdAndPhaseNo(p1.getId(), 1).isPresent());
        assertEquals(ph1.getId(), projectPhaseRepository.findByProjectIdAndPhaseNo(p1.getId(), 1)
                .orElseThrow().getId());
        assertEquals(ph2.getId(), projectPhaseRepository.findByProjectIdAndPhaseNo(p1.getId(), 2)
                .orElseThrow().getId());
    }

    @Test
    @DisplayName("挂靠：按项目/期次/客户查询正确，且 phaseId=null 也能按「项目级」检索")
    void assetLinkQueries() {
        Customer customer = customerRepository.save(Objects.requireNonNull(Customer.builder()
                .code("CUST-T67-2").name("某制造业").level("B").status("active").build()));
        Project project = projectRepository.save(Objects.requireNonNull(Project.builder()
                .code("PRJ-T67-3").customerId(customer.getId()).name("年度服务")
                .managerId(UUID.randomUUID()).status("ACTIVE").isPhased(true).build()));
        ProjectPhase phase = projectPhaseRepository.save(Objects.requireNonNull(ProjectPhase.builder()
                .projectId(project.getId()).phaseNo(1).name("一期").build()));

        Asset asset = newAsset("link");
        AssetProjectLink projectLevel = assetProjectLinkRepository.save(Objects.requireNonNull(
                AssetProjectLink.builder()
                        .assetId(asset.getId()).projectId(project.getId()).phaseId(null)
                        .customerId(customer.getId()).linkType("SHARED").remark("项目级").build()));
        AssetProjectLink phaseLevel = assetProjectLinkRepository.save(Objects.requireNonNull(
                AssetProjectLink.builder()
                        .assetId(asset.getId()).projectId(project.getId()).phaseId(phase.getId())
                        .customerId(customer.getId()).linkType("TARGET").build()));

        assertEquals(2, assetProjectLinkRepository.findByAssetId(asset.getId()).size());
        assertEquals(2, assetProjectLinkRepository.findByProjectId(project.getId()).size());
        assertEquals(2, assetProjectLinkRepository.findByCustomerId(customer.getId()).size());

        List<AssetProjectLink> projectLevelOnly =
                assetProjectLinkRepository.findByProjectIdAndPhaseId(project.getId(), null);
        assertEquals(1, projectLevelOnly.size(), "phaseId=null 只匹配项目级挂靠");
        assertEquals(projectLevel.getId(), projectLevelOnly.get(0).getId());

        List<AssetProjectLink> phaseLevelOnly =
                assetProjectLinkRepository.findByProjectIdAndPhaseId(project.getId(), phase.getId());
        assertEquals(1, phaseLevelOnly.size());
        assertEquals(phaseLevel.getId(), phaseLevelOnly.get(0).getId());

        assertTrue(assetProjectLinkRepository
                .existsByAssetIdAndProjectIdAndPhaseId(asset.getId(), project.getId(), null));
        assertTrue(assetProjectLinkRepository
                .existsByAssetIdAndProjectIdAndPhaseId(asset.getId(), project.getId(), phase.getId()));
    }

    @Test
    @DisplayName("唯一约束：同一资产在同一项目下「无期次」只允许一条（phase_key 方案，NULL 不去重）")
    void projectLevelLink_isUniqueDespiteNullPhase() {
        Customer customer = customerRepository.save(Objects.requireNonNull(Customer.builder()
                .code("CUST-T67-3").name("某医院").status("active").build()));
        Project project = projectRepository.save(Objects.requireNonNull(Project.builder()
                .code("PRJ-T67-4").customerId(customer.getId()).name("等保测评")
                .managerId(UUID.randomUUID()).status("ACTIVE").build()));
        Asset asset = newAsset("uniq");

        assetProjectLinkRepository.saveAndFlush(Objects.requireNonNull(AssetProjectLink.builder()
                .assetId(asset.getId()).projectId(project.getId()).phaseId(null)
                .customerId(customer.getId()).build()));

        // 同一 (asset, project, 无期次) 再插一条 → 唯一索引（asset_id, project_id, phase_key）拒绝
        assertThrows(DataIntegrityViolationException.class, () ->
                assetProjectLinkRepository.saveAndFlush(Objects.requireNonNull(AssetProjectLink.builder()
                        .assetId(asset.getId()).projectId(project.getId()).phaseId(null)
                        .customerId(customer.getId()).build())),
                "MariaDB/H2 的唯一索引对 NULL 不去重，phase_key 必须让它去重");

        // 换成「有期次」则允许并存
        ProjectPhase phase = projectPhaseRepository.save(Objects.requireNonNull(ProjectPhase.builder()
                .projectId(project.getId()).phaseNo(1).name("一期").build()));
        assetProjectLinkRepository.saveAndFlush(Objects.requireNonNull(AssetProjectLink.builder()
                .assetId(asset.getId()).projectId(project.getId()).phaseId(phase.getId())
                .customerId(customer.getId()).build()));

        assertEquals(2, assetProjectLinkRepository.findByAssetId(asset.getId()).size());
    }

    @Test
    @DisplayName("唯一约束：同项目内期次号不可重复；客户/项目 code 唯一")
    void otherUniqueConstraints() {
        Customer customer = customerRepository.save(Objects.requireNonNull(Customer.builder()
                .code("CUST-T67-4").name("某运营商").status("active").build()));
        assertThrows(DataIntegrityViolationException.class, () ->
                customerRepository.saveAndFlush(Objects.requireNonNull(Customer.builder()
                        .code("CUST-T67-4").name("重复编号").build())), "customer code 唯一");

        Project project = projectRepository.save(Objects.requireNonNull(Project.builder()
                .code("PRJ-T67-5").customerId(customer.getId()).name("渗透")
                .managerId(UUID.randomUUID()).status("ACTIVE").build()));
        assertThrows(DataIntegrityViolationException.class, () ->
                projectRepository.saveAndFlush(Objects.requireNonNull(Project.builder()
                        .code("PRJ-T67-5").customerId(customer.getId()).name("重复")
                        .managerId(UUID.randomUUID()).build())), "project code 唯一");

        projectPhaseRepository.saveAndFlush(Objects.requireNonNull(ProjectPhase.builder()
                .projectId(project.getId()).phaseNo(1).name("一期").build()));
        assertThrows(DataIntegrityViolationException.class, () ->
                projectPhaseRepository.saveAndFlush(Objects.requireNonNull(ProjectPhase.builder()
                        .projectId(project.getId()).phaseNo(1).name("重复期次").build())),
                "(project_id, phase_no) 唯一");
    }

    private int countBy(String sql) {
        Integer value = jdbcTemplate.queryForObject(sql, Integer.class);
        return value == null ? 0 : value;
    }
}
