package com.vulnaudit.service.impl;

import com.vulnaudit.controller.BaseTest;
import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.AuditLog;
import com.vulnaudit.repository.AuditLogRepository;
import com.vulnaudit.service.AuditLogService;
import com.vulnaudit.util.Paging;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 审计日志服务层测试（t2 补充覆盖）。
 *
 * <p>控制器层已由 {@code AuditLogEnhancementTest} 覆盖主链路，这里补的是服务层自身的
 * 口径与边界：
 * <ol>
 *   <li>多条件组合是「交集」而不是「并集」，且时间窗外、其它用户、其它操作都不命中；</li>
 *   <li>空白字符串筛选条件等同不传（否则会把「带空格的关键词」当成真实条件）；</li>
 *   <li>等值条件自动 trim；</li>
 *   <li>CSV 字段转义：含逗号加引号、内部引号翻倍、换行折成空格；</li>
 *   <li>导出复用同一套筛选，且上限为 {@link AuditLogService#EXPORT_LIMIT}；</li>
 *   <li>清理保留天数非正数时回落到 180 天默认值，并返回实际删除条数（幂等）；</li>
 *   <li>分页参数经 {@link Paging} 收敛（负页码、超大 size、size=0）。</li>
 * </ol>
 */
@DisplayName("审计日志服务层查询与导出测试")
public class AuditLogServiceQueryTest extends BaseTest {

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @BeforeEach
    void clearAuditLogs() {
        // BaseTest 不清理审计表；用 JPQL 批量删除，避免 Hibernate「先 INSERT 后 DELETE」的 flush 顺序问题
        entityManager.createQuery("DELETE FROM AuditLog").executeUpdate();
        entityManager.flush();
    }

    /**
     * 落一条审计并回写 created_at。
     *
     * <p>{@code @PrePersist} 会把 created_at 覆盖为当前时间，因此先 flush 落库，再用 JPQL 回写历史时间。
     */
    private AuditLog persistLog(UUID userId, String action, String resourceType,
                               String details, String ipAddress, LocalDateTime createdAt) {
        AuditLog saved = auditLogRepository.save(Objects.requireNonNull(AuditLog.builder()
                .userId(userId)
                .action(action)
                .resourceType(resourceType)
                .resourceId(userId)
                .details(details)
                .ipAddress(ipAddress)
                .build()));
        entityManager.flush();
        entityManager.createQuery("UPDATE AuditLog a SET a.createdAt = :time WHERE a.id = :id")
                .setParameter("time", createdAt)
                .setParameter("id", saved.getId())
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
        return saved;
    }

    // ------------------------------------------------------------------ 组合筛选

    @Test
    @DisplayName("多条件组合为交集：用户+操作+资源类型+时间窗+关键词只命中唯一一条")
    void findAll_combinedFiltersAreIntersection() {
        LocalDateTime inWindow = LocalDateTime.now().minusHours(1);
        AuditLog hit = persistLog(adminUserId, "CREATE", "asset", "组合筛选 命中 登录绕过", "10.0.0.1", inWindow);
        // 资源类型不符
        persistLog(adminUserId, "CREATE", "vulnerability", "组合筛选 但资源类型不符", "10.0.0.2", inWindow);
        // 用户不符
        persistLog(engineerUserId, "CREATE", "asset", "组合筛选 但用户不符", "10.0.0.3", inWindow);
        // 操作不符
        persistLog(adminUserId, "DELETE", "asset", "组合筛选 但操作不符", "10.0.0.4", inWindow);
        // 时间窗外
        persistLog(adminUserId, "CREATE", "asset", "组合筛选 但时间窗外", "10.0.0.5",
                LocalDateTime.now().minusDays(30));

        PageResponse<AuditLog> page = auditLogService.findAll(0, 50, adminUserId, "CREATE", "asset",
                LocalDateTime.now().minusHours(2), LocalDateTime.now().plusMinutes(1), "组合");

        assertEquals(1L, page.getTotalElements(), "五条审计中应只有一条同时满足全部条件");
        assertEquals(1, page.getContent().size());
        assertEquals(hit.getId(), page.getContent().get(0).getId());
        assertEquals("10.0.0.1", page.getContent().get(0).getIpAddress());
    }

    @Test
    @DisplayName("空白字符串筛选等同不传；等值条件自动 trim")
    void findAll_blankFiltersIgnoredAndEqualsTrimmed() {
        persistLog(adminUserId, "CREATE", "asset", "空白筛选 一", "10.0.1.1", LocalDateTime.now().minusMinutes(10));
        persistLog(engineerUserId, "DELETE", "vulnerability", "空白筛选 二", "10.0.1.2",
                LocalDateTime.now().minusMinutes(5));

        PageResponse<AuditLog> all = auditLogService.findAll(0, 50, null, "   ", "  ",
                null, null, "   ");
        assertEquals(2L, all.getTotalElements(), "空白 action/resourceType/keyword 不应过滤掉任何记录");

        PageResponse<AuditLog> trimmed = auditLogService.findAll(0, 50, null, " CREATE ", null,
                null, null, null);
        assertEquals(1L, trimmed.getTotalElements(), "action 两端空格应被 trim 后命中");
        assertEquals("CREATE", trimmed.getContent().get(0).getAction());
    }

    // ------------------------------------------------------------------ 导出

    @Test
    @DisplayName("导出 CSV：逗号加引号、内部引号翻倍、换行折成空格")
    void exportCsv_escapesCommaQuoteAndNewline() {
        persistLog(adminUserId, "CREATE", "asset", "含逗号,和\"引号\"\n换行", "10.0.2.1",
                LocalDateTime.now().minusMinutes(1));

        byte[] bytes = auditLogService.exportCsv(null, null, null, null, null, null);
        String text = new String(bytes, StandardCharsets.UTF_8);
        String[] lines = text.split("\n");

        assertEquals(2, lines.length, "表头 + 1 行数据（字段内换行已被折成空格，不应多出物理行）");
        assertTrue(lines[1].contains("\"含逗号,和\"\"引号\"\" 换行\""),
                "含逗号的字段应整体加引号且内部引号翻倍，实际: " + lines[1]);
        assertFalse(lines[1].contains("\"含逗号,和\"引号\""),
                "未翻倍内部引号的写法不应出现");
        assertTrue(lines[1].contains("10.0.2.1"), "IP 列应保留");
    }

    @Test
    @DisplayName("导出复用筛选条件，且上限常量与列表口径一致")
    void exportCsv_respectsFiltersAndLimitConstant() {
        persistLog(adminUserId, "CREATE", "asset", "导出命中项", "10.0.3.1", LocalDateTime.now().minusMinutes(3));
        persistLog(adminUserId, "DELETE", "asset", "导出未命中项", "10.0.3.2", LocalDateTime.now().minusMinutes(2));

        byte[] filtered = auditLogService.exportCsv(adminUserId, "CREATE", "asset", null, null, "命中");
        String text = new String(filtered, StandardCharsets.UTF_8);
        assertEquals(2, text.split("\n").length, "按 action/keyword 过滤后应只剩表头 + 1 行");
        assertTrue(text.contains("10.0.3.1"));
        assertFalse(text.contains("10.0.3.2"), "未命中筛选的记录不应被导出");

        // 首三字节为 BOM，保证 Excel 打开中文不乱码
        assertEquals((byte) 0xEF, filtered[0]);
        assertEquals((byte) 0xBB, filtered[1]);
        assertEquals((byte) 0xBF, filtered[2]);

        assertEquals(10_000, AuditLogService.EXPORT_LIMIT, "导出上限应为 10000 条");
        String unfiltered = new String(auditLogService.exportCsv(null, null, null, null, null, null),
                StandardCharsets.UTF_8);
        assertEquals(3, unfiltered.split("\n").length, "无筛选时应导出全部 2 条（表头 + 2 行）");
    }

    // ------------------------------------------------------------------ 清理

    @Test
    @DisplayName("清理：保留天数<=0 回落 180 天，返回实际删除条数且可重复执行")
    void cleanOldLogs_nonPositiveDaysFallsBackToDefault() {
        persistLog(adminUserId, "CREATE", "asset", "保留期内", "10.0.4.1", LocalDateTime.now().minusDays(10));
        AuditLog expired = persistLog(adminUserId, "CREATE", "asset", "已过期", "10.0.4.2",
                LocalDateTime.now().minusDays(400));

        int deletedWithZero = auditLogService.cleanOldLogs(0);
        assertEquals(1, deletedWithZero, "0 天应回落到 180 天，只删掉 400 天前那条");
        assertTrue(auditLogRepository.findById(Objects.requireNonNull(expired.getId())).isEmpty());

        assertEquals(0, auditLogService.cleanOldLogs(0), "重复清理应为 0，说明按保留期幂等");
        assertEquals(1, auditLogRepository.count(), "保留期内的记录必须保留");

        int deletedWithNegative = auditLogService.cleanOldLogs(-5);
        assertEquals(0, deletedWithNegative, "负数同样应回落默认值，不应删除保留期内数据");
        assertEquals(1, auditLogRepository.count());
    }

    // ------------------------------------------------------------------ 分页收敛

    @Test
    @DisplayName("分页参数收敛：负页码归 0、超大 size 收敛到上限、size=0 归 1")
    void findAll_pagingIsClamped() {
        for (int i = 0; i < 3; i++) {
            persistLog(adminUserId, "CREATE", "asset", "分页用例 " + i, "10.0.5." + i,
                    LocalDateTime.now().minusMinutes(i + 1L));
        }

        PageResponse<AuditLog> huge = auditLogService.findAll(-3, 999_999, null, null, null,
                null, null, null);
        assertEquals(0, huge.getCurrentPage(), "负页码应归 0");
        assertEquals(Paging.MAX_PAGE_SIZE, huge.getPageSize(), "超大 size 应收敛到单页上限");

        PageResponse<AuditLog> zero = auditLogService.findAll(0, 0, null, null, null, null, null, null);
        assertEquals(1, zero.getPageSize(), "size=0 应归 1，避免除零/空页异常");
        assertEquals(3L, zero.getTotalElements(), "收敛 size 不应影响总条数");
    }
}
