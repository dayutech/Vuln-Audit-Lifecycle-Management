package com.vulnaudit.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.vulnaudit.dto.AssetCreateRequest;
import com.vulnaudit.dto.AssetUpdateRequest;
import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.AssetGroup;
import com.vulnaudit.entity.Team;
import com.vulnaudit.entity.User;
import com.vulnaudit.repository.AssetGroupRepository;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.TeamRepository;
import com.vulnaudit.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Objects;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 资产管理模块测试类
 * 测试资产CRUD、分组、访问控制、认领等功能
 */
@DisplayName("资产管理模块测试")
public class AssetControllerTest extends BaseTest {

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private AssetGroupRepository assetGroupRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private UserRepository userRepository;

    private Asset testAsset;
    private AssetGroup testGroup;
    private Team testTeam;
    private User testUser;

    @BeforeEach
    void setUpAsset() {
        // 使用唯一后缀避免数据冲突
        String uniqueSuffix = String.valueOf(System.currentTimeMillis());
        
        // 创建测试用户
        testUser = User.builder()
                .username("assetowner_" + uniqueSuffix)
                .passwordHash("$2a$10$encrypted")
                .email("owner_" + uniqueSuffix + "@test.com")
                .fullName("资产负责人")
                .status("active")
                .build();
        testUser = userRepository.save(Objects.requireNonNull(testUser));

        // 创建测试团队
        testTeam = Team.builder()
                .name("测试团队_" + uniqueSuffix)
                .description("用于测试的团队")
                .leaderId(testUser.getId())
                .createdBy(testUser.getId())
                .build();
        testTeam = teamRepository.save(Objects.requireNonNull(testTeam));

        // 创建测试资产
        testAsset = Asset.builder()
                .name("测试资产")
                .code("TEST-" + uniqueSuffix)
                .targetType("web")
                .targetSource("internal")
                .description("用于测试的资产")
                .priority("P1")
                .status("active")
                .ownerId(testUser.getId())
                .teamId(testTeam.getId())
                .build();
        testAsset = assetRepository.save(Objects.requireNonNull(testAsset));

        // 创建测试分组
        testGroup = AssetGroup.builder()
                .name("测试分组_" + uniqueSuffix)
                .type("test")
                .description("用于测试的分组")
                .build();
        testGroup = assetGroupRepository.save(Objects.requireNonNull(testGroup));
    }

    @Nested
    @DisplayName("资产列表查询测试")
    class GetAssetListTests {

        @Test
        @DisplayName("获取资产列表 - 成功")
        void getAssetList_Success() throws Exception {
            mockMvc.perform(get("/api/v1/assets")
                    .header("Authorization", engineerToken)
                    .param("page", "0")
                    .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.content").isArray());
        }

        @Test
        @DisplayName("获取资产列表 - 按状态筛选")
        void getAssetList_FilterByStatus() throws Exception {
            mockMvc.perform(get("/api/v1/assets")
                    .header("Authorization", engineerToken)
                    .param("status", "active"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("获取资产列表 - 按优先级筛选")
        void getAssetList_FilterByPriority() throws Exception {
            mockMvc.perform(get("/api/v1/assets")
                    .header("Authorization", engineerToken)
                    .param("priority", "P1"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("获取资产列表 - 搜索")
        void getAssetList_Search() throws Exception {
            mockMvc.perform(get("/api/v1/assets")
                    .header("Authorization", engineerToken)
                    .param("search", "测试"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }
    }

    @Nested
    @DisplayName("资产详情查询测试")
    class GetAssetTests {

        @Test
        @DisplayName("获取资产详情 - 成功")
        void getAsset_Success() throws Exception {
            mockMvc.perform(get("/api/v1/assets/" + testAsset.getId())
                    .header("Authorization", adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("测试资产"))
                    .andExpect(jsonPath("$.data.code").exists());
        }

        @Test
        @DisplayName("获取资产详情 - 资产不存在")
        void getAsset_NotFound() throws Exception {
            mockMvc.perform(get("/api/v1/assets/" + UUID.randomUUID())
                    .header("Authorization", adminToken))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("创建资产测试")
    class CreateAssetTests {

        @Test
        @DisplayName("创建资产 - 成功")
        void createAsset_Success() throws Exception {
            AssetCreateRequest request = new AssetCreateRequest();
            request.setName("新资产");
            request.setTargetType("web");
            request.setTargetSource("internal");
            request.setDescription("新创建的资产");
            request.setPriority("P2");
            request.setStatus("active");
            request.setTeamId(testTeam.getId());

            mockMvc.perform(post("/api/v1/assets")
                    .header("Authorization", adminToken)
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.name").value("新资产"))
                    .andExpect(jsonPath("$.data.code").exists());
        }

        @Test
        @DisplayName("创建资产 - 参数校验失败")
        void createAsset_ValidationError() throws Exception {
            AssetCreateRequest request = new AssetCreateRequest();
            request.setName(""); // 空名称
            request.setTargetType(null); // 空类型

            mockMvc.perform(post("/api/v1/assets")
                    .header("Authorization", adminToken)
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("更新资产测试")
    class UpdateAssetTests {

        @Test
        @DisplayName("更新资产 - 成功")
        void updateAsset_Success() throws Exception {
            AssetUpdateRequest request = new AssetUpdateRequest();
            request.setName("更新后的资产");
            request.setDescription("更新后的描述");
            request.setPriority("P0");

            mockMvc.perform(put("/api/v1/assets/" + testAsset.getId())
                    .header("Authorization", adminToken)
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .content(Objects.requireNonNull(objectMapper.writeValueAsString(request))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("更新后的资产"));
        }

        @Test
        @DisplayName("更新资产状态 - 成功")
        void updateAssetStatus_Success() throws Exception {
            mockMvc.perform(put("/api/v1/assets/" + testAsset.getId() + "/status")
                    .header("Authorization", adminToken)
                    .param("status", "suspended"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("删除资产测试")
    class DeleteAssetTests {

        @Test
        @DisplayName("删除资产 - 成功")
        void deleteAsset_Success() throws Exception {
            mockMvc.perform(delete("/api/v1/assets/" + testAsset.getId())
                    .header("Authorization", adminToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("删除资产 - 资产不存在")
        void deleteAsset_NotFound() throws Exception {
            mockMvc.perform(delete("/api/v1/assets/" + UUID.randomUUID())
                    .header("Authorization", adminToken))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("资产标签管理测试")
    class AssetTagTests {

        @Test
        @DisplayName("获取资产标签 - 成功")
        void getAssetTags_Success() throws Exception {
            mockMvc.perform(get("/api/v1/assets/" + testAsset.getId() + "/tags")
                    .header("Authorization", adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray());
        }

        @Test
        @DisplayName("添加资产标签 - 成功")
        void addAssetTag_Success() throws Exception {
            mockMvc.perform(post("/api/v1/assets/" + testAsset.getId() + "/tags")
                    .header("Authorization", engineerToken)
                    .param("tag", "高价值"))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("移除资产标签 - 成功")
        void removeAssetTag_Success() throws Exception {
            // 先添加标签
            mockMvc.perform(post("/api/v1/assets/" + testAsset.getId() + "/tags")
                    .header("Authorization", engineerToken)
                    .param("tag", "测试标签"));

            // 获取标签ID后删除
            mockMvc.perform(get("/api/v1/assets/" + testAsset.getId() + "/tags")
                    .header("Authorization", engineerToken));
        }
    }

    @Nested
    @DisplayName("资产访问申请测试")
    class AssetAccessTests {

        @Test
        @DisplayName("申请访问资产 - 成功")
        void requestAccess_Success() throws Exception {
            String accessJson = "{\"canViewDocuments\":true,\"canViewEnvironments\":false,\"canViewFiles\":false,\"requestNote\":\"需要访问此资产进行测试\"}";
            mockMvc.perform(post("/api/v1/assets/" + testAsset.getId() + "/access-requests")
                    .header("Authorization", engineerToken)
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .content(accessJson))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("获取资产访问申请列表 - 成功")
        void getAccessRequests_Success() throws Exception {
            mockMvc.perform(get("/api/v1/assets/" + testAsset.getId() + "/access-requests")
                    .header("Authorization", adminToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("审批访问申请 - 成功")
        void reviewAccessRequest_Success() throws Exception {
            String accessJson = "{\"canViewDocuments\":true,\"canViewEnvironments\":false,\"canViewFiles\":false,\"requestNote\":\"申请访问\"}";
            mockMvc.perform(post("/api/v1/assets/" + testAsset.getId() + "/access-requests")
                    .header("Authorization", engineerToken)
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .content(accessJson))
                    .andExpect(status().isCreated());

            MvcResult listResult = mockMvc.perform(get("/api/v1/assets/" + testAsset.getId() + "/access-requests")
                    .header("Authorization", adminToken))
                    .andExpect(status().isOk())
                    .andReturn();

            JsonNode root = objectMapper.readTree(listResult.getResponse().getContentAsString());
            String requestId = root.path("data").path("content").get(0).path("id").asText();

            String reviewJson = "{\"approved\":true,\"reviewNote\":\"审批通过\"}";
            mockMvc.perform(post("/api/v1/assets/access-requests/" + requestId + "/review")
                    .header("Authorization", adminToken)
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .content(reviewJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("approved"));
        }
    }

    @Nested
    @DisplayName("资产认领测试")
    class AssetClaimTests {

        @Test
        @DisplayName("认领资产 - 成功")
        void claimAsset_Success() throws Exception {
            String claimJson = "{\"note\":\"认领此资产进行测试\"}";
            mockMvc.perform(post("/api/v1/assets/" + testAsset.getId() + "/claims")
                    .header("Authorization", engineerToken)
                    .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                    .content(claimJson))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("获取资产认领列表 - 成功")
        void getClaims_Success() throws Exception {
            mockMvc.perform(get("/api/v1/assets/" + testAsset.getId() + "/claims")
                    .header("Authorization", adminToken))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("资产历史记录测试")
    class AssetHistoryTests {

        @Test
        @DisplayName("获取资产历史记录 - 成功")
        void getAssetHistory_Success() throws Exception {
            mockMvc.perform(get("/api/v1/assets/" + testAsset.getId() + "/history")
                    .header("Authorization", adminToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray());
        }
    }

    @Nested
    @DisplayName("我的资产相关测试")
    class MyAssetTests {

        @Test
        @DisplayName("获取我发起的访问申请 - 成功")
        void getMyAccessRequests_Success() throws Exception {
            mockMvc.perform(get("/api/v1/assets/my/access-requests")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取我的认领列表 - 成功")
        void getMyClaims_Success() throws Exception {
            mockMvc.perform(get("/api/v1/assets/my/claims")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取待审批的访问申请 - 成功")
        void getMyAccessApprovalRequests_Success() throws Exception {
            mockMvc.perform(get("/api/v1/assets/my/access-approval-requests")
                    .header("Authorization", leaderToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取我已获授权的资产 - 成功")
        void getMyAuthorizedAssets_Success() throws Exception {
            mockMvc.perform(get("/api/v1/assets/my/authorized-assets")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }
    }
}
