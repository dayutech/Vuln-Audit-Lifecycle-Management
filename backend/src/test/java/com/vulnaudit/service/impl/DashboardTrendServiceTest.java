package com.vulnaudit.service.impl;

import com.vulnaudit.controller.BaseTest;
import com.vulnaudit.entity.Task;
import com.vulnaudit.entity.User;
import com.vulnaudit.repository.TaskRepository;
import com.vulnaudit.service.DashboardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 仪表盘趋势/排行的服务层测试（t20 补充覆盖）。
 *
 * <p>{@code DashboardTrendTest} / {@code DashboardRankingTest} 走的是 HTTP 层，
 * 这里直接调用 {@link DashboardService}，补的是容易被接口层掩盖的实现分支：
 * <ol>
 *   <li>工作量趋势的时间基准是 {@code coalesce(completed_at, updated_at)} ——
 *       没有 completed_at 时用 updated_at 归日（否则历史完成任务会整批丢出窗口）；</li>
 *   <li>completed_at 存在时优先使用它，不被 updated_at 拉平到今天；</li>
 *   <li>排行 limit 归一：非正数取默认 10、超大值收敛，窗口外任务不计入。</li>
 * </ol>
 */
@DisplayName("仪表盘趋势与排行服务层测试")
public class DashboardTrendServiceTest extends BaseTest {

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private TaskRepository taskRepository;

    // ------------------------------------------------------------------ 构造数据

    private Task newTask(String title, UUID assignee, String status, LocalDateTime completedAt,
                         String estimatedHours, String actualHours) {
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

    /** 直接改写 updated_at（JPQL 绕过 @PreUpdate，保证按历史时间归日） */
    private void shiftUpdatedAt(UUID taskId, LocalDateTime time) {
        entityManager.createQuery("UPDATE Task t SET t.updatedAt = :time WHERE t.id = :id")
                .setParameter("time", time)
                .setParameter("id", taskId)
                .executeUpdate();
        entityManager.flush();
        entityManager.clear();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> trendData(Map<String, Object> payload) {
        Object data = payload.get("data");
        assertNotNull(data, "趋势响应必须包含 data 列表");
        return (List<Map<String, Object>>) data;
    }

    private long countOn(List<Map<String, Object>> data, LocalDate date) {
        return data.stream()
                .filter(row -> date.toString().equals(String.valueOf(row.get("date"))))
                .mapToLong(row -> ((Number) row.get("count")).longValue())
                .sum();
    }

    private User newUser(String username) {
        return userRepository.save(Objects.requireNonNull(User.builder()
                .username(username)
                .passwordHash(passwordEncoder.encode("initPass123!"))
                .email(username + "@test.com")
                .fullName("排行用户 " + username)
                .status("active")
                .build()));
    }

    // ------------------------------------------------------------------ 趋势

    @Test
    @DisplayName("工作量趋势：无 completed_at 时按 updated_at 归日，不掉出窗口")
    void workloadTrend_fallsBackToUpdatedAt() {
        Task finishedLongAgo = newTask("三天前完成但无 completed_at", engineerUserId, "completed",
                null, "6", "4");
        shiftUpdatedAt(Objects.requireNonNull(finishedLongAgo.getId()),
                LocalDateTime.now().minusDays(3).withHour(12).withMinute(0).withSecond(0).withNano(0));

        Map<String, Object> payload = dashboardService.getWorkloadTrend(engineerUserId, 5);
        List<Map<String, Object>> data = trendData(payload);

        assertEquals(5, data.size(), "点数应恒等于 days");
        assertEquals(1L, countOn(data, LocalDate.now().minusDays(3)),
                "无 completed_at 的任务应回落到 updated_at 所在日，实际: " + data);
        assertEquals(1L, data.stream().mapToLong(row -> ((Number) row.get("count")).longValue()).sum(),
                "只应统计这一条");
    }

    @Test
    @DisplayName("工作量趋势：completed_at 优先于 updated_at，不被更新动作拉平到今天")
    void workloadTrend_prefersCompletedAtOverUpdatedAt() {
        newTask("四天前完成", engineerUserId, "completed", LocalDateTime.now().minusDays(4), "3", "3");
        // 未完成任务：即使今天更新过也不应计入（只统计 status=completed）
        newTask("进行中", engineerUserId, "in_progress", null, "8", "0");

        Map<String, Object> payload = dashboardService.getWorkloadTrend(engineerUserId, 5);
        List<Map<String, Object>> data = trendData(payload);

        assertEquals(1L, countOn(data, LocalDate.now().minusDays(4)), "应归到 completed_at 当天");
        assertEquals(0L, countOn(data, LocalDate.now()), "更新过但未完成的今日不应产生计数");
    }

    // ------------------------------------------------------------------ 排行

    @Test
    @DisplayName("排行：limit<=0 取默认 10，窗口外任务不计入，按任务数倒序")
    void workloadRanking_normalizesLimitAndWindow() {
        for (int i = 0; i < 12; i++) {
            User user = newUser("rank_user_" + i);
            newTask("排行任务 " + i, user.getId(), "completed", LocalDateTime.now(), "2", "1");
        }
        // 窗口外任务：7 天窗口下不应被统计
        User stale = newUser("rank_stale");
        newTask("十天前完成", stale.getId(), "completed", LocalDateTime.now().minusDays(10), "5", "5");

        List<Map<String, Object>> ranking = dashboardService.getWorkloadRanking(0, 7, true, adminUserId);

        assertEquals(10, ranking.size(), "limit=0 应回落默认 10 条");
        for (Map<String, Object> row : ranking) {
            assertEquals(1L, ((Number) row.get("taskCount")).longValue(),
                    "窗口外任务不应计入排行: " + row);
            assertTrue(row.get("username") != null, "排行行应带用户名");
        }
        assertTrue(ranking.stream().noneMatch(row -> "rank_stale".equals(row.get("username"))),
                "只有窗口外任务的用户不应出现在排行里");

        List<Map<String, Object>> three = dashboardService.getWorkloadRanking(3, 7, true, adminUserId);
        assertEquals(3, three.size(), "limit=3 应只返回 3 条");
    }

    @Test
    @DisplayName("非管理视角排行只返回自己那一行，无任务时 taskCount=0")
    void workloadRanking_selfScopeWhenNotAllUsers() {
        newTask("自己的任务", engineerUserId, "completed", LocalDateTime.now(), "1", "1");

        List<Map<String, Object>> self = dashboardService.getWorkloadRanking(10, 30, false, engineerUserId);
        assertEquals(1, self.size(), "非 allUsers 只返回当前用户一行");
        assertEquals(engineerUserId, self.get(0).get("userId"));
        assertEquals(1L, ((Number) self.get(0).get("taskCount")).longValue());

        User newcomer = newUser("rank_newcomer");
        List<Map<String, Object>> empty = dashboardService.getWorkloadRanking(10, 30, false, newcomer.getId());
        assertEquals(1, empty.size(), "无任务的新用户也应返回一行，保证前端渲染一致");
        assertEquals(0L, ((Number) empty.get(0).get("taskCount")).longValue());
    }
}
