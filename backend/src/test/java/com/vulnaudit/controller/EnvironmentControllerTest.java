package com.vulnaudit.controller;

import com.vulnaudit.entity.Environment;
import com.vulnaudit.entity.AssetVersion;
import com.vulnaudit.entity.EnvironmentSnapshot;
import com.vulnaudit.repository.AssetVersionRepository;
import com.vulnaudit.repository.EnvironmentRepository;
import com.vulnaudit.repository.EnvironmentSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 环境管理模块测试类
 */
@DisplayName("环境管理模块测试")
public class EnvironmentControllerTest extends BaseTest {

    @Autowired
    private EnvironmentRepository environmentRepository;
    @Autowired
    private AssetVersionRepository assetVersionRepository;
    @Autowired
    private EnvironmentSnapshotRepository environmentSnapshotRepository;

    private Environment testEnvironment;
    private UUID testAssetId;
    private UUID testAssetVersionId;
    private UUID testTemplateId;
    private UUID testUserId;

    @BeforeEach
    void setUpEnvironment() {
        testAssetId = UUID.randomUUID();
        testTemplateId = UUID.randomUUID();
        testUserId = UUID.randomUUID();
        AssetVersion assetVersion = assetVersionRepository.save(Objects.requireNonNull(AssetVersion.builder()
                .assetId(testAssetId)
                .versionNo(1)
                .name("v1")
                .isDefault(true)
                .status("active")
                .createdBy(testUserId)
                .build()));
        testAssetVersionId = assetVersion.getId();

        testEnvironment = Environment.builder()
                .name("测试环境")
                .assetId(testAssetId)
                .assetVersionId(testAssetVersionId)
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
        @DisplayName("获取环境详情 - 成功，并返回部署器类型与可用性")
        void getEnvironment_Success() throws Exception {
            mockMvc.perform(get("/api/v1/environments/" + testEnvironment.getId())
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.provisioner").value("noop"))
                    .andExpect(jsonPath("$.data.provisionerAvailable").value(false))
                    .andExpect(jsonPath("$.data.provisionerMessage", containsString("当前部署未启用容器编排")));
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
                    .header("Authorization", leaderToken)
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .content(envJson))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("创建环境 - 未传版本时使用默认版本")
        void createEnvironment_UseDefaultAssetVersionWhenMissing() throws Exception {
            String envJson = "{\"name\":\"默认版本环境\",\"assetId\":\"" + testAssetId + "\",\"templateId\":\"" + testTemplateId + "\",\"config\":\"{}\",\"createdBy\":\"" + testUserId + "\"}";

            mockMvc.perform(post("/api/v1/environments")
                            .header("Authorization", leaderToken)
                            .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                            .content(envJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.assetVersionId").value(testAssetVersionId.toString()));
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
                    .header("Authorization", leaderToken)
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .content(envJson))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("更新环境 - 版本不属于资产时返回404")
        void updateEnvironment_InvalidAssetVersion_ReturnsNotFound() throws Exception {
            String envJson = "{\"assetVersionId\":\"" + UUID.randomUUID() + "\"}";

            mockMvc.perform(put("/api/v1/environments/" + testEnvironment.getId())
                            .header("Authorization", leaderToken)
                            .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                            .content(envJson))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("环境操作测试")
    class EnvironmentOperationTests {

        @Test
        @DisplayName("部署环境 - 未启用容器编排时返回503，状态写failed且给出原因")
        void deployEnvironment_WithoutOrchestration_Returns503() throws Exception {
            mockMvc.perform(post("/api/v1/environments/" + testEnvironment.getId() + "/deploy")
                            .header("Authorization", leaderToken))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("当前部署未启用容器编排"));

            Environment reloaded = environmentRepository.findById(testEnvironment.getId()).orElseThrow();
            assertEquals("failed", reloaded.getStatus());
            assertNotNull(reloaded.getFailReason());
            assertTrue(reloaded.getFailReason().startsWith("当前部署未启用容器编排"));
            assertEquals(null, reloaded.getContainerId());
        }

        @Test
        @DisplayName("启动环境 - 未启用容器编排时返回503，不再伪装 running")
        void startEnvironment_WithoutOrchestration_Returns503() throws Exception {
            mockMvc.perform(post("/api/v1/environments/" + testEnvironment.getId() + "/start")
                            .header("Authorization", leaderToken))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.message").value("当前部署未启用容器编排"));

            Environment reloaded = environmentRepository.findById(testEnvironment.getId()).orElseThrow();
            assertEquals("failed", reloaded.getStatus());
        }

        @Test
        @DisplayName("停止环境 - 未启用容器编排时返回503")
        void stopEnvironment_WithoutOrchestration_Returns503() throws Exception {
            mockMvc.perform(post("/api/v1/environments/" + testEnvironment.getId() + "/stop")
                            .header("Authorization", leaderToken))
                    .andExpect(status().isServiceUnavailable());

            Environment reloaded = environmentRepository.findById(testEnvironment.getId()).orElseThrow();
            assertEquals("failed", reloaded.getStatus());
        }

        @Test
        @DisplayName("创建快照 - createdBy 记录真实当前用户")
        void createSnapshot_Success() throws Exception {
            mockMvc.perform(post("/api/v1/environments/" + testEnvironment.getId() + "/snapshots")
                            .header("Authorization", leaderToken)
                            .param("name", "测试快照"))
                    .andExpect(status().isOk());

            List<EnvironmentSnapshot> snapshots =
                    environmentSnapshotRepository.findByEnvironmentIdOrderByVersionDesc(testEnvironment.getId());
            assertEquals(1, snapshots.size());
            assertEquals(leaderUserId, snapshots.get(0).getCreatedBy());
            assertFalse(snapshots.get(0).getCreatedBy().equals(testUserId));
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
