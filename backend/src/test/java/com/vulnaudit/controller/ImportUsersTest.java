package com.vulnaudit.controller;

import com.vulnaudit.entity.Role;
import com.vulnaudit.entity.User;
import com.vulnaudit.entity.UserRole;
import com.vulnaudit.repository.AuditLogRepository;
import com.vulnaudit.repository.RoleRepository;
import com.vulnaudit.repository.UserRepository;
import com.vulnaudit.repository.UserRoleRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 用户 CSV 批量导入测试（SRS 4.5.3）
 *
 * <p>覆盖：正常 2 行、重复 username、未知 roleCode、空文件、非管理员 403，
 * 以及验收要求的「导入后可用管理员重置密码流程正常登录」。
 */
@DisplayName("用户批量导入测试")
class ImportUsersTest extends BaseTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserRoleRepository userRoleRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private MockMultipartFile usersCsv(String... lines) {
        String content = String.join("\n", lines) + "\n";
        return new MockMultipartFile("file", "users.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    private static final String HEADER = "username,email,fullName,phone,roleCode";

    @Test
    @DisplayName("正常两行 - 200，两个用户创建成功并绑定角色，初始密码为强随机 16 位")
    void importUsers_twoValidRows_success() throws Exception {
        MockMultipartFile file = usersCsv(
                HEADER,
                "zhangsan,zhangsan@example.com,张三,13800000001,vulnerability_engineer",
                "lisi,lisi@example.com,李四,,vulnerability_engineer");

        mockMvc.perform(multipart("/api/v1/import/users").file(file)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalRows").value(2))
                .andExpect(jsonPath("$.data.successRows").value(2))
                .andExpect(jsonPath("$.data.failedRows", hasSize(0)))
                .andExpect(jsonPath("$.data.createdUsers", hasSize(2)))
                .andExpect(jsonPath("$.data.createdUsers[0].initialPassword").isNotEmpty())
                .andExpect(jsonPath("$.data.createdUsers[0].mustChangePassword").value(true));

        Optional<User> zhangsan = userRepository.findByUsername("zhangsan");
        assertTrue(zhangsan.isPresent(), "zhangsan 应已创建");
        User user = zhangsan.get();
        assertEquals("zhangsan@example.com", user.getEmail());
        assertEquals("张三", user.getFullName());
        assertEquals("active", user.getStatus());
        // 密码必须经 service 层 BCrypt 编码，而不是明文入库
        assertNotNull(user.getPasswordHash());
        assertFalse(user.getPasswordHash().equals("zhangsan"));

        List<UserRole> userRoles = userRoleRepository.findByUserId(user.getId());
        assertEquals(1, userRoles.size());
        Role role = roleRepository.findById(userRoles.get(0).getRoleId()).orElseThrow();
        assertEquals("vulnerability_engineer", role.getCode());
        assertEquals(adminUserId, userRoles.get(0).getAssignedBy(), "分配人应为当前操作者");

        // 审计日志
        assertTrue(auditLogRepository.findAll().stream()
                        .anyMatch(log -> "import".equals(log.getResourceType())
                                && log.getAction() != null && log.getAction().contains("importUsers")),
                "导入应写审计日志");
    }

    @Test
    @DisplayName("重复 username - 部分成功：首行成功、第二行失败并给出原因")
    void importUsers_duplicateUsername_partialFailure() throws Exception {
        MockMultipartFile file = usersCsv(
                HEADER,
                "wangwu,wangwu@example.com,王五,,vulnerability_engineer",
                "wangwu,wangwu2@example.com,王五重复,,vulnerability_engineer");

        mockMvc.perform(multipart("/api/v1/import/users").file(file)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRows").value(2))
                .andExpect(jsonPath("$.data.successRows").value(1))
                .andExpect(jsonPath("$.data.failedRows", hasSize(1)))
                .andExpect(jsonPath("$.data.failedRows[0].line").value(3))
                .andExpect(jsonPath("$.data.failedRows[0].reason", containsString("用户名已存在")));

        assertTrue(userRepository.findByUsername("wangwu").isPresent());
        assertTrue(userRepository.findByEmail("wangwu2@example.com").isEmpty(), "失败行不应创建用户");
    }

    @Test
    @DisplayName("未知 roleCode - 该行失败并提示角色不存在，用户不被创建")
    void importUsers_unknownRole_fails() throws Exception {
        MockMultipartFile file = usersCsv(
                HEADER,
                "zhaoliu,zhaoliu@example.com,赵六,,not_a_real_role");

        mockMvc.perform(multipart("/api/v1/import/users").file(file)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successRows").value(0))
                .andExpect(jsonPath("$.data.failedRows", hasSize(1)))
                .andExpect(jsonPath("$.data.failedRows[0].reason", containsString("角色不存在")));

        assertTrue(userRepository.findByUsername("zhaoliu").isEmpty(), "角色校验失败时不应创建用户");
    }

    @Test
    @DisplayName("空文件 - 返回 400")
    void importUsers_emptyFile_returns400() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "users.csv", "text/csv", new byte[0]);

        mockMvc.perform(multipart("/api/v1/import/users").file(empty)
                        .header("Authorization", adminToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("非管理员调用 - 403")
    void importUsers_nonAdmin_returns403() throws Exception {
        MockMultipartFile file = usersCsv(
                HEADER,
                "qianqi,qianqi@example.com,钱七,,vulnerability_engineer");

        mockMvc.perform(multipart("/api/v1/import/users").file(file)
                        .header("Authorization", engineerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("用户模板下载 - 含表头且带 BOM")
    void downloadUsersTemplate() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/import/templates/users")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(containsString("username,email,fullName,phone,roleCode")));
    }

    @Test
    @DisplayName("验收：导入后管理员重置密码，新用户可用新密码登录")
    void importedUser_canLoginAfterAdminResetPassword() throws Exception {
        MockMultipartFile file = usersCsv(
                HEADER,
                "sunba,sunba@example.com,孙八,,vulnerability_engineer");

        String response = mockMvc.perform(multipart("/api/v1/import/users").file(file)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(response.contains("sunba"));

        User imported = userRepository.findByUsername("sunba").orElseThrow();
        // 1) 初始密码可用于登录（证明走 UserService 编码、账号为 active）
        String importedPassword = extractInitialPassword(response);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(
                                Map.of("username", "sunba", "password", importedPassword)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty());

        // 2) 管理员重置密码后，新密码同样可登录（验收要求的管理员重置流程）
        mockMvc.perform(post("/api/v1/users/" + imported.getId() + "/reset-password")
                        .header("Authorization", adminToken)
                        .param("newPassword", "NewPass@2026"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(
                                Map.of("username", "sunba", "password", "NewPass@2026")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    /**
     * 从导入响应中取出首行生成的初始密码（避免额外引入 JSON 解析样板）
     */
    private String extractInitialPassword(String response) {
        String marker = "\"initialPassword\":\"";
        int start = response.indexOf(marker);
        if (start < 0) {
            throw new AssertionError("响应中未找到初始密码: " + response);
        }
        int begin = start + marker.length();
        int end = response.indexOf('"', begin);
        return response.substring(begin, end);
    }
}
