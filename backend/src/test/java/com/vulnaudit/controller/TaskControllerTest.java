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

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

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
                .type("vulnerability")
                .description("用于测试的任务")
                .priority("P1")
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
            String taskJson = "{\"title\":\"新任务\",\"type\":\"vulnerability\",\"description\":\"新创建的任务\",\"priority\":\"P2\",\"assigneeId\":\"" + testAssignee.getId() + "\",\"dueDate\":\"" + LocalDateTime.now().plusDays(3) + "\"}";

            mockMvc.perform(post("/api/v1/tasks")
                    .header("Authorization", leaderToken)
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .content(taskJson))
                    .andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("更新任务测试")
    class UpdateTaskTests {

        @Test
        @DisplayName("更新任务状态 - 成功")
        void updateTaskStatus_Success() throws Exception {
            mockMvc.perform(put("/api/v1/tasks/" + testTask.getId() + "/status")
                    .header("Authorization", engineerToken)
                    .param("status", "in_progress"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("任务评论测试")
    class TaskCommentTests {

        @Test
        @DisplayName("添加任务评论 - 成功")
        void addTaskComment_Success() throws Exception {
            mockMvc.perform(post("/api/v1/tasks/" + testTask.getId() + "/comments")
                    .header("Authorization", engineerToken)
                    .param("content", "这是一条测试评论"))
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
}
