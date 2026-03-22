package com.vulnaudit.controller;

import com.vulnaudit.BaseTest;
import com.vulnaudit.entity.Permission;
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
 * 权限管理模块测试类
 */
@DisplayName("权限管理模块测试")
public class PermissionControllerTest extends BaseTest {

    @Autowired
    private PermissionRepository permissionRepository;

    private Permission testPermission;

    @BeforeEach
    void setUpPermission() {
        // 使用唯一后缀避免数据冲突
        String uniqueSuffix = String.valueOf(System.currentTimeMillis());
        
        testPermission = Permission.builder()
                .name("测试权限_" + uniqueSuffix)
                .code("test:permission_" + uniqueSuffix)
                .apiPath("/api/v1/test")
                .httpMethod("GET")
                .description("用于测试的权限")
                .build();
        testPermission = permissionRepository.save(testPermission);
    }

    @Nested
    @DisplayName("权限列表查询测试")
    class GetPermissionListTests {

        @Test
        @DisplayName("获取权限列表 - 成功")
        void getPermissionList_Success() throws Exception {
            mockMvc.perform(get("/api/v1/permissions")
                    .header("Authorization", adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("获取权限列表 - 按类型筛选")
        void getPermissionList_FilterByType() throws Exception {
            mockMvc.perform(get("/api/v1/permissions")
                    .header("Authorization", adminToken)
                    .param("type", "api"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("权限详情查询测试")
    class GetPermissionTests {

        @Test
        @DisplayName("获取权限详情 - 成功")
        void getPermission_Success() throws Exception {
            mockMvc.perform(get("/api/v1/permissions/" + testPermission.getId())
                    .header("Authorization", adminToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取权限详情 - 权限不存在")
        void getPermission_NotFound() throws Exception {
            mockMvc.perform(get("/api/v1/permissions/" + UUID.randomUUID())
                    .header("Authorization", adminToken))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("创建权限测试")
    class CreatePermissionTests {

        @Test
        @DisplayName("创建权限 - 成功")
        void createPermission_Success() throws Exception {
            String permissionJson = "{\"name\":\"新权限\",\"code\":\"new:permission\",\"apiPath\":\"/api/v1/new\",\"httpMethod\":\"POST\",\"description\":\"新创建的权限\"}";

            mockMvc.perform(post("/api/v1/permissions")
                    .header("Authorization", adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(permissionJson))
                    .andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("更新权限测试")
    class UpdatePermissionTests {

        @Test
        @DisplayName("更新权限 - 成功")
        void updatePermission_Success() throws Exception {
            String permissionJson = "{\"name\":\"更新后的权限\",\"description\":\"更新后的描述\"}";

            mockMvc.perform(put("/api/v1/permissions/" + testPermission.getId())
                    .header("Authorization", adminToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(permissionJson))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("删除权限测试")
    class DeletePermissionTests {

        @Test
        @DisplayName("删除权限 - 成功")
        void deletePermission_Success() throws Exception {
            mockMvc.perform(delete("/api/v1/permissions/" + testPermission.getId())
                    .header("Authorization", adminToken))
                    .andExpect(status().isOk());
        }
    }
}
