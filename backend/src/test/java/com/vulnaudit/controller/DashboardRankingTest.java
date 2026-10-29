package com.vulnaudit.controller;

import com.vulnaudit.entity.Task;
import com.vulnaudit.entity.User;
import com.vulnaudit.entity.UserRole;
import com.vulnaudit.repository.TaskRepository;
import com.vulnaudit.repository.UserRepository;
import com.vulnaudit.repository.UserRoleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 跨用户工作量排行测试（t20）。
 *
 * <p>覆盖：按 taskCount 倒序、工时聚合、统计窗口过滤、limit 生效，
 * 以及数据边界——管理类角色（admin / team_leader）看全量，普通工程师只看自己那条。
 */
@DisplayName("工作量排行测试")
public class DashboardRankingTest extends BaseTest {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String emptyUserToken;
    private UUID emptyUserId;

    @BeforeEach
    void setUpRanking() {
        // admin: 3 个任务（估 6h / 实 3h）
        createTask("admin 任务1", adminUserId, "6", "3", LocalDateTime.now());
        createTask("admin 任务2", adminUserId, "0", "0", LocalDateTime.now());
        createTask("admin 任务3", adminUserId, "0", "0", LocalDateTime.now());

        // engineer: 2 个窗口内任务（估 4h / 实 2h）+ 1 个 60 天前的任务（应被窗口排除）
        createTask("engineer 任务1", engineerUserId, "4", "2", LocalDateTime.now());
        createTask("engineer 任务2", engineerUserId, "0", "0", LocalDateTime.now());
        createTask("engineer 旧任务", engineerUserId, "9", "9", LocalDateTime.now().minusDays(60));

        // team_leader: 1 个任务（估 5h / 实 5h）
        createTask("leader 任务", leaderUserId, "5", "5", LocalDateTime.now());

        // 无任务用户（用于验证普通用户视角的 0 行展示）
        User emptyUser = userRepository.save(Objects.requireNonNull(User.builder()
                .username("emptyuser_" + System.currentTimeMillis())
                .passwordHash(passwordEncoder.encode("empty123"))
                .email("empty_" + System.currentTimeMillis() + "@test.com")
                .fullName("无任务用户")
                .status("active")
                .build()));
        emptyUserId = emptyUser.getId();
        UUID engineerRoleId = roleRepository.findByCode("vulnerability_engineer")
                .orElseThrow()
                .getId();
        userRoleRepository.save(Objects.requireNonNull(UserRole.builder()
                .userId(emptyUserId)
                .roleId(engineerRoleId)
                .assignedBy(adminUserId)
                .assignedAt(LocalDateTime.now())
                .build()));
        emptyUserToken = Objects.requireNonNull(
                generateTestToken(emptyUser.getUsername(), "ROLE_VULNERABILITY_ENGINEER"));
    }

    private Task createTask(String title, UUID assignee, String estimatedHours,
                            String actualHours, LocalDateTime completedAt) {
        return taskRepository.save(Objects.requireNonNull(Task.builder()
                .title(title)
                .assignedTo(assignee)
                .assignedBy(adminUserId)
                .status("completed")
                .completedAt(completedAt)
                .estimatedHours(new BigDecimal(estimatedHours))
                .actualHours(new BigDecimal(actualHours))
                .build()));
    }

    @Test
    @DisplayName("admin 视角：返回全量排行，按 taskCount 倒序且工时聚合正确")
    void ranking_adminSeesAllSortedDesc() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/workload/ranking")
                        .header("Authorization", adminToken)
                        .param("limit", "10")
                        .param("days", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                // admin 3 个任务排第一
                .andExpect(jsonPath("$.data[0].userId").value(adminUserId.toString()))
                .andExpect(jsonPath("$.data[0].taskCount").value(3))
                .andExpect(jsonPath("$.data[0].estimatedHours").value(6))
                .andExpect(jsonPath("$.data[0].actualHours").value(3))
                // engineer 窗口内 2 个任务（60 天前的被排除）
                .andExpect(jsonPath("$.data[1].userId").value(engineerUserId.toString()))
                .andExpect(jsonPath("$.data[1].taskCount").value(2))
                .andExpect(jsonPath("$.data[1].estimatedHours").value(4))
                .andExpect(jsonPath("$.data[1].actualHours").value(2))
                // leader 1 个任务
                .andExpect(jsonPath("$.data[2].userId").value(leaderUserId.toString()))
                .andExpect(jsonPath("$.data[2].taskCount").value(1))
                .andExpect(jsonPath("$.data[2].username").isNotEmpty());
    }

    @Test
    @DisplayName("team_leader 视角：同样可见全量排行")
    void ranking_teamLeaderSeesAll() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/workload/ranking")
                        .header("Authorization", leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));
    }

    @Test
    @DisplayName("普通工程师视角：只返回自己那条")
    void ranking_engineerSeesOnlySelf() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/workload/ranking")
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].userId").value(engineerUserId.toString()))
                .andExpect(jsonPath("$.data[0].taskCount").value(2))
                .andExpect(jsonPath("$.data[0].estimatedHours").value(4));
    }

    @Test
    @DisplayName("普通用户无任务时仍返回自己那条且 taskCount=0")
    void ranking_engineerWithoutTasks_returnsZeroRow() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/workload/ranking")
                        .header("Authorization", emptyUserToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].userId").value(emptyUserId.toString()))
                .andExpect(jsonPath("$.data[0].taskCount").value(0))
                .andExpect(jsonPath("$.data[0].estimatedHours").value(0));
    }

    @Test
    @DisplayName("limit 生效，且 days 缩小后窗口内数据随之减少")
    void ranking_limitAndWindow() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/workload/ranking")
                        .header("Authorization", adminToken)
                        .param("limit", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].userId").value(adminUserId.toString()));

        // 窗口 0 天 → 归一为默认 30 天，仍能拿到 3 行
        mockMvc.perform(get("/api/v1/dashboard/workload/ranking")
                        .header("Authorization", adminToken)
                        .param("days", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));

        // 上限收敛：limit=999 不应报错
        mockMvc.perform(get("/api/v1/dashboard/workload/ranking")
                        .header("Authorization", adminToken)
                        .param("limit", "999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));
    }

    @Test
    @DisplayName("排行不受 60 天前任务影响：把窗口设为 7 天后 engineer 仍是 2")
    void ranking_windowExcludesOldTasks() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/workload/ranking")
                        .header("Authorization", adminToken)
                        .param("days", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[1].userId").value(engineerUserId.toString()))
                .andExpect(jsonPath("$.data[1].taskCount").value(2));

        // 扩大到 365 天后，engineer 的旧任务（60 天前）被计入
        String json = mockMvc.perform(get("/api/v1/dashboard/workload/ranking")
                        .header("Authorization", adminToken)
                        .param("days", "365"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        boolean engineerCountThree = objectMapper.readTree(json).path("data").findValues("taskCount")
                .stream().anyMatch(node -> node.asInt() == 3);
        assertTrue(engineerCountThree, "365 天窗口应包含 60 天前的旧任务（engineer 3 条或 admin 3 条）");
        assertEquals(3, objectMapper.readTree(json).path("data").size());
    }
}
