package com.vulnaudit.controller;

import com.vulnaudit.entity.AssetVersion;
import com.vulnaudit.entity.metadata.AssetMetadataBase;
import com.vulnaudit.repository.AssetVersionRepository;
import com.vulnaudit.repository.metadata.AssetMetadataBaseRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.Objects;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("资产版本管理模块测试")
public class AssetVersionControllerTest extends BaseTest {

    @Autowired
    private AssetVersionRepository assetVersionRepository;

    @Autowired
    private AssetMetadataBaseRepository assetMetadataBaseRepository;

    @Nested
    @DisplayName("资产版本列表查询测试")
    class ListVersionsTests {

        @Test
        @DisplayName("获取资产版本列表 - 成功")
        void listVersions_Success() throws Exception {
            UUID assetId = UUID.randomUUID();
            assetVersionRepository.save(Objects.requireNonNull(AssetVersion.builder()
                    .assetId(assetId)
                    .versionNo(1)
                    .name("v1")
                    .isDefault(true)
                    .status("active")
                    .createdBy(engineerUserId)
                    .build()));
            assetVersionRepository.save(Objects.requireNonNull(AssetVersion.builder()
                    .assetId(assetId)
                    .versionNo(2)
                    .name("v2")
                    .isDefault(false)
                    .status("active")
                    .createdBy(engineerUserId)
                    .build()));

            mockMvc.perform(get("/api/v1/assets/" + assetId + "/versions")
                            .header("Authorization", engineerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2))
                    .andExpect(jsonPath("$.data[0].versionNo").value(2))
                    .andExpect(jsonPath("$.data[1].versionNo").value(1));
        }
    }

    @Nested
    @DisplayName("默认版本查询测试")
    class GetDefaultVersionTests {

        @Test
        @DisplayName("获取默认版本 - 存在默认版本")
        void getDefaultVersion_WhenExists() throws Exception {
            UUID assetId = UUID.randomUUID();
            assetVersionRepository.save(Objects.requireNonNull(AssetVersion.builder()
                    .assetId(assetId)
                    .versionNo(1)
                    .name("v1")
                    .isDefault(false)
                    .status("active")
                    .createdBy(engineerUserId)
                    .build()));
            AssetVersion defaultVersion = assetVersionRepository.save(Objects.requireNonNull(AssetVersion.builder()
                    .assetId(assetId)
                    .versionNo(2)
                    .name("v2")
                    .isDefault(true)
                    .status("active")
                    .createdBy(engineerUserId)
                    .build()));

            mockMvc.perform(get("/api/v1/assets/" + assetId + "/versions/default")
                            .header("Authorization", engineerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(defaultVersion.getId().toString()))
                    .andExpect(jsonPath("$.data.versionNo").value(2))
                    .andExpect(jsonPath("$.data.isDefault").value(true));
        }

        @Test
        @DisplayName("获取默认版本 - 不存在默认版本时回退最新版本")
        void getDefaultVersion_FallbackToLatest() throws Exception {
            UUID assetId = UUID.randomUUID();
            assetVersionRepository.save(Objects.requireNonNull(AssetVersion.builder()
                    .assetId(assetId)
                    .versionNo(1)
                    .name("v1")
                    .isDefault(false)
                    .status("active")
                    .createdBy(engineerUserId)
                    .build()));
            assetVersionRepository.save(Objects.requireNonNull(AssetVersion.builder()
                    .assetId(assetId)
                    .versionNo(3)
                    .name("v3")
                    .isDefault(false)
                    .status("active")
                    .createdBy(engineerUserId)
                    .build()));

            mockMvc.perform(get("/api/v1/assets/" + assetId + "/versions/default")
                            .header("Authorization", engineerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.versionNo").value(3));
        }
    }

    @Nested
    @DisplayName("创建资产版本测试")
    class CreateVersionTests {

        @Test
        @DisplayName("创建资产版本 - 首个版本自动设为默认")
        void createVersion_FirstOneShouldDefault() throws Exception {
            UUID assetId = UUID.randomUUID();
            String payload = "{\"name\":\"初始版本\",\"description\":\"首版\"}";

            mockMvc.perform(post("/api/v1/assets/" + assetId + "/versions")
                            .header("Authorization", engineerToken)
                            .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                            .content(payload))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.versionNo").value(1))
                    .andExpect(jsonPath("$.data.isDefault").value(true))
                    .andExpect(jsonPath("$.data.name").value("初始版本"));
        }

        @Test
        @DisplayName("创建资产版本 - 指定 setDefault=true 切换默认")
        void createVersion_SetDefaultShouldSwitch() throws Exception {
            UUID assetId = UUID.randomUUID();
            assetVersionRepository.save(Objects.requireNonNull(AssetVersion.builder()
                    .assetId(assetId)
                    .versionNo(1)
                    .name("v1")
                    .isDefault(true)
                    .status("active")
                    .createdBy(engineerUserId)
                    .build()));
            String payload = "{\"name\":\"v2\",\"description\":\"第二版\"}";

            mockMvc.perform(post("/api/v1/assets/" + assetId + "/versions")
                            .param("setDefault", "true")
                            .header("Authorization", engineerToken)
                            .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                            .content(payload))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.versionNo").value(2))
                    .andExpect(jsonPath("$.data.isDefault").value(true));

            mockMvc.perform(get("/api/v1/assets/" + assetId + "/versions/default")
                            .header("Authorization", engineerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.versionNo").value(2));
        }

        @Test
        @DisplayName("创建资产版本 - 带入指定版本产品概览")
        void createVersion_InheritBaseOverview_Success() throws Exception {
            UUID assetId = UUID.randomUUID();
            AssetVersion sourceVersion = assetVersionRepository.save(Objects.requireNonNull(AssetVersion.builder()
                    .assetId(assetId)
                    .versionNo(1)
                    .name("v1")
                    .isDefault(true)
                    .isLatest(true)
                    .status("active")
                    .createdBy(engineerUserId)
                    .build()));
            AssetMetadataBase sourceBase = new AssetMetadataBase();
            sourceBase.setAssetId(assetId);
            sourceBase.setAssetVersionId(sourceVersion.getId());
            sourceBase.setClaimedBy(engineerUserId);
            sourceBase.setVersion(1);
            sourceBase.setSystemArchitecture("x86_64");
            sourceBase.setRuntimePlatform("linux");
            sourceBase.setDeploymentMethod("k8s");
            sourceBase.setProgrammingLanguagesJson("[{\"language\":\"Java\"}]");
            sourceBase.setProgrammingLanguage("Java");
            sourceBase.setFramework("Spring Boot");
            assetMetadataBaseRepository.save(Objects.requireNonNull(sourceBase));

            String payload = "{\"name\":\"v2\",\"inheritBaseOverview\":true,\"inheritFromVersionId\":\"" + sourceVersion.getId() + "\"}";

            mockMvc.perform(post("/api/v1/assets/" + assetId + "/versions")
                            .header("Authorization", engineerToken)
                            .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                            .content(payload))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.versionNo").value(2));

            AssetVersion targetVersion = assetVersionRepository.findFirstByAssetIdOrderByVersionNoDesc(assetId).orElseThrow();
            AssetMetadataBase targetBase = assetMetadataBaseRepository.findByAssetIdAndAssetVersionId(assetId, targetVersion.getId()).orElseThrow();
            org.junit.jupiter.api.Assertions.assertEquals("x86_64", targetBase.getSystemArchitecture());
            org.junit.jupiter.api.Assertions.assertEquals("linux", targetBase.getRuntimePlatform());
            org.junit.jupiter.api.Assertions.assertEquals(sourceVersion.getId(), targetBase.getCopiedFromAssetVersionId());
        }
    }

    @Nested
    @DisplayName("默认版本切换测试")
    class SetDefaultVersionTests {

        @Test
        @DisplayName("切换默认版本 - 成功")
        void setDefaultVersion_Success() throws Exception {
            UUID assetId = UUID.randomUUID();
            assetVersionRepository.save(Objects.requireNonNull(AssetVersion.builder()
                    .assetId(assetId)
                    .versionNo(1)
                    .name("v1")
                    .isDefault(true)
                    .status("active")
                    .createdBy(engineerUserId)
                    .build()));
            AssetVersion v2 = assetVersionRepository.save(Objects.requireNonNull(AssetVersion.builder()
                    .assetId(assetId)
                    .versionNo(2)
                    .name("v2")
                    .isDefault(false)
                    .status("active")
                    .createdBy(engineerUserId)
                    .build()));

            mockMvc.perform(put("/api/v1/assets/" + assetId + "/versions/" + v2.getId() + "/default")
                            .header("Authorization", engineerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(v2.getId().toString()))
                    .andExpect(jsonPath("$.data.isDefault").value(true));

            mockMvc.perform(get("/api/v1/assets/" + assetId + "/versions/default")
                            .header("Authorization", engineerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(v2.getId().toString()));
        }

        @Test
        @DisplayName("切换默认版本 - 版本不属于资产")
        void setDefaultVersion_NotFoundWhenVersionNotBelongs() throws Exception {
            UUID assetId = UUID.randomUUID();
            UUID anotherAssetId = UUID.randomUUID();
            assetVersionRepository.save(Objects.requireNonNull(AssetVersion.builder()
                    .assetId(assetId)
                    .versionNo(1)
                    .name("v1")
                    .isDefault(true)
                    .status("active")
                    .createdBy(engineerUserId)
                    .build()));
            AssetVersion anotherAssetVersion = assetVersionRepository.save(Objects.requireNonNull(AssetVersion.builder()
                    .assetId(anotherAssetId)
                    .versionNo(1)
                    .name("other-v1")
                    .isDefault(true)
                    .status("active")
                    .createdBy(engineerUserId)
                    .build()));

            mockMvc.perform(put("/api/v1/assets/" + assetId + "/versions/" + anotherAssetVersion.getId() + "/default")
                            .header("Authorization", engineerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("版本不存在"));
        }
    }
}
