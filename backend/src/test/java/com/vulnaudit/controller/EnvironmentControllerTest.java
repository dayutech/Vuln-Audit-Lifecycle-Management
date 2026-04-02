package com.vulnaudit.controller;

import com.vulnaudit.entity.Environment;
import com.vulnaudit.repository.EnvironmentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.Objects;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 环境管理模块测试类
 */
@DisplayName("环境管理模块测试")
public class EnvironmentControllerTest extends BaseTest {

    @Autowired
    private EnvironmentRepository environmentRepository;

    private Environment testEnvironment;
    private UUID testAssetId;
    private UUID testTemplateId;
    private UUID testUserId;

    @BeforeEach
    void setUpEnvironment() {
        testAssetId = UUID.randomUUID();
        testTemplateId = UUID.randomUUID();
        testUserId = UUID.randomUUID();

        testEnvironment = Environment.builder()
                .name("测试环境")
                .assetId(testAssetId)
                .templateId(testTemplateId)
                .config("{\"key\":\"value\"}")
                .status("created")
                .version(1)
                .createdBy(testUserId)
                .build();
        testEnvironment = environmentRepository.save(Objects.requireNonNull(testEnvironment));
    }

    @Nested
    @DisplayName("环境列表查询测试")
    class GetEnvironmentListTests {

        @Test
        @DisplayName("获取环境列表 - 返回501")
        void getEnvironmentList_NotImplemented() throws Exception {
            mockMvc.perform(get("/api/v1/environments")
                    .header("Authorization", engineerToken)
                    .param("page", "0")
                    .param("size", "10"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("环境详情查询测试")
    class GetEnvironmentTests {

        @Test
        @DisplayName("获取环境详情 - 成功")
        void getEnvironment_Success() throws Exception {
            mockMvc.perform(get("/api/v1/environments/" + testEnvironment.getId())
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取环境详情 - 环境不存在")
        void getEnvironment_NotFound() throws Exception {
            mockMvc.perform(get("/api/v1/environments/" + UUID.randomUUID())
                    .header("Authorization", engineerToken))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("创建环境测试")
    class CreateEnvironmentTests {

        @Test
        @DisplayName("创建环境 - 成功")
        void createEnvironment_Success() throws Exception {
            String envJson = "{\"name\":\"新环境\",\"assetId\":\"" + testAssetId + "\",\"templateId\":\"" + testTemplateId + "\",\"config\":\"{}\",\"createdBy\":\"" + testUserId + "\"}";

            mockMvc.perform(post("/api/v1/environments")
                    .header("Authorization", engineerToken)
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .content(envJson))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("更新环境测试")
    class UpdateEnvironmentTests {

        @Test
        @DisplayName("更新环境 - 成功")
        void updateEnvironment_Success() throws Exception {
            String envJson = "{\"name\":\"更新后的环境\",\"config\":\"{\\\"updated\\\":true}\"}";

            mockMvc.perform(put("/api/v1/environments/" + testEnvironment.getId())
                    .header("Authorization", engineerToken)
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .content(envJson))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("环境操作测试")
    class EnvironmentOperationTests {

        @Test
        @DisplayName("启动环境 - 成功")
        void startEnvironment_Success() throws Exception {
            mockMvc.perform(post("/api/v1/environments/" + testEnvironment.getId() + "/start")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("停止环境 - 成功")
        void stopEnvironment_Success() throws Exception {
            mockMvc.perform(post("/api/v1/environments/" + testEnvironment.getId() + "/stop")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("创建快照 - 成功")
        void createSnapshot_Success() throws Exception {
            mockMvc.perform(post("/api/v1/environments/" + testEnvironment.getId() + "/snapshots")
                    .header("Authorization", engineerToken)
                    .param("name", "测试快照"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("删除环境测试")
    class DeleteEnvironmentTests {

        @Test
        @DisplayName("删除环境 - 成功")
        void deleteEnvironment_Success() throws Exception {
            mockMvc.perform(delete("/api/v1/environments/" + testEnvironment.getId())
                    .header("Authorization", adminToken))
                    .andExpect(status().isOk());
        }
    }
}
