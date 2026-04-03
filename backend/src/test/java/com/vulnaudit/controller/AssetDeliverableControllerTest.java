package com.vulnaudit.controller;

import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.AssetDeliverable;
import com.vulnaudit.entity.AssetVersion;
import com.vulnaudit.repository.AssetDeliverableRepository;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.AssetVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.util.Objects;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("资产交付物模块测试")
public class AssetDeliverableControllerTest extends BaseTest {

    @Autowired
    private AssetDeliverableRepository assetDeliverableRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private AssetVersionRepository assetVersionRepository;

    private Asset testAsset;
    private AssetVersion testVersion;

    @BeforeEach
    void setUpDeliverableData() {
        super.setUp();
        String uniqueSuffix = String.valueOf(System.currentTimeMillis());

        testAsset = assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name("交付物测试资产")
                .code("DELIVERABLE-TEST-" + uniqueSuffix)
                .targetType("web")
                .targetSource("internal")
                .description("用于交付物测试的资产")
                .priority("P1")
                .status("active")
                .ownerId(engineerUserId)
                .build()));

        testVersion = assetVersionRepository.save(Objects.requireNonNull(AssetVersion.builder()
                .assetId(testAsset.getId())
                .versionNo(1)
                .name("v1")
                .isDefault(true)
                .status("active")
                .createdBy(engineerUserId)
                .build()));

        assetDeliverableRepository.save(Objects.requireNonNull(AssetDeliverable.builder()
                .assetId(testAsset.getId())
                .assetVersionId(testVersion.getId())
                .name("poc.py")
                .deliverableType("POC脚本")
                .fileType("py")
                .description("用于验证漏洞")
                .fileSize(128L)
                .filePath("./uploads/deliverables/poc.py")
                .status("active")
                .uploadedBy(engineerUserId)
                .build()));

        assetDeliverableRepository.save(Objects.requireNonNull(AssetDeliverable.builder()
                .assetId(testAsset.getId())
                .assetVersionId(testVersion.getId())
                .name("archived.py")
                .deliverableType("POC脚本")
                .fileType("py")
                .description("已归档")
                .fileSize(64L)
                .filePath("./uploads/deliverables/archived.py")
                .status("archived")
                .uploadedBy(engineerUserId)
                .build()));
    }

    @Nested
    @DisplayName("交付物列表查询测试")
    class ListDeliverablesTests {

        @Test
        @DisplayName("获取交付物列表 - 成功")
        void listDeliverables_Success() throws Exception {
            mockMvc.perform(get("/api/v1/assets/" + testAsset.getId() + "/deliverables")
                            .param("assetVersionId", testVersion.getId().toString())
                            .header("Authorization", engineerToken))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0].deliverableType").value("POC脚本"));
        }
    }

    @Nested
    @DisplayName("交付物上传测试")
    class UploadDeliverableTests {

        @Test
        @DisplayName("上传交付物 - 成功")
        void uploadDeliverable_Success() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "exp.py",
                    "text/plain",
                    "print('exp')".getBytes()
            );

            mockMvc.perform(multipart("/api/v1/assets/" + testAsset.getId() + "/deliverables")
                            .file(file)
                            .param("assetVersionId", testVersion.getId().toString())
                            .param("deliverableType", "EXP脚本")
                            .param("description", "自动化利用脚本")
                            .header("Authorization", engineerToken))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.name").value("exp.py"))
                    .andExpect(jsonPath("$.data.deliverableType").value("EXP脚本"));
        }

        @Test
        @DisplayName("上传交付物 - 空文件")
        void uploadDeliverable_EmptyFile() throws Exception {
            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "empty.txt",
                    "text/plain",
                    new byte[0]
            );

            mockMvc.perform(multipart("/api/v1/assets/" + testAsset.getId() + "/deliverables")
                            .file(file)
                            .param("assetVersionId", testVersion.getId().toString())
                            .param("deliverableType", "POC脚本")
                            .header("Authorization", engineerToken))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("上传文件不能为空"));
        }

        @Test
        @DisplayName("上传交付物 - 资产版本不属于当前资产")
        void uploadDeliverable_NotFoundWhenVersionNotBelongs() throws Exception {
            Asset anotherAsset = assetRepository.save(Objects.requireNonNull(Asset.builder()
                    .name("其他资产")
                    .code("DELIVERABLE-OTHER-" + System.currentTimeMillis())
                    .targetType("web")
                    .targetSource("internal")
                    .description("其他资产")
                    .priority("P1")
                    .status("active")
                    .ownerId(engineerUserId)
                    .build()));
            AssetVersion anotherVersion = assetVersionRepository.save(Objects.requireNonNull(AssetVersion.builder()
                    .assetId(anotherAsset.getId())
                    .versionNo(1)
                    .name("other-v1")
                    .isDefault(true)
                    .status("active")
                    .createdBy(engineerUserId)
                    .build()));

            MockMultipartFile file = new MockMultipartFile(
                    "file",
                    "poc.py",
                    "text/plain",
                    "print('poc')".getBytes()
            );

            mockMvc.perform(multipart("/api/v1/assets/" + testAsset.getId() + "/deliverables")
                            .file(file)
                            .param("assetVersionId", anotherVersion.getId().toString())
                            .param("deliverableType", "POC脚本")
                            .header("Authorization", engineerToken))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("资产版本不存在或不属于当前资产"));
        }
    }
}
