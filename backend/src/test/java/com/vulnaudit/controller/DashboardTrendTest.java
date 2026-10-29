package com.vulnaudit.controller;

import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.Task;
import com.vulnaudit.entity.Vulnerability;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.DashboardConfigRepository;
import com.vulnaudit.repository.TaskRepository;
import com.vulnaudit.repository.VulnerabilityRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 仪表盘趋势与配置健壮性测试（t20）。
 *
 * <p>覆盖：
 * <ol>
 *   <li>资产/漏洞趋势按天归日、缺失天补 0、点数恒等于 days；</li>
 *   <li>days 边界：0 取默认 30、超大收敛 365、1 只返回 1 个点；</li>
 *   <li>工作量趋势只统计已完成任务，且按 completed_at（回退 updated_at）归日；</li>
 *   <li>配置接口仅传 cards 不再 500，layout 兜底 "[]"，重复保存幂等；读取未保存配置返回默认值且不落库。</li>
 * </ol>
 */
@DisplayName("仪表盘趋势与配置测试")
public class DashboardTrendTest extends BaseTest {

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private VulnerabilityRepository vulnerabilityRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private DashboardConfigRepository dashboardConfigRepository;

    // ------------------------------------------------------------------ 构造数据

    private Asset newAsset(String name, UUID ownerId) {
        return assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name(name)
                .code("AST-" + UUID.randomUUID().toString().substring(0, 8))
                .targetType("Web应用")
                .targetSource("客户委托")
                .priority("P2")
                .status("active")
                .ownerId(ownerId)
                .build()));
    }

    /** 改写资产的 created_at（@PrePersist 会覆盖为当前时间，测试里用 JPQL 回写） */
    private void shiftAssetCreatedAt(UUID assetId, LocalDateTime time) {
        entityManager.createQuery("UPDATE Asset a SET a.createdAt = :time WHERE a.id = :id")
                .setParameter("time", time)
                .setParameter("id", assetId)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }

    private Vulnerability newVulnerability(String title, UUID assetId) {
        return vulnerabilityRepository.save(Objects.requireNonNull(Vulnerability.builder()
                .title(title)
                .assetId(assetId)
                .type("SQL注入")
                .severity("high")
                .vulnerabilityNo("VUL-" + UUID.randomUUID().toString().substring(0, 8))
                .discovererId(engineerUserId)
                // description / detail 在库里是 NOT NULL，直接落库时必须显式给值
                .description("趋势用例漏洞")
                .detail("趋势用例漏洞详情")
                .build()));
    }

    private void shiftVulnerabilityCreatedAt(UUID id, LocalDateTime time) {
        entityManager.createQuery("UPDATE Vulnerability v SET v.createdAt = :time WHERE v.id = :id")
                .setParameter("time", time)
                .setParameter("id", id)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }

    private Task newTask(String title, UUID assignee, String status,
                         LocalDateTime completedAt, String estimatedHours, String actualHours) {
        return taskRepository.save(Objects.requireNonNull(Task.builder()
                .title(title)
                .assignedTo(assignee)
                .assignedBy(adminUserId)
                .status(status)
                .completedAt(completedAt)
                .estimatedHours(estimatedHours == null ? null : new BigDecimal(estimatedHours))
                .actualHours(actualHours == null ? BigDecimal.ZERO : new BigDecimal(actualHours))
                .build()));
    }

    // ------------------------------------------------------------------ 资产趋势

    @Test
    @DisplayName("资产趋势：按天归日、缺的那天补 0、点数等于 days")
    void assetTrend_fillsMissingDays() throws Exception {
        newAsset("今日资产A", adminUserId);
        newAsset("今日资产B", adminUserId);
        Asset older = newAsset("三天前资产", adminUserId);
        shiftAssetCreatedAt(older.getId(), LocalDateTime.now().minusDays(3));

        LocalDate today = LocalDate.now();

        mockMvc.perform(get("/api/v1/dashboard/trends/assets")
                        .header("Authorization", adminToken)
                        .param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days").value(7))
                .andExpect(jsonPath("$.data.startDate").value(today.minusDays(6).toString()))
                .andExpect(jsonPath("$.data.endDate").value(today.toString()))
                .andExpect(jsonPath("$.data.data.length()").value(7))
                // 轴从 today-6 起算，today-3 落在下标 3
                .andExpect(jsonPath("$.data.data[3].date").value(today.minusDays(3).toString()))
                .andExpect(jsonPath("$.data.data[3].count").value(1))
                .andExpect(jsonPath("$.data.data[6].date").value(today.toString()))
                .andExpect(jsonPath("$.data.data[6].count").value(2))
                .andExpect(jsonPath("$.data.data[0].count").value(0))
                .andExpect(jsonPath("$.data.data[1].count").value(0))
                .andExpect(jsonPath("$.data.data[4].count").value(0));
    }

    @Test
    @DisplayName("趋势 days 边界：0→30、超大→365、1→单点")
    void trend_daysBoundaries() throws Exception {
        newAsset("边界资产", adminUserId);

        mockMvc.perform(get("/api/v1/dashboard/trends/assets")
                        .header("Authorization", adminToken)
                        .param("days", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days").value(30))
                .andExpect(jsonPath("$.data.data.length()").value(30));

        mockMvc.perform(get("/api/v1/dashboard/trends/assets")
                        .header("Authorization", adminToken)
                        .param("days", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days").value(365))
                .andExpect(jsonPath("$.data.data.length()").value(365));

        mockMvc.perform(get("/api/v1/dashboard/trends/assets")
                        .header("Authorization", adminToken)
                        .param("days", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days").value(1))
                .andExpect(jsonPath("$.data.data.length()").value(1))
                .andExpect(jsonPath("$.data.data[0].count").value(1));
    }

    // ------------------------------------------------------------------ 漏洞趋势

    @Test
    @DisplayName("漏洞趋势：今日与昨日各 1 条，窗口外不计入")
    void vulnerabilityTrend_countsByCreatedAt() throws Exception {
        Asset asset = newAsset("漏洞归属资产", adminUserId);
        newVulnerability("今日漏洞", asset.getId());
        Vulnerability yesterday = newVulnerability("昨日漏洞", asset.getId());
        shiftVulnerabilityCreatedAt(yesterday.getId(), LocalDateTime.now().minusDays(1));

        LocalDate today = LocalDate.now();

        mockMvc.perform(get("/api/v1/dashboard/trends/vulnerabilities")
                        .header("Authorization", adminToken)
                        .param("days", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data.length()").value(3))
                .andExpect(jsonPath("$.data.data[1].date").value(today.minusDays(1).toString()))
                .andExpect(jsonPath("$.data.data[1].count").value(1))
                .andExpect(jsonPath("$.data.data[2].count").value(1));

        // 只取 1 天时不包含昨日数据
        mockMvc.perform(get("/api/v1/dashboard/trends/vulnerabilities")
                        .header("Authorization", adminToken)
                        .param("days", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.data[0].count").value(1));
    }

    // ------------------------------------------------------------------ 工作量趋势

    @Test
    @DisplayName("工作量趋势：只统计已完成任务，按 completed_at 归日")
    void workloadTrend_countsCompletedOnly() throws Exception {
        newTask("今日完成", engineerUserId, "completed", LocalDateTime.now(), "4", "3");
        newTask("两天前完成", engineerUserId, "completed", LocalDateTime.now().minusDays(2), "2", "2");
        newTask("进行中", engineerUserId, "in_progress", null, "8", "0");

        LocalDate today = LocalDate.now();

        mockMvc.perform(get("/api/v1/dashboard/trends/workload")
                        .header("Authorization", engineerToken)
                        .param("days", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.days").value(5))
                .andExpect(jsonPath("$.data.data.length()").value(5))
                .andExpect(jsonPath("$.data.data[4].date").value(today.toString()))
                .andExpect(jsonPath("$.data.data[4].count").value(1))
                .andExpect(jsonPath("$.data.data[2].date").value(today.minusDays(2).toString()))
                .andExpect(jsonPath("$.data.data[2].count").value(1));
    }

    // ------------------------------------------------------------------ 配置健壮性

    @Test
    @DisplayName("配置：只传 cards 返回 200，layout 兜底 []，重复保存幂等")
    void saveConfig_onlyCards_isRobust() throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of("cards", "[\"assets\",\"vulns\"]"));

        mockMvc.perform(post("/api/v1/dashboard/config")
                        .header("Authorization", engineerToken)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.layout").value("[]"))
                .andExpect(jsonPath("$.data.cards").value("[\"assets\",\"vulns\"]"));

        // 重复保存同一份内容：仍 200，且同一用户只有一行配置
        mockMvc.perform(post("/api/v1/dashboard/config")
                        .header("Authorization", engineerToken)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cards").value("[\"assets\",\"vulns\"]"));

        assertEquals(1, dashboardConfigRepository.findByUserId(engineerUserId)
                .map(config -> 1).orElse(0), "同一用户只应有一行仪表盘配置");

        // 只传 layout 时应保留已保存的 cards
        mockMvc.perform(post("/api/v1/dashboard/config")
                        .header("Authorization", engineerToken)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content("{\"layout\":\"{\\\"cols\\\":2}\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.layout").value("{\"cols\":2}"))
                .andExpect(jsonPath("$.data.cards").value("[\"assets\",\"vulns\"]"));

        mockMvc.perform(get("/api/v1/dashboard/config")
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.layout").value("{\"cols\":2}"))
                .andExpect(jsonPath("$.data.cards").value("[\"assets\",\"vulns\"]"));
    }

    @Test
    @DisplayName("配置：未保存时读取返回默认值且不落库")
    void getConfig_withoutSaved_returnsDefaults() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/config")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.layout").value("[]"))
                .andExpect(jsonPath("$.data.cards").value("[]"));

        assertTrue(dashboardConfigRepository.findByUserId(adminUserId).isEmpty(),
                "读取默认配置不应写入数据库");
    }
}
