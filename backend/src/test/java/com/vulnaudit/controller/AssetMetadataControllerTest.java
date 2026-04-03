package com.vulnaudit.controller;

import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.AssetVersion;
import com.vulnaudit.entity.User;
import com.vulnaudit.entity.metadata.AssetMetadataBase;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.AssetVersionRepository;
import com.vulnaudit.repository.UserRepository;
import com.vulnaudit.repository.metadata.AssetMetadataBaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Objects;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 资产元数据控制器测试类
 */
@DisplayName("资产元数据控制器测试")
public class AssetMetadataControllerTest extends BaseTest {

    @Autowired
    private AssetMetadataBaseRepository baseRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private AssetVersionRepository assetVersionRepository;

    @Autowired
    private UserRepository userRepository;

    private Asset testAsset;
    private User testUser;
    private AssetMetadataBase testMetadataBase;

    @BeforeEach
    void setUpMetadataData() {
        String uniqueSuffix = String.valueOf(System.currentTimeMillis());

        testUser = User.builder()
                .username("metadataowner_" + uniqueSuffix)
                .passwordHash("$2a$10$encrypted")
                .email("owner_" + uniqueSuffix + "@metadata.com")
                .fullName("元数据负责人")
                .status("active")
                .build();
        testUser = userRepository.save(Objects.requireNonNull(testUser));

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
        testAsset = assetRepository.save(Objects.requireNonNull(testAsset));

        testMetadataBase = new AssetMetadataBase();
        testMetadataBase.setAssetId(testAsset.getId());
        testMetadataBase.setClaimedBy(testUser.getId());
        testMetadataBase.setVersion(1);
        testMetadataBase.setSystemArchitecture("x86_64");
        testMetadataBase.setProgrammingLanguage("Java");
        testMetadataBase.setFramework("Spring Boot");
        testMetadataBase = baseRepository.save(Objects.requireNonNull(testMetadataBase));
    }

    @Nested
    @DisplayName("基础元数据测试")
    class BaseMetadataTests {

        @Test
        @DisplayName("获取基础元数据 - 成功")
        void getBaseMetadata_Success() throws Exception {
            mockMvc.perform(get("/api/v1/assets/" + testAsset.getId() + "/metadata/base")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.systemArchitecture").value("x86_64"));
        }

        @Test
        @DisplayName("更新基础元数据 - 成功")
        void updateBaseMetadata_Success() throws Exception {
            String metadataJson = "{\"systemArchitecture\":\"arm64\",\"runtimePlatform\":\"Docker\",\"deploymentMethod\":\"k8s\"}";

            mockMvc.perform(put("/api/v1/assets/" + testAsset.getId() + "/metadata/base")
                    .header("Authorization", engineerToken)
                    .contentType("application/json")
                    .content(metadataJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.systemArchitecture").value("arm64"))
                    .andExpect(jsonPath("$.data.runtimePlatform").value("Docker"));
        }
    }

    @Nested
    @DisplayName("网络元数据测试")
    class NetworkMetadataTests {

        @Test
        @DisplayName("获取网络元数据 - 不存在时返回null")
        void getNetworkMetadata_NotFound() throws Exception {
            mockMvc.perform(get("/api/v1/assets/" + testAsset.getId() + "/metadata/network")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("更新网络元数据 - 成功")
        void updateNetworkMetadata_Success() throws Exception {
            String metadataJson = "{\"servicePorts\":\"8080,8443\",\"apiEndpoints\":\"/api/v1/test\"}";

            mockMvc.perform(put("/api/v1/assets/" + testAsset.getId() + "/metadata/network")
                    .header("Authorization", engineerToken)
                    .contentType("application/json")
                    .content(metadataJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.servicePorts").value("8080,8443"))
                    .andExpect(jsonPath("$.data.apiEndpoints").value("/api/v1/test"));
        }
    }

    @Nested
    @DisplayName("账号元数据测试")
    class AccountMetadataTests {

        @Test
        @DisplayName("更新账号元数据 - 多账号格式化")
        void updateAccountMetadata_WithMultipleAccounts() throws Exception {
            String metadataJson = "{\"accountCredentialsJson\":\"[{\\\"usageNote\\\":\\\"SSH登录\\\",\\\"username\\\":\\\"root\\\",\\\"password\\\":\\\"root123\\\"},{\\\"usageNote\\\":\\\"Web管理员\\\",\\\"username\\\":\\\"admin\\\",\\\"password\\\":\\\"admin123\\\"}]\"}";

            mockMvc.perform(put("/api/v1/assets/" + testAsset.getId() + "/metadata/account")
                    .header("Authorization", engineerToken)
                    .contentType("application/json")
                    .content(metadataJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accountCredentialsJson").exists());
        }
    }

    @Nested
    @DisplayName("环境与附加元数据测试")
    class EnvAndExtraMetadataTests {

        @Test
        @DisplayName("更新环境元数据 - 成功")
        void updateEnvMetadata_Success() throws Exception {
            String metadataJson = "{\"installPath\":\"/opt/app\",\"startupScriptPath\":\"[\\\"/opt/app/start.sh\\\"]\",\"configFilePath\":\"[\\\"/etc/app/config.yml\\\"]\",\"startupCommand\":\"./start.sh\",\"databaseType\":\"MySQL\",\"databaseConnection\":\"jdbc:mysql://localhost:3306/demo\"}";

            mockMvc.perform(put("/api/v1/assets/" + testAsset.getId() + "/metadata/env")
                    .header("Authorization", engineerToken)
                    .contentType("application/json")
                    .content(metadataJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.installPath").value("/opt/app"))
                    .andExpect(jsonPath("$.data.databaseType").value("MySQL"));
        }

        @Test
        @DisplayName("更新附加元数据 - 成功")
        void updateExtraMetadata_Success() throws Exception {
            String metadataJson = "{\"specialNotes\":\"该资产依赖内网DNS与白名单策略\"}";

            mockMvc.perform(put("/api/v1/assets/" + testAsset.getId() + "/metadata/extra")
                    .header("Authorization", engineerToken)
                    .contentType("application/json")
                    .content(metadataJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.specialNotes").value("该资产依赖内网DNS与白名单策略"));
        }
    }

    @Nested
    @DisplayName("版本元数据测试")
    class VersionedMetadataTests {

        @Test
        @DisplayName("按版本更新基础元数据 - 成功")
        void updateBaseMetadata_WithAssetVersionId_Success() throws Exception {
            AssetVersion version = assetVersionRepository.save(Objects.requireNonNull(AssetVersion.builder()
                    .assetId(testAsset.getId())
                    .versionNo(1)
                    .name("v1.0.0")
                    .description("首个版本")
                    .isDefault(true)
                    .status("active")
                    .createdBy(testUser.getId())
                    .build()));

            String metadataJson = "{\"systemArchitecture\":\"arm64\",\"runtimePlatform\":\"Linux\"}";

            mockMvc.perform(put("/api/v1/assets/" + testAsset.getId() + "/metadata/base")
                    .header("Authorization", engineerToken)
                    .param("assetVersionId", version.getId().toString())
                    .contentType("application/json")
                    .content(metadataJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.assetVersionId").value(version.getId().toString()))
                    .andExpect(jsonPath("$.data.runtimePlatform").value("Linux"));
        }

        @Test
        @DisplayName("按版本更新基础元数据 - 版本不属于资产时返回404")
        void updateBaseMetadata_WithInvalidAssetVersionId_ReturnsNotFound() throws Exception {
            Asset anotherAsset = assetRepository.save(Objects.requireNonNull(Asset.builder()
                    .name("其他资产")
                    .code("META-OTHER-" + System.nanoTime())
                    .targetType("web")
                    .targetSource("internal")
                    .description("其他资产")
                    .priority("P2")
                    .status("active")
                    .ownerId(testUser.getId())
                    .build()));

            AssetVersion version = assetVersionRepository.save(Objects.requireNonNull(AssetVersion.builder()
                    .assetId(anotherAsset.getId())
                    .versionNo(1)
                    .name("v1.0.0")
                    .description("其他资产版本")
                    .isDefault(true)
                    .status("active")
                    .createdBy(testUser.getId())
                    .build()));

            String metadataJson = "{\"systemArchitecture\":\"arm64\",\"runtimePlatform\":\"Linux\"}";

            mockMvc.perform(put("/api/v1/assets/" + testAsset.getId() + "/metadata/base")
                    .header("Authorization", engineerToken)
                    .param("assetVersionId", version.getId().toString())
                    .contentType("application/json")
                    .content(metadataJson))
                    .andExpect(status().isNotFound());
        }
    }
}
