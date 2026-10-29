package com.vulnaudit.controller;

import com.vulnaudit.entity.Tool;
import com.vulnaudit.entity.ToolVersion;
import com.vulnaudit.repository.ToolRepository;
import com.vulnaudit.repository.ToolVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 工具版本上传/下载测试
 *
 * <p>覆盖 t23 验收口径：
 * <ol>
 *   <li>带文件上传 → 201、createdBy 非空、文件可下载且字节一致；</li>
 *   <li>无文件上传 → hasFile=false；</li>
 *   <li>versionNo 在同工具下自增；</li>
 *   <li>跨工具 versionId 下载 → 404。</li>
 * </ol>
 */
@DisplayName("工具版本上传与下载测试")
class ToolVersionUploadTest extends BaseTest {

    @Autowired
    private ToolRepository toolRepository;

    @Autowired
    private ToolVersionRepository toolVersionRepository;

    private Tool tool;
    private Tool otherTool;
    private byte[] fileContent;

    @BeforeEach
    void setUpTool() throws Exception {
        // 通过接口创建工具（同时验证 tools.created_by 不再为 null）
        mockMvc.perform(post("/api/v1/tools")
                        .header("Authorization", adminToken)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(
                                java.util.Map.of("name", "t23-测试工具-" + UUID.randomUUID(),
                                        "description", "用于版本上传测试",
                                        "category", "扫描器")))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.createdBy").value(adminUserId.toString()));

        tool = toolRepository.findAll().stream()
                .filter(item -> "扫描器".equals(item.getCategory()))
                .findFirst()
                .orElseThrow();

        otherTool = toolRepository.save(Objects.requireNonNull(Tool.builder()
                .name("t23-另一个工具-" + UUID.randomUUID())
                .description("另一个工具")
                .category("利用工具")
                .createdBy(adminUserId)
                .build()));

        fileContent = new byte[512];
        for (int i = 0; i < fileContent.length; i++) {
            fileContent[i] = (byte) (i % 251);
        }
    }

    private MockMultipartFile versionFile() {
        return new MockMultipartFile("file", "poc-tool.bin", "application/octet-stream", fileContent);
    }

    private String uploadUrl(UUID toolId) {
        return "/api/v1/tools/" + toolId + "/versions";
    }

    private String downloadUrl(UUID toolId, UUID versionId) {
        return "/api/v1/tools/" + toolId + "/versions/" + versionId + "/download";
    }

    @Test
    @DisplayName("带文件上传版本 - 201，createdBy 非空，文件可下载且字节一致")
    void uploadVersion_withFile_persistsAndDownloads() throws Exception {
        MvcResult result = mockMvc.perform(multipart(uploadUrl(tool.getId()))
                        .file(versionFile())
                        .param("version", "v1")
                        .param("changelog", "首个版本")
                        .header("Authorization", adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.createdBy").value(adminUserId.toString()))
                .andExpect(jsonPath("$.data.versionNo").value(1))
                .andExpect(jsonPath("$.data.hasFile").value(true))
                .andExpect(jsonPath("$.data.fileName").value("poc-tool.bin"))
                .andExpect(jsonPath("$.data.fileSize").value(fileContent.length))
                // 落盘路径包含 versions/{versionId} 目录（Windows 下分隔符为反斜杠，这里只校验关键片段）
                .andExpect(jsonPath("$.data.filePath", containsString("versions")))
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        UUID versionId = UUID.fromString(extract(body, "\"id\":\""));

        // DB 校验：createdBy 必须真实写入
        ToolVersion saved = toolVersionRepository.findById(versionId).orElseThrow();
        assertEquals(adminUserId, saved.getCreatedBy());
        assertNotNull(saved.getFilePath());
        assertTrue(saved.isHasFile());

        // 下载：字节一致 + Content-Disposition
        MvcResult download = mockMvc.perform(get(downloadUrl(tool.getId(), versionId))
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        containsString("attachment; filename=\"poc-tool.bin\"")))
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, fileContent.length))
                .andReturn();

        byte[] downloaded = download.getResponse().getContentAsByteArray();
        assertEquals(fileContent.length, downloaded.length);
        assertArrayEquals(fileContent, downloaded);
    }

    @Test
    @DisplayName("无文件上传版本 - 201 且 hasFile=false，下载返回 404 明确提示")
    void uploadVersion_withoutFile_marksMetadataOnly() throws Exception {
        MvcResult result = mockMvc.perform(multipart(uploadUrl(tool.getId()))
                        .param("version", "v-meta")
                        .param("changelog", "仅元数据")
                        .header("Authorization", adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.hasFile").value(false))
                .andExpect(jsonPath("$.data.createdBy").value(adminUserId.toString()))
                .andReturn();

        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        UUID versionId = UUID.fromString(extract(body, "\"id\":\""));

        ToolVersion saved = toolVersionRepository.findById(versionId).orElseThrow();
        assertFalse(saved.isHasFile());
        assertNull(saved.getFileSize());
        assertEquals("", saved.getFilePath());

        mockMvc.perform(get(downloadUrl(tool.getId(), versionId))
                        .header("Authorization", adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("仅元数据")));
    }

    @Test
    @DisplayName("versionNo 自增 - 连续上传 1/2/3，且版本号留空自动生成 v3")
    void uploadVersion_incrementsVersionNo() throws Exception {
        for (int i = 1; i <= 2; i++) {
            mockMvc.perform(multipart(uploadUrl(tool.getId()))
                            .file(versionFile())
                            .param("version", "v" + i)
                            .header("Authorization", adminToken))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.versionNo").value(i));
        }

        // 第三次不传 version，服务应按最大 versionNo + 1 生成 v3
        mockMvc.perform(multipart(uploadUrl(tool.getId()))
                        .file(versionFile())
                        .header("Authorization", adminToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.versionNo").value(3))
                .andExpect(jsonPath("$.data.version").value("v3"));

        List<ToolVersion> versions = toolVersionRepository.findByToolIdOrderByCreatedAtDesc(tool.getId());
        assertEquals(3, versions.size());
        assertTrue(versions.stream().allMatch(version -> version.getCreatedBy().equals(adminUserId)));
        assertTrue(versions.stream().allMatch(version -> version.getVersionNo() != null));
    }

    @Test
    @DisplayName("跨工具下载 - versionId 属于其他工具时返回 404")
    void downloadVersion_ofAnotherTool_returns404() throws Exception {
        MvcResult uploaded = mockMvc.perform(multipart(uploadUrl(otherTool.getId()))
                        .file(versionFile())
                        .param("version", "v1")
                        .header("Authorization", adminToken))
                .andExpect(status().isCreated())
                .andReturn();
        UUID otherVersionId = UUID.fromString(extract(
                uploaded.getResponse().getContentAsString(StandardCharsets.UTF_8), "\"id\":\""));

        mockMvc.perform(get(downloadUrl(tool.getId(), otherVersionId))
                        .header("Authorization", adminToken))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("不属于该工具")));

        // 存在性验证：换回正确工具可下载
        mockMvc.perform(get(downloadUrl(otherTool.getId(), otherVersionId))
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, fileContent.length));
    }

    @Test
    @DisplayName("版本列表返回 createdBy 与 hasFile，便于前端展示审计信息")
    void listVersions_exposesAuditFields() throws Exception {
        mockMvc.perform(multipart(uploadUrl(tool.getId()))
                        .file(versionFile())
                        .param("version", "v1")
                        .header("Authorization", adminToken))
                .andExpect(status().isCreated());

        mockMvc.perform(get(uploadUrl(tool.getId()))
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].createdBy", notNullValue()))
                .andExpect(jsonPath("$.data[0].hasFile").value(true));
    }

    /**
     * 从 JSON 中截取指定字段起始后的第一个值（避免额外引入解析样板）
     */
    private String extract(String body, String marker) {
        int start = body.indexOf(marker);
        if (start < 0) {
            throw new AssertionError("响应中未找到 " + marker + ": " + body);
        }
        int begin = start + marker.length();
        int end = body.indexOf('"', begin);
        return body.substring(begin, end);
    }
}
