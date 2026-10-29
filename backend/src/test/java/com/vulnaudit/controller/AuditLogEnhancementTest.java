package com.vulnaudit.controller;

import com.vulnaudit.entity.AuditLog;
import com.vulnaudit.entity.Team;
import com.vulnaudit.repository.AuditLogRepository;
import com.vulnaudit.repository.TeamRepository;
import com.vulnaudit.service.AuditLogCleanupJob;
import com.vulnaudit.service.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 审计日志增强测试（t2 验收）。
 *
 * <p>覆盖：
 * <ol>
 *   <li>列表的时间范围与关键词过滤；</li>
 *   <li>CSV 导出：首三字节为 BOM、首行为表头、内容与筛选一致、仅管理层可导出；</li>
 *   <li>过期清理：超过保留期的记录被删除并返回删除条数；</li>
 *   <li>导入审计去重：一次导入只产生一条 import 审计（且是带上下文的 Service 记录）。</li>
 * </ol>
 */
@DisplayName("审计日志增强测试")
public class AuditLogEnhancementTest extends BaseTest {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private AuditLogCleanupJob auditLogCleanupJob;

    @Autowired
    private TeamRepository teamRepository;

    private AuditLog recentLog;
    private AuditLog oldLog;

    /**
     * 清空审计表。
     *
     * <p>用 JPQL 批量删除而不是 {@code deleteAll()}：Hibernate 的 flush 顺序是「先 INSERT 后 DELETE」，
     * 同一事务里先删后插会把刚插入的审计一并删掉，导致断言失真。
     */
    private void clearAuditLogs() {
        entityManager.createQuery("DELETE FROM AuditLog").executeUpdate();
        entityManager.flush();
    }

    @BeforeEach
    void setUpLogs() {
        clearAuditLogs();

        recentLog = auditLogRepository.save(Objects.requireNonNull(AuditLog.builder()
                .userId(adminUserId)
                .action("CREATE")
                .resourceType("vulnerability")
                .resourceId(adminUserId)
                .details("创建漏洞记录：登录绕过")
                .ipAddress("10.0.0.8")
                .createdAt(LocalDateTime.now().minusMinutes(5))
                .build()));

        oldLog = auditLogRepository.save(Objects.requireNonNull(AuditLog.builder()
                .userId(engineerUserId)
                .action("DELETE")
                .resourceType("asset")
                .resourceId(engineerUserId)
                .details("删除资产记录（历史归档）")
                .ipAddress("10.0.0.9")
                .createdAt(LocalDateTime.now().minusDays(400))
                .build()));

        // @PrePersist 会统一把 createdAt 覆盖为当前时间，这里显式回写历史时间以模拟真实归档数据
        entityManager.createQuery("UPDATE AuditLog a SET a.createdAt = :time WHERE a.id = :id")
                .setParameter("time", LocalDateTime.now().minusDays(400))
                .setParameter("id", oldLog.getId())
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }

    // ------------------------------------------------------------------ 查询过滤

    @Test
    @DisplayName("列表按时间范围过滤：只返回窗口内的记录")
    void listAuditLogs_filterByTimeRange() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs")
                        .header("Authorization", adminToken)
                        .param("startTime", LocalDateTime.now().minusDays(1).toString())
                        .param("endTime", LocalDateTime.now().plusDays(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].action").value("CREATE"));

        // 覆盖全部历史的窗口应返回两条
        mockMvc.perform(get("/api/v1/audit-logs")
                        .header("Authorization", adminToken)
                        .param("startTime", LocalDateTime.now().minusDays(500).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    @DisplayName("列表按关键词过滤：命中详情或操作/资源类型")
    void listAuditLogs_filterByKeyword() throws Exception {
        // 命中 details
        mockMvc.perform(get("/api/v1/audit-logs")
                        .header("Authorization", adminToken)
                        .param("keyword", "登录绕过"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].action").value("CREATE"));

        // 命中 action（大小写不敏感）
        mockMvc.perform(get("/api/v1/audit-logs")
                        .header("Authorization", adminToken)
                        .param("keyword", "delete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].resourceType").value("asset"));

        // 命中 resourceType
        mockMvc.perform(get("/api/v1/audit-logs")
                        .header("Authorization", adminToken)
                        .param("keyword", "vulnerability"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        // 无命中
        mockMvc.perform(get("/api/v1/audit-logs")
                        .header("Authorization", adminToken)
                        .param("keyword", "不存在的关键词"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    // ------------------------------------------------------------------ 导出

    @Test
    @DisplayName("导出 CSV：UTF-8 BOM + 表头 + 与筛选一致的数据行")
    void exportAuditLogs_returnsBomCsv() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/audit-logs/export")
                        .header("Authorization", adminToken)
                        .param("action", "CREATE"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition", containsString("audit-logs.csv")))
                .andReturn();

        byte[] bytes = result.getResponse().getContentAsByteArray();
        assertTrue(bytes.length > 3, "导出内容不应为空");
        assertEquals((byte) 0xEF, bytes[0], "首字节应为 UTF-8 BOM");
        assertEquals((byte) 0xBB, bytes[1]);
        assertEquals((byte) 0xBF, bytes[2]);

        String text = new String(bytes, StandardCharsets.UTF_8);
        String[] lines = text.split("\n");
        assertEquals("\uFEFF时间,用户ID,操作,资源类型,资源ID,IP地址,详情", lines[0], "首行应为表头");
        assertEquals(2, lines.length, "按 action=CREATE 过滤后应只有表头 + 1 行数据");
        assertTrue(lines[1].contains("CREATE"), "数据行应包含操作类型");
        assertTrue(lines[1].contains("创建漏洞记录：登录绕过"), "数据行应包含详情（含中文）");
        assertTrue(lines[1].contains("10.0.0.8"), "数据行应包含 IP");

        // 不带筛选时应导出全部（表头 + 2 行）
        MvcResult all = mockMvc.perform(get("/api/v1/audit-logs/export")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andReturn();
        assertEquals(3, new String(all.getResponse().getContentAsByteArray(),
                StandardCharsets.UTF_8).split("\n").length);
    }

    @Test
    @DisplayName("导出 CSV 限管理层：工程师返回 403")
    void exportAuditLogs_forbiddenForEngineer() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs/export")
                        .header("Authorization", engineerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/audit-logs")
                        .header("Authorization", engineerToken))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ 清理

    @Test
    @DisplayName("过期清理：超过保留期的日志被删除并返回清理条数")
    void cleanupJob_deletesExpiredLogs() {
        long before = auditLogRepository.count();
        assertEquals(2, before);
        assertEquals(180, auditLogCleanupJob.retentionDays(), "默认保留天数应为 180（app.audit.retention-days）");

        int deleted = auditLogCleanupJob.cleanOnce();

        assertEquals(1, deleted, "默认保留 180 天，应只删除 400 天前的那一条");
        assertTrue(auditLogRepository.findById(Objects.requireNonNull(recentLog.getId())).isPresent(),
                "保留期内日志不应被删除");
        assertTrue(auditLogRepository.findById(Objects.requireNonNull(oldLog.getId())).isEmpty(),
                "过期日志应被删除");

        // 再执行一次应为 0，证明按保留期幂等
        assertEquals(0, auditLogCleanupJob.cleanOnce());
    }

    // ------------------------------------------------------------------ 去重

    @Test
    @DisplayName("一次导入只产生一条 import 审计（避免拦截器重复记录）")
    void importAudit_isNotDuplicated() throws Exception {
        Team team = teamRepository.save(Objects.requireNonNull(Team.builder()
                .name("审计测试组")
                .leaderId(engineerUserId)
                .createdBy(adminUserId)
                .build()));

        String content = String.join("\n",
                "name,targetType,targetSource,priority,status,teamId,description",
                "客户门户,Web应用,客户委托,P1,active," + team.getId() + ",审计去重用例") + "\n";
        MockMultipartFile file = new MockMultipartFile("file", "audit-import.csv", "text/csv",
                content.getBytes(StandardCharsets.UTF_8));

        clearAuditLogs();

        mockMvc.perform(multipart("/api/v1/import/assets").file(file)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successRows").value(1));

        List<AuditLog> importLogs = auditLogRepository.findAll().stream()
                .filter(item -> "POST ImportController.importAssets".equals(item.getAction()))
                .toList();
        assertEquals(1, importLogs.size(), "一次导入只应有一条 import 审计");

        AuditLog audit = importLogs.get(0);
        assertTrue(audit.getDetails() != null && audit.getDetails().startsWith("CSV导入资产"),
                "保留的应是 Service 手工写入的带上下文审计，实际: " + audit.getDetails());
        assertEquals("import", audit.getResourceType());

        // 拦截器的「URI 型详情」不应出现（说明 ImportController 已被跳过）
        List<AuditLog> interceptorStyle = auditLogRepository.findAll().stream()
                .filter(item -> item.getDetails() != null && item.getDetails().startsWith("/api/v1/import"))
                .toList();
        assertTrue(interceptorStyle.isEmpty(), "拦截器不应再为导入接口写审计: " + interceptorStyle);
    }

    @Test
    @DisplayName("非白名单控制器的写操作仍由拦截器记录（跳过名单未误伤）")
    void otherWriteOperations_stillAudited() throws Exception {
        clearAuditLogs();

        // 用创建团队（TeamController，不在跳过名单）验证统一审计仍生效
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "name", "审计拦截器回归组",
                "description", "验证写操作审计",
                "leaderId", engineerUserId.toString(),
                "createdBy", adminUserId.toString()));

        mockMvc.perform(post("/api/v1/teams")
                        .header("Authorization", adminToken)
                        .contentType(Objects.requireNonNull(org.springframework.http.MediaType.APPLICATION_JSON))
                        .content(body))
                .andExpect(status().isCreated());

        List<AuditLog> logs = auditLogRepository.findAll();
        assertTrue(logs.stream().anyMatch(item -> item.getAction() != null
                        && item.getAction().startsWith("POST TeamController.")),
                "TeamController 的写操作应被拦截器记录，实际: " + logs.stream().map(AuditLog::getAction).toList());
    }
}
