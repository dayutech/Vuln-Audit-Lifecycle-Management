package com.vulnaudit.controller;

import com.vulnaudit.BaseTest;
import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.AssetFile;
import com.vulnaudit.entity.User;
import com.vulnaudit.repository.AssetFileRepository;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 资产文件模块测试类
 */
@DisplayName("资产文件模块测试")
public class AssetFileControllerTest extends BaseTest {

    @Autowired
    private AssetFileRepository assetFileRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private UserRepository userRepository;

    private AssetFile testFile;
    private Asset testAsset;
    private User testUser;

    @BeforeEach
    void setUpFile() {
        testUser = User.builder()
                .username("fileowner")
                .passwordHash("$2a$10$encrypted")
                .email("owner@file.com")
                .fullName("文件负责人")
                .status("active")
                .build();
        testUser = userRepository.save(testUser);

        testAsset = Asset.builder()
                .name("文件测试资产")
                .code("FILE-TEST-001")
                .targetType("web")
                .targetSource("internal")
                .description("用于文件测试的资产")
                .priority("P1")
                .status("active")
                .ownerId(testUser.getId())
                .build();
        testAsset = assetRepository.save(testAsset);

        testFile = AssetFile.builder()
                .assetId(testAsset.getId())
                .name("test_file.zip")
                .filePath("/uploads/test_file.zip")
                .fileSize(1024L)
                .fileType("zip")
                .version(1)
                .status("active")
                .uploadedBy(null)
                .build();
        testFile = assetFileRepository.save(testFile);
    }

    @Nested
    @DisplayName("文件列表查询测试")
    class GetFileListTests {

        @Test
        @DisplayName("获取资产文件列表 - 成功")
        void getFileList_Success() throws Exception {
            mockMvc.perform(get("/api/v1/assets/" + testAsset.getId() + "/files")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("文件详情查询测试")
    class GetFileTests {

        @Test
        @DisplayName("获取文件详情 - 成功")
        void getFile_Success() throws Exception {
            mockMvc.perform(get("/api/v1/asset-files/" + testFile.getId())
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取文件详情 - 文件不存在")
        void getFile_NotFound() throws Exception {
            mockMvc.perform(get("/api/v1/asset-files/" + UUID.randomUUID())
                    .header("Authorization", engineerToken))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("文件版本管理测试")
    class FileVersionTests {

        @Test
        @DisplayName("获取文件版本列表 - 成功")
        void getFileVersions_Success() throws Exception {
            mockMvc.perform(get("/api/v1/asset-files/" + testFile.getId() + "/versions")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("删除文件测试")
    class DeleteFileTests {

        @Test
        @DisplayName("删除文件 - 成功")
        void deleteFile_Success() throws Exception {
            mockMvc.perform(delete("/api/v1/asset-files/" + testFile.getId())
                    .header("Authorization", adminToken))
                    .andExpect(status().isOk());
        }
    }
}
