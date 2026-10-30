package com.vulnaudit.controller;

import com.vulnaudit.entity.Tool;
import com.vulnaudit.repository.ToolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 工具域写端点收尾鉴权测试（t55）。
 *
 * <p>覆盖 t51 上报的同类遗留：工具标签增删与工具包上传此前「仅需登录」。
 * <ul>
 *   <li>{@code POST /tools/{id}/tags}、{@code DELETE /tools/{id}/tags}、{@code POST /tools/{id}/file}
 *       → {@code hasAnyRole('ADMIN','PROJECT_LEADER','TEAM_LEADER')}（与 {@code PUT /tools/{id}} 同权）；</li>
 *   <li>工具不存在 → <b>404</b>（角色通过后由服务层判定资源存在性，不用 403 掩盖）；</li>
 *   <li>查询/下载类端点（列表、详情、版本、标签、download、download-count）保持全员开放；</li>
 *   <li>t23 回归：上传后 {@code fileName/fileSize} 有值，下载字节与上传一致。</li>
 * </ul>
 */
@DisplayName("工具域写端点鉴权测试")
public class ToolWriteAuthorizationTest extends BaseTest {

    @Autowired
    private ToolRepository toolRepository;

    private Tool tool;
    private byte[] fileBytes;

    @BeforeEach
    void setUpToolWriteData() {
        tool = toolRepository.save(Objects.requireNonNull(Tool.builder()
                .name("t55-工具_" + System.currentTimeMillis())
                .category("分析工具")
                .description("工具域写权收口用例")
                .isOfficial(false)
                .createdBy(adminUserId)
                .build()));

        fileBytes = "t55-tool-package-bytes".getBytes(StandardCharsets.UTF_8);
    }

    private MockMultipartFile toolFile() {
        return new MockMultipartFile("file", "t55-tool.zip", "application/zip", fileBytes);
    }

    // ------------------------------------------------------------------ 工程师 403

    @Test
    @DisplayName("工程师：加/删工具标签与上传工具包 → 全部 403")
    void toolWrites_forbiddenForEngineer() throws Exception {
        mockMvc.perform(post("/api/v1/tools/" + tool.getId() + "/tags")
                        .header("Authorization", engineerToken)
                        .param("tag", "t55-tag"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/tools/" + tool.getId() + "/tags")
                        .header("Authorization", engineerToken)
                        .param("tag", "t55-tag"))
                .andExpect(status().isForbidden());

        mockMvc.perform(multipart("/api/v1/tools/" + tool.getId() + "/file")
                        .file(toolFile())
                        .header("Authorization", engineerToken))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ 团队长 / 管理员 200

    @Test
    @DisplayName("团队长：加标签 200、删标签 200、上传工具包 200（fileName/fileSize 有值，下载字节一致）")
    void toolWrites_allowedForTeamLeader() throws Exception {
        mockMvc.perform(post("/api/v1/tools/" + tool.getId() + "/tags")
                        .header("Authorization", leaderToken)
                        .param("tag", "t55-leader-tag"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tools/" + tool.getId() + "/tags")
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0]").value("t55-leader-tag"));

        mockMvc.perform(delete("/api/v1/tools/" + tool.getId() + "/tags")
                        .header("Authorization", leaderToken)
                        .param("tag", "t55-leader-tag"))
                .andExpect(status().isOk());

        // t23 回归：真实落盘 + 元数据
        mockMvc.perform(multipart("/api/v1/tools/" + tool.getId() + "/file")
                        .file(toolFile())
                        .header("Authorization", leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileName").value("t55-tool.zip"))
                .andExpect(jsonPath("$.data.fileSize").value(fileBytes.length));

        // 下载字节一致
        byte[] downloaded = mockMvc.perform(get("/api/v1/tools/" + tool.getId() + "/download")
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertArrayEquals(fileBytes, downloaded, "下载内容应与上传字节一致");
    }

    @Test
    @DisplayName("管理员：加标签 200、删标签 200、上传工具包 200")
    void toolWrites_allowedForAdmin() throws Exception {
        mockMvc.perform(post("/api/v1/tools/" + tool.getId() + "/tags")
                        .header("Authorization", adminToken)
                        .param("tag", "t55-admin-tag"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/tools/" + tool.getId() + "/tags")
                        .header("Authorization", adminToken)
                        .param("tag", "t55-admin-tag"))
                .andExpect(status().isOk());

        mockMvc.perform(multipart("/api/v1/tools/" + tool.getId() + "/file")
                        .file(toolFile())
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fileSize").value(fileBytes.length));
    }

    // ------------------------------------------------------------------ 资源不存在 → 404

    @Test
    @DisplayName("工具不存在：三个写端点均 404（不是 403）")
    void toolWrites_notFoundForMissingTool() throws Exception {
        UUID missing = UUID.randomUUID();

        mockMvc.perform(post("/api/v1/tools/" + missing + "/tags")
                        .header("Authorization", adminToken)
                        .param("tag", "t55-tag"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/tools/" + missing + "/tags")
                        .header("Authorization", adminToken)
                        .param("tag", "t55-tag"))
                .andExpect(status().isNotFound());

        mockMvc.perform(multipart("/api/v1/tools/" + missing + "/file")
                        .file(toolFile())
                        .header("Authorization", adminToken))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ 查询保持开放

    @Test
    @DisplayName("工具查询与下载对工程师仍 200（列表/详情/版本/标签/计数）")
    void toolReads_stillOpenForEngineer() throws Exception {
        mockMvc.perform(get("/api/v1/tools").header("Authorization", engineerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tools/" + tool.getId()).header("Authorization", engineerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tools/" + tool.getId() + "/versions")
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tools/" + tool.getId() + "/tags")
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tools/" + tool.getId() + "/download-count")
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk());
    }
}
