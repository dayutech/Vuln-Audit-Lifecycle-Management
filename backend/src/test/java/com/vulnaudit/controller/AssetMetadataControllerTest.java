package com.vulnaudit.controller;

import com.vulnaudit.BaseTest;
import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.AssetAppMetadata;
import com.vulnaudit.entity.User;
import com.vulnaudit.repository.AssetAppMetadataRepository;
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
 * 资产元数据模块测试类
 */
@DisplayName("资产元数据模块测试")
public class AssetMetadataControllerTest extends BaseTest {

    @Autowired
    private AssetAppMetadataRepository metadataRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private UserRepository userRepository;

    private Asset testAsset;
    private AssetAppMetadata testMetadata;
    private User testUser;

    @BeforeEach
    void setUpMetadata() {
        // 使用唯一后缀避免数据冲突
        String uniqueSuffix = String.valueOf(System.currentTimeMillis());
        
        testUser = User.builder()
                .username("metadataowner_" + uniqueSuffix)
                .passwordHash("$2a$10$encrypted")
                .email("owner_" + uniqueSuffix + "@metadata.com")
                .fullName("元数据负责人")
                .status("active")
                .build();
        testUser = userRepository.save(testUser);

        testAsset = Asset.builder()
                .name("元数据测试资产")
                .code("META-TEST-" + uniqueSuffix)
                .targetType("web")
                .targetSource("internal")
                .description("用于元数据测试的资产")
                .priority("P1")
                .status("active")
                .ownerId(testUser.getId())
                .build();
        testAsset = assetRepository.save(testAsset);

        testMetadata = AssetAppMetadata.builder()
                .assetId(testAsset.getId())
                .claimedBy(testUser.getId())
                .version(1)
                .systemArchitecture("x86_64")
                .installPath("/opt/app")
                .programmingLanguage("Java")
                .framework("Spring Boot")
                .databaseType("MySQL")
                .servicePorts("8080,8443")
                .build();
        testMetadata = metadataRepository.save(testMetadata);
    }

    @Nested
    @DisplayName("元数据查询测试")
    class GetMetadataTests {

        @Test
        @DisplayName("获取资产元数据 - 成功")
        void getAssetMetadata_Success() throws Exception {
            mockMvc.perform(get("/api/v1/assets/" + testAsset.getId() + "/metadata")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取资产元数据 - 元数据不存在")
        void getAssetMetadata_NotFound() throws Exception {
            String uniqueSuffix = String.valueOf(System.currentTimeMillis());
            Asset newAsset = Asset.builder()
                    .name("无元数据资产")
                    .code("NO-META-" + uniqueSuffix)
                    .targetType("web")
                    .targetSource("internal")
                    .description("无元数据资产")
                    .priority("P2")
                    .status("active")
                    .ownerId(testUser.getId())
                    .build();
            newAsset = assetRepository.save(newAsset);

            mockMvc.perform(get("/api/v1/assets/" + newAsset.getId() + "/metadata")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取元数据详情 - 成功")
        void getMetadataById_Success() throws Exception {
            mockMvc.perform(get("/api/v1/assets/" + testAsset.getId() + "/metadata/" + testMetadata.getId())
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取元数据详情 - 元数据不存在")
        void getMetadataById_NotFound() throws Exception {
            mockMvc.perform(get("/api/v1/assets/" + testAsset.getId() + "/metadata/" + UUID.randomUUID())
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("创建元数据测试")
    class CreateMetadataTests {

        @Test
        @DisplayName("创建元数据 - 成功")
        void createMetadata_Success() throws Exception {
            Asset newAsset = Asset.builder()
                    .name("新元数据资产")
                    .code("NEW-META-001")
                    .targetType("web")
                    .targetSource("internal")
                    .description("新元数据资产")
                    .priority("P2")
                    .status("active")
                    .ownerId(testUser.getId())
                    .build();
            newAsset = assetRepository.save(newAsset);

            String metadataJson = "{\"systemArchitecture\":\"arm64\",\"installPath\":\"/usr/local/app\",\"programmingLanguage\":\"Python\",\"framework\":\"Django\",\"databaseType\":\"PostgreSQL\",\"servicePorts\":\"8000\"}";

            mockMvc.perform(post("/api/v1/assets/" + newAsset.getId() + "/metadata")
                    .header("Authorization", engineerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(metadataJson))
                    .andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("更新元数据测试")
    class UpdateMetadataTests {

        @Test
        @DisplayName("更新元数据 - 成功")
        void updateMetadata_Success() throws Exception {
            String metadataJson = "{\"systemArchitecture\":\"x86_64\",\"installPath\":\"/opt/app/updated\",\"programmingLanguage\":\"Java\",\"framework\":\"Spring Boot 3.0\"}";

            mockMvc.perform(put("/api/v1/assets/" + testAsset.getId() + "/metadata/" + testMetadata.getId())
                    .header("Authorization", engineerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(metadataJson)
                    .param("changeReason", "框架版本升级"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("更新元数据 - 元数据不存在")
        void updateMetadata_NotFound() throws Exception {
            String metadataJson = "{\"systemArchitecture\":\"x86_64\"}";

            mockMvc.perform(put("/api/v1/assets/" + testAsset.getId() + "/metadata/" + UUID.randomUUID())
                    .header("Authorization", engineerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(metadataJson))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("元数据历史测试")
    class MetadataHistoryTests {

        @Test
        @DisplayName("获取元数据历史 - 成功")
        void getMetadataHistory_Success() throws Exception {
            mockMvc.perform(get("/api/v1/assets/" + testAsset.getId() + "/metadata/" + testMetadata.getId() + "/history")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取历史版本 - 成功")
        void getMetadataVersion_Success() throws Exception {
            mockMvc.perform(get("/api/v1/assets/" + testAsset.getId() + "/metadata/" + testMetadata.getId() + "/history/" + UUID.randomUUID())
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("删除元数据测试")
    class DeleteMetadataTests {

        @Test
        @DisplayName("删除元数据 - 成功")
        void deleteMetadata_Success() throws Exception {
            mockMvc.perform(delete("/api/v1/assets/" + testAsset.getId() + "/metadata/" + testMetadata.getId())
                    .header("Authorization", adminToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("删除元数据 - 元数据不存在")
        void deleteMetadata_NotFound() throws Exception {
            mockMvc.perform(delete("/api/v1/assets/" + testAsset.getId() + "/metadata/" + UUID.randomUUID())
                    .header("Authorization", adminToken))
                    .andExpect(status().isOk());
        }
    }
}
