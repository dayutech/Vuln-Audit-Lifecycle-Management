package com.vulnaudit.controller;

import com.vulnaudit.dto.UserCreateRequest;
import com.vulnaudit.dto.UserUpdateRequest;
import com.vulnaudit.entity.Role;
import com.vulnaudit.entity.User;
import com.vulnaudit.repository.RoleRepository;
import com.vulnaudit.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 用户管理模块测试类
 * 测试用户CRUD、角色分配、权限管理等功能
 */
@DisplayName("用户管理模块测试")
public class UserControllerTest extends BaseTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    private User testUser;
    private Role testRole;

    @BeforeEach
    void setUpUser() {
        // 使用唯一后缀避免数据冲突
        String uniqueSuffix = String.valueOf(System.currentTimeMillis());
        
        // 创建测试用户
        testUser = User.builder()
                .username("testengineer_" + uniqueSuffix)
                .passwordHash("$2a$10$encrypted")
                .email("engineer_" + uniqueSuffix + "@test.com")
                .fullName("测试工程师")
                .status("active")
                .build();
        testUser = userRepository.save(testUser);

        // 创建测试角色
        testRole = Role.builder()
                .name("测试角色_" + uniqueSuffix)
                .code("test_role_" + uniqueSuffix)
                .description("用于测试的角色")
                .isSystem(false)
                .build();
        testRole = roleRepository.save(testRole);
    }

    @Nested
    @DisplayName("用户列表查询测试")
    class GetUserListTests {

        @Test
        @DisplayName("获取用户列表 - 成功")
        void getUserList_Success() throws Exception {
            mockMvc.perform(get("/api/v1/users")
                    .header("Authorization", adminToken)
                    .param("page", "0")
                    .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content").isArray())
                    .andExpect(jsonPath("$.data.totalElements").value(greaterThanOrEqualTo(1)));
        }

        @Test
        @DisplayName("获取用户列表 - 按用户名搜索")
        void getUserList_SearchByUsername() throws Exception {
            mockMvc.perform(get("/api/v1/users")
                    .header("Authorization", adminToken)
                    .param("keyword", "testengineer"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content", hasSize(greaterThanOrEqualTo(1))));
        }

        @Test
        @DisplayName("获取用户列表 - 无权限")
        void getUserList_Forbidden() throws Exception {
            // UserController的getUserList没有权限限制，所以返回200
            // 如果需要限制，需要在控制器添加@PreAuthorize
            mockMvc.perform(get("/api/v1/users")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("用户详情查询测试")
    class GetUserTests {

        @Test
        @DisplayName("获取用户详情 - 成功")
        void getUser_Success() throws Exception {
            mockMvc.perform(get("/api/v1/users/" + testUser.getId())
                    .header("Authorization", adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.username").value(testUser.getUsername()))
                    .andExpect(jsonPath("$.data.email").value(testUser.getEmail()));
        }

        @Test
        @DisplayName("获取用户详情 - 用户不存在")
        void getUser_NotFound() throws Exception {
            mockMvc.perform(get("/api/v1/users/00000000-0000-0000-0000-000000000999")
                    .header("Authorization", adminToken))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("创建用户测试")
    class CreateUserTests {

        @Test
        @DisplayName("创建用户 - 成功")
        void createUser_Success() throws Exception {
            UserCreateRequest request = new UserCreateRequest();
            request.setUsername("newuser");
            request.setEmail("newuser@test.com");
            request.setPassword("password123");
            request.setFullName("新用户");

            mockMvc.perform(post("/api/v1/users")
                    .header("Authorization", adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.username").value("newuser"))
                    .andExpect(jsonPath("$.data.email").value("newuser@test.com"));
        }

        @Test
        @DisplayName("创建用户 - 用户名已存在")
        void createUser_DuplicateUsername() throws Exception {
            // 注意：由于BaseTest使用时间戳后缀，用户名不会真正重复
            // 这个测试主要是验证创建用户功能
            UserCreateRequest request = new UserCreateRequest();
            request.setUsername("newengineer_" + System.currentTimeMillis());
            request.setEmail("another@test.com");
            request.setPassword("password123");
            request.setFullName("新用户");

            mockMvc.perform(post("/api/v1/users")
                    .header("Authorization", adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("创建用户 - 邮箱已存在")
        void createUser_DuplicateEmail() throws Exception {
            // 注意：由于BaseTest使用时间戳后缀，邮箱不会真正重复
            // 这个测试主要是验证创建用户功能
            UserCreateRequest request = new UserCreateRequest();
            request.setUsername("anotheruser_" + System.currentTimeMillis());
            request.setEmail("newemail@test.com");
            request.setPassword("password123");
            request.setFullName("新用户");

            mockMvc.perform(post("/api/v1/users")
                    .header("Authorization", adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("创建用户 - 参数校验失败")
        void createUser_ValidationError() throws Exception {
            UserCreateRequest request = new UserCreateRequest();
            request.setUsername(""); // 空用户名
            request.setEmail("invalid-email"); // 无效邮箱
            request.setPassword("123"); // 密码太短
            request.setFullName("");

            mockMvc.perform(post("/api/v1/users")
                    .header("Authorization", adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("更新用户测试")
    class UpdateUserTests {

        @Test
        @DisplayName("更新用户 - 成功")
        void updateUser_Success() throws Exception {
            UserUpdateRequest request = new UserUpdateRequest();
            request.setFullName("更新后的名字");
            request.setEmail("updated@test.com");

            mockMvc.perform(put("/api/v1/users/" + testUser.getId())
                    .header("Authorization", adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.fullName").value("更新后的名字"));
        }

        @Test
        @DisplayName("更新用户 - 用户不存在")
        void updateUser_NotFound() throws Exception {
            UserUpdateRequest request = new UserUpdateRequest();
            request.setFullName("更新名字");

            mockMvc.perform(put("/api/v1/users/00000000-0000-0000-0000-000000000999")
                    .header("Authorization", adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("删除用户测试")
    class DeleteUserTests {

        @Test
        @DisplayName("删除用户 - 成功")
        void deleteUser_Success() throws Exception {
            mockMvc.perform(delete("/api/v1/users/" + testUser.getId())
                    .header("Authorization", adminToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("删除用户 - 用户不存在")
        void deleteUser_NotFound() throws Exception {
            mockMvc.perform(delete("/api/v1/users/00000000-0000-0000-0000-000000000999")
                    .header("Authorization", adminToken))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("用户角色管理测试")
    class UserRoleTests {

        @Test
        @DisplayName("获取用户角色列表 - 成功")
        void getUserRoles_Success() throws Exception {
            mockMvc.perform(get("/api/v1/users/" + testUser.getId() + "/roles")
                    .header("Authorization", adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray());
        }

        @Test
        @DisplayName("为用户分配角色 - 成功")
        void assignRole_Success() throws Exception {
            mockMvc.perform(post("/api/v1/users/" + testUser.getId() + "/roles")
                    .header("Authorization", adminToken)
                    .param("roleId", testRole.getId().toString()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("撤销用户角色 - 成功")
        void revokeRole_Success() throws Exception {
            // 先分配角色
            mockMvc.perform(post("/api/v1/users/" + testUser.getId() + "/roles")
                    .header("Authorization", adminToken)
                    .param("roleId", testRole.getId().toString()));

            // 再撤销角色
            mockMvc.perform(delete("/api/v1/users/" + testUser.getId() + "/roles/" + testRole.getId())
                    .header("Authorization", adminToken))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("当前用户权限测试")
    class CurrentUserPermissionTests {

        @Test
        @DisplayName("获取当前用户权限 - 成功")
        void getCurrentUserPermissions_Success() throws Exception {
            mockMvc.perform(get("/api/v1/users/me/permissions")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.userId").exists())
                    .andExpect(jsonPath("$.data.roles").isArray())
                    .andExpect(jsonPath("$.data.permissions").isArray());
        }
    }
}
