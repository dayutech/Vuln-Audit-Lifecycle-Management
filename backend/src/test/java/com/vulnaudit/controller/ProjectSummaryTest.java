package com.vulnaudit.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.AssetProjectLink;
import com.vulnaudit.entity.Customer;
import com.vulnaudit.entity.Project;
import com.vulnaudit.entity.ProjectPhase;
import com.vulnaudit.entity.Task;
import com.vulnaudit.entity.Team;
import com.vulnaudit.repository.AssetProjectLinkRepository;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.CustomerRepository;
import com.vulnaudit.repository.ProjectPhaseRepository;
import com.vulnaudit.repository.ProjectRepository;
import com.vulnaudit.repository.TaskRepository;
import com.vulnaudit.repository.TeamRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Objects;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 项目列表聚合接口测试（t82）
 *
 * <p>目标：{@code GET /projects/summary} 一次请求返回「项目 + phaseCount + assetCount +
 * openTaskCount + managerName + customerName」，取代前端逐项目查详情的 N+1。
 *
 * <p>重点验证：
 * <ol>
 *   <li>计数与库内独立计算一致；</li>
 *   <li>工程师视角 {@code assetCount} 收敛、{@code phaseCount} 不收敛（口径不能混用）；</li>
 *   <li>空结果不报错；customerId 过滤生效；</li>
 *   <li><b>查询次数不随页大小变化</b>（用 Hibernate Statistics 实采 SQL 条数）。</li>
 * </ol>
 */
@DisplayName("项目列表聚合接口测试")
class ProjectSummaryTest extends BaseTest {

    @Autowired private CustomerRepository customerRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectPhaseRepository projectPhaseRepository;
    @Autowired private AssetProjectLinkRepository linkRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private Team team;
    private Customer customerA;
    private Customer customerB;
    private Project projectA1;   // 2 期 / 2 资产（其中 1 个仅管理员可见）
    private Project projectA2;   // 0 期 / 1 资产
    private Project projectB1;   // 1 期 / 0 资产

    @BeforeEach
    void setUpSummaryData() {
        String suffix = String.valueOf(System.nanoTime());
        team = teamRepository.save(Objects.requireNonNull(Team.builder()
                .name("T82 团队-" + suffix).leaderId(leaderUserId).createdBy(adminUserId).build()));

        customerA = customerRepository.save(Objects.requireNonNull(Customer.builder()
                .code("CUST-T82A-" + suffix).name("T82 客户A").status("active")
                .createdBy(adminUserId).build()));
        customerB = customerRepository.save(Objects.requireNonNull(Customer.builder()
                .code("CUST-T82B-" + suffix).name("T82 客户B").status("active")
                .createdBy(adminUserId).build()));

        projectA1 = projectRepository.save(Objects.requireNonNull(Project.builder()
                .code("PRJ-T82A1-" + suffix).customerId(customerA.getId()).name("T82 项目A1")
                .type("PENTEST").status("ACTIVE").managerId(adminUserId).isPhased(true)
                .createdBy(adminUserId).build()));
        projectA2 = projectRepository.save(Objects.requireNonNull(Project.builder()
                .code("PRJ-T82A2-" + suffix).customerId(customerA.getId()).name("T82 项目A2")
                .type("CODE_AUDIT").status("DRAFT").managerId(leaderUserId).isPhased(false)
                .createdBy(adminUserId).build()));
        projectB1 = projectRepository.save(Objects.requireNonNull(Project.builder()
                .code("PRJ-T82B1-" + suffix).customerId(customerB.getId()).name("T82 项目B1")
                .type("COMPLIANCE").status("ACTIVE").managerId(adminUserId).isPhased(true)
                .createdBy(adminUserId).build()));

        projectPhaseRepository.save(Objects.requireNonNull(ProjectPhase.builder()
                .projectId(projectA1.getId()).phaseNo(1).name("一期").build()));
        projectPhaseRepository.save(Objects.requireNonNull(ProjectPhase.builder()
                .projectId(projectA1.getId()).phaseNo(2).name("二期").build()));
        projectPhaseRepository.save(Objects.requireNonNull(ProjectPhase.builder()
                .projectId(projectB1.getId()).phaseNo(1).name("B 一期").build()));

        Asset adminOnly = assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name("T82 管理员资产").code("T82A-" + UUID.randomUUID().toString().substring(0, 8))
                .targetType("web").targetSource("external").description("t82")
                .priority("P1").status("active").ownerId(adminUserId).teamId(team.getId()).build()));
        Asset engineerOwned = assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name("T82 工程师资产").code("T82E-" + UUID.randomUUID().toString().substring(0, 8))
                .targetType("api").targetSource("internal").description("t82")
                .priority("P2").status("active").ownerId(engineerUserId).teamId(team.getId()).build()));
        Asset shared = assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name("T82 共享资产").code("T82S-" + UUID.randomUUID().toString().substring(0, 8))
                .targetType("web").targetSource("internal").description("t82")
                .priority("P3").status("active").ownerId(engineerUserId).teamId(team.getId()).build()));

        link(adminOnly, projectA1);
        link(engineerOwned, projectA1);
        link(shared, projectA2);

        // 未完成任务：挂在 projectA1 的资产上 → 只有 1 个（另一个 completed 不计）
        taskRepository.save(Objects.requireNonNull(Task.builder()
                .title("T82 未完成任务").assetId(engineerOwned.getId()).assignedTo(engineerUserId)
                .assignedBy(adminUserId).status("in_progress").build()));
        taskRepository.save(Objects.requireNonNull(Task.builder()
                .title("T82 已完成任务").assetId(adminOnly.getId()).assignedTo(adminUserId)
                .assignedBy(adminUserId).status("completed").build()));
    }

    private void link(Asset asset, Project project) {
        linkRepository.saveAndFlush(Objects.requireNonNull(AssetProjectLink.builder()
                .assetId(asset.getId()).projectId(project.getId()).phaseId(null)
                .phaseKey(AssetProjectLink.PHASE_KEY_NONE).customerId(project.getCustomerId())
                .linkType("TARGET").linkedBy(adminUserId).build()));
    }

    private JsonNode summary(String token, String... params) throws Exception {
        var builder = get("/api/v1/projects/summary").header("Authorization", token);
        for (int i = 0; i < params.length; i += 2) {
            builder = builder.param(params[i], params[i + 1]);
        }
        String body = mockMvc.perform(builder)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    private JsonNode findProject(JsonNode content, UUID projectId) {
        for (JsonNode node : content) {
            if (projectId.toString().equals(node.path("id").asText())) {
                return node;
            }
        }
        throw new AssertionError("未找到项目 " + projectId);
    }

    // ------------------------------------------------------------------ 结构

    @Test
    @DisplayName("一次请求返回分页且每项带三个计数与经理/客户名")
    void summary_returnsCountsAndNames() throws Exception {
        JsonNode data = summary(adminToken, "size", "50");

        assertTrue(data.path("totalElements").asLong() >= 3);
        JsonNode a1 = findProject(data.path("content"), projectA1.getId());
        assertEquals("T82 项目A1", a1.path("name").asText());
        assertEquals(2, a1.path("phaseCount").asLong());
        assertEquals(2, a1.path("assetCount").asLong());
        assertEquals(1, a1.path("openTaskCount").asLong(), "只有一个未完成任务（completed 不计）");
        assertEquals("T82 客户A", a1.path("customerName").asText());
        assertEquals("管理员", a1.path("managerName").asText(), "managerName 取 fullName");

        JsonNode a2 = findProject(data.path("content"), projectA2.getId());
        assertEquals(0, a2.path("phaseCount").asLong());
        assertEquals(1, a2.path("assetCount").asLong());
        assertEquals("组长", a2.path("managerName").asText());
    }

    @Test
    @DisplayName("计数与库内独立计算一致（逐项目比对）")
    void summary_countsMatchDatabase() throws Exception {
        JsonNode data = summary(adminToken, "size", "50");

        for (Project project : java.util.List.of(projectA1, projectA2, projectB1)) {
            JsonNode node = findProject(data.path("content"), project.getId());
            long expectedPhases = projectPhaseRepository.findByProjectIdOrderByPhaseNo(project.getId()).size();
            long expectedAssets = linkRepository.findByProjectId(project.getId()).stream()
                    .map(AssetProjectLink::getAssetId).distinct().count();
            long expectedOpenTasks = taskRepository.countOpenTasksGroupedByProjectIds(java.util.List.of(project.getId()))
                    .stream().mapToLong(row -> ((Number) row[1]).longValue()).sum();

            assertEquals(expectedPhases, node.path("phaseCount").asLong(), project.getName() + " phaseCount");
            assertEquals(expectedAssets, node.path("assetCount").asLong(), project.getName() + " assetCount");
            assertEquals(expectedOpenTasks, node.path("openTaskCount").asLong(), project.getName() + " openTaskCount");
        }
    }

    // ------------------------------------------------------------------ 口径差异

    @Test
    @DisplayName("工程师：assetCount 收敛、phaseCount 不收敛（口径不混用）")
    void summary_engineerAssetCountScopedPhaseCountNot() throws Exception {
        JsonNode adminData = summary(adminToken, "size", "50");
        JsonNode engineerData = summary(engineerToken, "size", "50");

        JsonNode adminA1 = findProject(adminData.path("content"), projectA1.getId());
        JsonNode engineerA1 = findProject(engineerData.path("content"), projectA1.getId());

        assertEquals(2, adminA1.path("assetCount").asLong(), "管理员看到项目 A1 的全部 2 个资产");
        assertEquals(1, engineerA1.path("assetCount").asLong(),
                "工程师只能看到自己负责的那 1 个资产");
        assertEquals(2, engineerA1.path("phaseCount").asLong(),
                "期次数是组织结构事实，不随可见性收敛");
        assertEquals(adminA1.path("phaseCount").asLong(), engineerA1.path("phaseCount").asLong());

        // 未完成任务数随资产口径收敛：管理员 1（挂在工程师资产上，管理员也可见）
        assertEquals(1, adminA1.path("openTaskCount").asLong());
        assertEquals(1, engineerA1.path("openTaskCount").asLong());
    }

    // ------------------------------------------------------------------ 过滤 / 空结果

    @Test
    @DisplayName("customerId 过滤生效：只返回该客户的项目")
    void summary_filterByCustomer() throws Exception {
        JsonNode data = summary(adminToken, "customerId", customerB.getId().toString(), "size", "50");

        assertEquals(1, data.path("totalElements").asLong());
        assertEquals(projectB1.getId().toString(), data.path("content").get(0).path("id").asText());
        assertEquals(1, data.path("content").get(0).path("phaseCount").asLong());
        assertEquals(0, data.path("content").get(0).path("assetCount").asLong());
        assertEquals("T82 客户B", data.path("content").get(0).path("customerName").asText());
    }

    @Test
    @DisplayName("空结果不报错：无项目的客户返回空数组 + totalElements=0")
    void summary_emptyResult() throws Exception {
        Customer empty = customerRepository.save(Objects.requireNonNull(Customer.builder()
                .code("CUST-T82-EMPTY-" + System.nanoTime()).name("T82 空客户").status("active")
                .createdBy(adminUserId).build()));

        mockMvc.perform(get("/api/v1/projects/summary")
                        .param("customerId", empty.getId().toString())
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.totalPages").value(0))
                .andExpect(jsonPath("$.data.content", hasSize(0)));
    }

    // ------------------------------------------------------------------ 排序与分页

    @Test
    @DisplayName("分页：size 生效且 totalElements/totalPages 正确；size 上限 500")
    void summary_paging() throws Exception {
        JsonNode firstPage = summary(adminToken, "customerId", customerA.getId().toString(), "page", "0", "size", "1");
        assertEquals(2, firstPage.path("totalElements").asLong());
        assertEquals(2, firstPage.path("totalPages").asLong());
        assertEquals(1, firstPage.path("content").size());
        assertEquals(0, firstPage.path("currentPage").asInt());
        assertEquals(1, firstPage.path("pageSize").asInt());

        JsonNode secondPage = summary(adminToken, "customerId", customerA.getId().toString(), "page", "1", "size", "1");
        assertEquals(1, secondPage.path("content").size());
        assertTrue(!secondPage.path("content").get(0).path("id").asText()
                .equals(firstPage.path("content").get(0).path("id").asText()), "两页不应重复");

        JsonNode capped = summary(adminToken, "size", "99999");
        assertEquals(500, capped.path("pageSize").asInt(), "size 由 Paging 收敛到 500");
    }

    @Test
    @DisplayName("sort=assetCount：按可见资产数倒序（项目A1 优先于项目A2）")
    void summary_sortByAssetCount() throws Exception {
        JsonNode data = summary(engineerToken, "customerId", customerA.getId().toString(),
                "sort", "assetCount", "size", "50");

        assertEquals(2, data.path("content").size());
        JsonNode first = data.path("content").get(0);
        JsonNode second = data.path("content").get(1);
        assertTrue(first.path("assetCount").asLong() >= second.path("assetCount").asLong(),
                "应按 assetCount 倒序（相同计数时以 createdAt desc / id asc 稳定排序）");
    }

    // ------------------------------------------------------------------ 查询次数不变式

    @Test
    @DisplayName("查询次数不随页大小变化（Hibernate Statistics 实采）")
    void summary_queryCountIndependentOfPageSize() throws Exception {
        // 追加项目，让 size=1 与 size=10 命中不同数量的行
        for (int i = 0; i < 5; i++) {
            projectRepository.save(Objects.requireNonNull(Project.builder()
                    .code("PRJ-T82X-" + i + "-" + System.nanoTime())
                    .customerId(customerA.getId()).name("T82 批量项目" + i)
                    .type("PENTEST").status("ACTIVE").managerId(adminUserId).isPhased(false)
                    .createdBy(adminUserId).build()));
        }

        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);

        // 预热一次：让查询计划/实体图缓存就绪，避免首次调用的额外语句干扰计数
        summary(adminToken, "size", "1");

        long smallQueries = countQueries(statistics, () -> summary(adminToken, "size", "1"));
        long largeQueries = countQueries(statistics, () -> summary(adminToken, "size", "10"));

        assertTrue(smallQueries > 0, "统计应采到 SQL（实际 " + smallQueries + "）");
        // 固定预算：分页 2（数据 + count）+ 三个 group by + 两次 findAllById ≈ 7-8 条；
        // 断言「不随页大小线性增长」（若存在逐项目 N+1，size=10 会到 30+）
        assertTrue(largeQueries <= 12,
                "size=10 的 SQL 条数应在固定预算内（实际 " + largeQueries + "）");
        assertTrue(Math.abs(largeQueries - smallQueries) <= 2,
                "页大小 1 与 10 的查询条数不应随页大小变化（N+1 会线性增长）："
                        + smallQueries + " vs " + largeQueries);
    }

    /** 用 count 增量统计一次请求内的 SQL 条数（prepareStatementCount 是累计值，不能只用 clear()） */
    private long countQueries(Statistics statistics, ThrowingRunnable action) throws Exception {
        statistics.clear();
        long before = statistics.getPrepareStatementCount();
        action.run();
        long after = statistics.getPrepareStatementCount();
        return after - before;
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }
}
