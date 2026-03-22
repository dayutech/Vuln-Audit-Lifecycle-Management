package com.vulnaudit.controller;

import com.vulnaudit.BaseTest;
import com.vulnaudit.entity.AuditLog;
import com.vulnaudit.entity.User;
import com.vulnaudit.repository.AuditLogRepository;
import com.vulnaudit.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 审计日志模块测试类
 */
@DisplayName("审计日志模块测试")
public class AuditLogControllerTest extends BaseTest {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    private AuditLog testAuditLog;
    private User testUser;

    @BeforeEach
    void setUpAuditLog() {
        testUser = User.builder()
                .username("audituser")
                .passwordHash("$2a$10$encrypted")
                .email("audit@test.com")
                .fullName("审计用户")
                .status("active")
                .build();
        testUser = userRepository.save(testUser);

        testAuditLog = AuditLog.builder()
                .userId(testUser.getId())
                .action("CREATE")
                .resourceType("vulnerability")
                .resourceId(UUID.randomUUID())
                .details("创建漏洞记录")
                .ipAddress("192.168.1.100")
                .createdAt(LocalDateTime.now())
                .build();
        testAuditLog = auditLogRepository.save(testAuditLog);
    }

    @Nested
    @DisplayName("审计日志列表查询测试")
    class GetAuditLogListTests {

        @Test
        @DisplayName("获取审计日志列表 - 成功")
        void getAuditLogList_Success() throws Exception {
            mockMvc.perform(get("/api/v1/audit-logs")
                    .header("Authorization", adminToken)
                    .param("page", "0")
                    .param("size", "10"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取审计日志列表 - 按用户筛选")
        void getAuditLogList_FilterByUser() throws Exception {
            mockMvc.perform(get("/api/v1/audit-logs")
                    .header("Authorization", adminToken)
                    .param("userId", testUser.getId().toString()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取审计日志列表 - 按操作类型筛选")
        void getAuditLogList_FilterByAction() throws Exception {
            mockMvc.perform(get("/api/v1/audit-logs")
                    .header("Authorization", adminToken)
                    .param("action", "CREATE"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取审计日志列表 - 按资源类型筛选")
        void getAuditLogList_FilterByResourceType() throws Exception {
            mockMvc.perform(get("/api/v1/audit-logs")
                    .header("Authorization", adminToken)
                    .param("resourceType", "vulnerability"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取审计日志列表 - 按时间范围筛选")
        void getAuditLogList_FilterByTimeRange() throws Exception {
            mockMvc.perform(get("/api/v1/audit-logs")
                    .header("Authorization", adminToken)
                    .param("startTime", LocalDateTime.now().minusDays(7).toString())
                    .param("endTime", LocalDateTime.now().toString()))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("审计日志详情查询测试")
    class GetAuditLogTests {

        @Test
        @DisplayName("获取审计日志详情 - 成功")
        void getAuditLog_Success() throws Exception {
            mockMvc.perform(get("/api/v1/audit-logs/" + testAuditLog.getId())
                    .header("Authorization", adminToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取审计日志详情 - 日志不存在")
        void getAuditLog_NotFound() throws Exception {
            mockMvc.perform(get("/api/v1/audit-logs/" + UUID.randomUUID())
                    .header("Authorization", adminToken))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("资源审计日志查询测试")
    class ResourceAuditLogTests {

        @Test
        @DisplayName("获取资源的审计日志 - 成功")
        void getResourceAuditLogs_Success() throws Exception {
            mockMvc.perform(get("/api/v1/audit-logs/resource")
                    .header("Authorization", adminToken)
                    .param("resourceType", "vulnerability")
                    .param("resourceId", testAuditLog.getResourceId().toString()))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("用户审计日志查询测试")
    class UserAuditLogTests {

        @Test
        @DisplayName("获取用户的审计日志 - 成功")
        void getUserAuditLogs_Success() throws Exception {
            mockMvc.perform(get("/api/v1/audit-logs/user/" + testUser.getId())
                    .header("Authorization", adminToken))
                    .andExpect(status().isOk());
        }
    }
}
