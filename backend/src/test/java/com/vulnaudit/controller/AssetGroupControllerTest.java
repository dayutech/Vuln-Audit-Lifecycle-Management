package com.vulnaudit.controller;

import com.vulnaudit.BaseTest;
import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.AssetGroup;
import com.vulnaudit.entity.User;
import com.vulnaudit.repository.AssetGroupRepository;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.UserRepository;
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
 * 资产分组模块测试类
 */
@DisplayName("资产分组模块测试")
public class AssetGroupControllerTest extends BaseTest {

    @Autowired
    private AssetGroupRepository assetGroupRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private UserRepository userRepository;

    private AssetGroup testGroup;
    private Asset testAsset;

    @BeforeEach
    void setUpGroup() {
        // 使用唯一后缀避免数据冲突
        String uniqueSuffix = String.valueOf(System.currentTimeMillis());
        
        User testUser = User.builder()
                .username("groupowner_" + uniqueSuffix)
                .passwordHash("$2a$10$encrypted")
                .email("owner_" + uniqueSuffix + "@group.com")
                .fullName("分组负责人")
                .status("active")
                .build();
        testUser = userRepository.save(testUser);

        testGroup = AssetGroup.builder()
                .name("测试分组_" + uniqueSuffix)
                .type("customer")
                .description("用于测试的分组")
                .build();
        testGroup = assetGroupRepository.save(testGroup);

        testAsset = Asset.builder()
                .name("测试资产")
                .code("GROUP-TEST-" + uniqueSuffix)
                .targetType("web")
                .targetSource("internal")
                .description("用于分组测试的资产")
                .priority("P1")
                .status("active")
                .ownerId(testUser.getId())
                .build();
        testAsset = assetRepository.save(testAsset);
    }

    @Nested
    @DisplayName("分组列表查询测试")
    class GetGroupListTests {

        @Test
        @DisplayName("获取分组列表 - 成功")
        void getGroupList_Success() throws Exception {
            mockMvc.perform(get("/api/v1/asset-groups")
                    .header("Authorization", engineerToken)
                    .param("page", "0")
                    .param("size", "10"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取分组列表 - 按类型筛选")
        void getGroupList_FilterByType() throws Exception {
            mockMvc.perform(get("/api/v1/asset-groups")
                    .header("Authorization", engineerToken)
                    .param("type", "customer"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("分组详情查询测试")
    class GetGroupTests {

        @Test
        @DisplayName("获取分组详情 - 成功")
        void getGroup_Success() throws Exception {
            mockMvc.perform(get("/api/v1/asset-groups/" + testGroup.getId())
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取分组详情 - 分组不存在")
        void getGroup_NotFound() throws Exception {
            mockMvc.perform(get("/api/v1/asset-groups/" + UUID.randomUUID())
                    .header("Authorization", engineerToken))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("创建分组测试")
    class CreateGroupTests {

        @Test
        @DisplayName("创建分组 - 成功")
        void createGroup_Success() throws Exception {
            String groupJson = "{\"name\":\"新分组\",\"type\":\"project\",\"description\":\"新创建的分组\"}";

            mockMvc.perform(post("/api/v1/asset-groups")
                    .header("Authorization", engineerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(groupJson))
                    .andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("更新分组测试")
    class UpdateGroupTests {

        @Test
        @DisplayName("更新分组 - 成功")
        void updateGroup_Success() throws Exception {
            String groupJson = "{\"name\":\"更新后的分组\",\"description\":\"更新后的描述\"}";

            mockMvc.perform(put("/api/v1/asset-groups/" + testGroup.getId())
                    .header("Authorization", engineerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(groupJson))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("分组资产管理测试")
    class GroupAssetTests {

        @Test
        @DisplayName("获取分组中的资产列表 - 成功")
        void getGroupAssets_Success() throws Exception {
            mockMvc.perform(get("/api/v1/asset-groups/" + testGroup.getId() + "/assets")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("添加资产到分组 - 成功")
        void addAssetToGroup_Success() throws Exception {
            mockMvc.perform(post("/api/v1/asset-groups/" + testGroup.getId() + "/assets")
                    .header("Authorization", engineerToken)
                    .param("assetId", testAsset.getId().toString()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("从分组中移除资产 - 成功")
        void removeAssetFromGroup_Success() throws Exception {
            // 先添加资产
            mockMvc.perform(post("/api/v1/asset-groups/" + testGroup.getId() + "/assets")
                    .header("Authorization", engineerToken)
                    .param("assetId", testAsset.getId().toString()));

            // 再移除资产
            mockMvc.perform(delete("/api/v1/asset-groups/" + testGroup.getId() + "/assets/" + testAsset.getId())
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("删除分组测试")
    class DeleteGroupTests {

        @Test
        @DisplayName("删除分组 - 成功")
        void deleteGroup_Success() throws Exception {
            mockMvc.perform(delete("/api/v1/asset-groups/" + testGroup.getId())
                    .header("Authorization", adminToken))
                    .andExpect(status().isOk());
        }
    }
}
