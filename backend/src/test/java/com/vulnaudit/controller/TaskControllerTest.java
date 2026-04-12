package com.vulnaudit.controller;

import com.vulnaudit.entity.Task;
import com.vulnaudit.entity.User;
import com.vulnaudit.repository.TaskRepository;
import com.vulnaudit.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 任务管理模块测试类
 */
@DisplayName("任务管理模块测试")
public class TaskControllerTest extends BaseTest {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    private Task testTask;
    private User testAssignee;

    @BeforeEach
    void setUpTask() {
        // 使用唯一后缀避免数据冲突
        String uniqueSuffix = String.valueOf(System.currentTimeMillis());
        
        testAssignee = User.builder()
                .username("taskassignee_" + uniqueSuffix)
                .passwordHash("$2a$10$encrypted")
                .email("assignee_" + uniqueSuffix + "@task.com")
                .fullName("任务执行人")
                .status("active")
                .build();
        testAssignee = userRepository.save(Objects.requireNonNull(testAssignee));

        testTask = Task.builder()
                .title("测试任务")
                .description("用于测试的任务")
                .status("pending")
                .assignedTo(testAssignee.getId())
                .assignedBy(testAssignee.getId())
                .dueDate(LocalDateTime.now().plusDays(7))
                .build();
        testTask = taskRepository.save(Objects.requireNonNull(testTask));
    }

    @Nested
    @DisplayName("任务列表查询测试")
    class GetTaskListTests {

        @Test
        @DisplayName("获取任务列表 - 成功")
        void getTaskList_Success() throws Exception {
            mockMvc.perform(get("/api/v1/tasks")
                    .header("Authorization", engineerToken)
                    .param("page", "0")
                    .param("size", "10"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取任务列表 - 按状态筛选")
        void getTaskList_FilterByStatus() throws Exception {
            mockMvc.perform(get("/api/v1/tasks")
                    .header("Authorization", engineerToken)
                    .param("status", "pending"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("任务详情查询测试")
    class GetTaskTests {

        @Test
        @DisplayName("获取任务详情 - 成功")
        void getTask_Success() throws Exception {
            mockMvc.perform(get("/api/v1/tasks/" + testTask.getId())
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取任务详情 - 任务不存在")
        void getTask_NotFound() throws Exception {
            mockMvc.perform(get("/api/v1/tasks/" + UUID.randomUUID())
                    .header("Authorization", engineerToken))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("创建任务测试")
    class CreateTaskTests {

        @Test
        @DisplayName("创建任务 - 成功")
        void createTask_Success() throws Exception {
            UUID assetId = UUID.randomUUID();
            UUID assetVersionId = UUID.randomUUID();
            String taskJson = "{\"description\":\"新创建的任务\",\"assigneeId\":\"" + engineerUserId + "\",\"assetId\":\"" + assetId + "\",\"assetVersionId\":\"" + assetVersionId + "\",\"estimatedHours\":8.5,\"dueDate\":\"" + LocalDateTime.now().plusDays(3) + "\"}";

            mockMvc.perform(post("/api/v1/tasks")
                    .header("Authorization", leaderToken)
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .content(taskJson))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.title", startsWith("审计任务-")))
                    .andExpect(jsonPath("$.data.status").value("pending"))
                    .andExpect(jsonPath("$.data.estimatedHours").value(8.5))
                    .andExpect(jsonPath("$.data.assignedTo").value(engineerUserId.toString()))
                    .andExpect(jsonPath("$.data.assignedBy").value(leaderUserId.toString()))
                    .andExpect(jsonPath("$.data.assetId").value(assetId.toString()))
                    .andExpect(jsonPath("$.data.assetVersionId").value(assetVersionId.toString()));
        }

    }

    @Nested
    @DisplayName("任务分配测试")
    class AssignTaskTests {

        @Test
        @DisplayName("分配任务 - 组长成功")
        void assignTask_ByLeader_Success() throws Exception {
            mockMvc.perform(post("/api/v1/tasks/" + testTask.getId() + "/assign")
                    .header("Authorization", leaderToken)
                    .param("assigneeId", engineerUserId.toString()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("分配任务 - 工程师无权限")
        void assignTask_ByEngineer_Forbidden() throws Exception {
            mockMvc.perform(post("/api/v1/tasks/" + testTask.getId() + "/assign")
                    .header("Authorization", engineerToken)
                    .param("assigneeId", leaderUserId.toString()))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("更新任务测试")
    class UpdateTaskTests {

        @Test
        @DisplayName("更新任务状态 - 成功")
        void updateTaskStatus_Success() throws Exception {
            testTask.setAssignedTo(engineerUserId);
            taskRepository.save(Objects.requireNonNull(testTask));
            mockMvc.perform(put("/api/v1/tasks/" + testTask.getId() + "/status")
                    .header("Authorization", engineerToken)
                    .param("status", "in_progress"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("更新任务状态 - 非法流转被拒绝")
        void updateTaskStatus_InvalidTransition_BadRequest() throws Exception {
            testTask.setAssignedTo(engineerUserId);
            taskRepository.save(Objects.requireNonNull(testTask));
            mockMvc.perform(put("/api/v1/tasks/" + testTask.getId() + "/status")
                    .header("Authorization", engineerToken)
                    .param("status", "completed"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("任务审核流测试")
    class TaskReviewFlowTests {

        @Test
        @DisplayName("提交审核 - 负责人成功")
        void submitReview_ByAssignee_Success() throws Exception {
            testTask.setAssignedTo(engineerUserId);
            testTask.setAssignedBy(leaderUserId);
            testTask.setAuditConclusion("已完成审计，存在中危配置项");
            taskRepository.save(Objects.requireNonNull(testTask));

            mockMvc.perform(post("/api/v1/tasks/" + testTask.getId() + "/submit-review")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("review_pending"));
        }

        @Test
        @DisplayName("审核通过 - 组长成功")
        void approveReview_ByLeader_Success() throws Exception {
            testTask.setAssignedTo(engineerUserId);
            testTask.setAssignedBy(leaderUserId);
            testTask.setAuditConclusion("审计完成，建议加固");
            taskRepository.save(Objects.requireNonNull(testTask));

            mockMvc.perform(post("/api/v1/tasks/" + testTask.getId() + "/submit-review")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/v1/tasks/" + testTask.getId() + "/approve")
                    .header("Authorization", leaderToken)
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .content("{\"signature\":\"组长签字\",\"comment\":\"同意结论\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("completed"));
        }

        @Test
        @DisplayName("审核通过 - 工程师无权限")
        void approveReview_ByEngineer_Forbidden() throws Exception {
            testTask.setAssignedTo(engineerUserId);
            testTask.setAssignedBy(leaderUserId);
            testTask.setStatus("review_pending");
            testTask.setAuditConclusion("审计完成，待审核");
            taskRepository.save(Objects.requireNonNull(testTask));

            mockMvc.perform(post("/api/v1/tasks/" + testTask.getId() + "/approve")
                    .header("Authorization", engineerToken)
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .content("{\"signature\":\"工程师签字\"}"))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("任务评论测试")
    class TaskCommentTests {

        @Test
        @DisplayName("添加任务评论 - 成功")
        void addTaskComment_Success() throws Exception {
            testTask.setAssignedTo(engineerUserId);
            taskRepository.save(Objects.requireNonNull(testTask));
            mockMvc.perform(post("/api/v1/tasks/" + testTask.getId() + "/comments")
                    .header("Authorization", engineerToken)
                    .param("content", "这是一条测试评论")
                    .param("spentHours", "2.5"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("添加任务评论 - 支持执行类型与证据路径")
        void addTaskComment_WithExecutionMeta_Success() throws Exception {
            testTask.setAssignedTo(engineerUserId);
            taskRepository.save(Objects.requireNonNull(testTask));
            mockMvc.perform(post("/api/v1/tasks/" + testTask.getId() + "/comments")
                    .header("Authorization", engineerToken)
                    .param("content", "已完成环境获取")
                    .param("workType", "env_collect")
                    .param("evidencePath", "/evidence/task/login-analysis.md"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("添加任务评论 - 非负责人非协作者无权限")
        void addTaskComment_ByUnauthorized_Forbidden() throws Exception {
            testTask.setAssignedTo(leaderUserId);
            taskRepository.save(Objects.requireNonNull(testTask));
            mockMvc.perform(post("/api/v1/tasks/" + testTask.getId() + "/comments")
                    .header("Authorization", engineerToken)
                    .param("content", "无权限执行记录"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("查询任务评论列表 - 成功")
        void getTaskComments_Success() throws Exception {
            mockMvc.perform(get("/api/v1/tasks/" + testTask.getId() + "/comments")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("任务附件测试")
    class TaskAttachmentTests {

        @Test
        @DisplayName("查询任务附件列表 - 成功")
        void getTaskAttachments_Success() throws Exception {
            mockMvc.perform(get("/api/v1/tasks/" + testTask.getId() + "/attachments")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("删除任务测试")
    class DeleteTaskTests {

        @Test
        @DisplayName("删除任务 - 成功")
        void deleteTask_Success() throws Exception {
            mockMvc.perform(delete("/api/v1/tasks/" + testTask.getId())
                    .header("Authorization", adminToken))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("任务可见性测试")
    class TaskVisibilityTests {
        @Test
        @DisplayName("按指派人查询 - 普通工程师不可查看他人任务")
        void listByAssignee_EngineerCannotViewOthers_Forbidden() throws Exception {
            mockMvc.perform(get("/api/v1/tasks/assignee/" + leaderUserId)
                    .header("Authorization", engineerToken))
                    .andExpect(status().isForbidden());
        }
    }

    @Nested
    @DisplayName("任务协作者测试")
    class TaskCollaboratorTests {

        @Test
        @DisplayName("添加协作者后可记录执行项")
        void addCollaborator_ThenCanComment() throws Exception {
            testTask.setAssignedTo(leaderUserId);
            taskRepository.save(Objects.requireNonNull(testTask));

            mockMvc.perform(post("/api/v1/tasks/" + testTask.getId() + "/collaborators")
                    .header("Authorization", leaderToken)
                    .param("userId", engineerUserId.toString()))
                    .andExpect(status().isOk());

            mockMvc.perform(post("/api/v1/tasks/" + testTask.getId() + "/comments")
                    .header("Authorization", engineerToken)
                    .param("content", "协作者执行记录")
                    .param("spentHours", "1.0"))
                    .andExpect(status().isOk());

            Task updatedTask = taskRepository.findById(testTask.getId()).orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals(0, new BigDecimal("1.0").compareTo(updatedTask.getActualHours()));
        }
    }
}
