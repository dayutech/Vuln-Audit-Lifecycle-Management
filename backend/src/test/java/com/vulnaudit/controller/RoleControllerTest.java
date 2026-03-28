package com.vulnaudit.controller;

import com.vulnaudit.entity.Role;
import com.vulnaudit.entity.Permission;
import com.vulnaudit.repository.RoleRepository;
import com.vulnaudit.repository.PermissionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 角色管理模块测试类
 */
@DisplayName("角色管理模块测试")
public class RoleControllerTest extends BaseTest {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    private Role testRole;
    private Permission testPermission;

    @BeforeEach
    void setUpRole() {
        // 使用唯一后缀避免数据冲突
        String uniqueSuffix = String.valueOf(System.currentTimeMillis());
        
        testRole = Role.builder()
                .name("测试角色_" + uniqueSuffix)
                .code("test_role_" + uniqueSuffix)
                .description("用于测试的角色")
                .isSystem(false)
                .build();
        testRole = roleRepository.save(testRole);

        testPermission = Permission.builder()
                .name("测试权限_" + uniqueSuffix)
                .code("test:permission_" + uniqueSuffix)
                .apiPath("/api/v1/test")
                .httpMethod("GET")
                .description("测试用权限")
                .build();
        testPermission = permissionRepository.save(testPermission);
    }

    @Nested
    @DisplayName("角色列表查询测试")
    class GetRoleListTests {

        @Test
        @DisplayName("获取角色列表 - 成功")
        void getRoleList_Success() throws Exception {
            mockMvc.perform(get("/api/v1/roles")
                    .header("Authorization", adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("角色详情查询测试")
    class GetRoleTests {

        @Test
        @DisplayName("获取角色详情 - 成功")
        void getRole_Success() throws Exception {
            mockMvc.perform(get("/api/v1/roles/" + testRole.getId())
                    .header("Authorization", adminToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取角色详情 - 角色不存在")
        void getRole_NotFound() throws Exception {
            mockMvc.perform(get("/api/v1/roles/" + UUID.randomUUID())
                    .header("Authorization", adminToken))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("创建角色测试")
    class CreateRoleTests {

        @Test
        @DisplayName("创建角色 - 成功")
        void createRole_Success() throws Exception {
            String roleJson = "{\"name\":\"新角色\",\"code\":\"new_role\",\"description\":\"新创建的角色\"}";

            mockMvc.perform(post("/api/v1/roles")
                    .header("Authorization", adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(roleJson))
                    .andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("更新角色测试")
    class UpdateRoleTests {

        @Test
        @DisplayName("更新角色 - 成功")
        void updateRole_Success() throws Exception {
            String roleJson = "{\"name\":\"更新后的角色\",\"description\":\"更新后的描述\"}";

            mockMvc.perform(put("/api/v1/roles/" + testRole.getId())
                    .header("Authorization", adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(roleJson))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("删除角色测试")
    class DeleteRoleTests {

        @Test
        @DisplayName("删除角色 - 成功")
        void deleteRole_Success() throws Exception {
            mockMvc.perform(delete("/api/v1/roles/" + testRole.getId())
                    .header("Authorization", adminToken))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("角色权限管理测试")
    class RolePermissionTests {

        @Test
        @DisplayName("为角色分配权限 - 成功")
        void assignPermission_Success() throws Exception {
            mockMvc.perform(post("/api/v1/roles/" + testRole.getId() + "/permissions")
                    .header("Authorization", adminToken)
                    .contentType("application/json")
                    .content("[\"" + testPermission.getId() + "\"]"))
                    .andExpect(status().isOk());
        }
    }
}
