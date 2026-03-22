package com.vulnaudit.controller;

import com.vulnaudit.BaseTest;
import com.vulnaudit.entity.Tool;
import com.vulnaudit.repository.ToolRepository;
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
 * 工具管理模块测试类
 */
@DisplayName("工具管理模块测试")
public class ToolControllerTest extends BaseTest {

    @Autowired
    private ToolRepository toolRepository;

    private Tool testTool;

    @BeforeEach
    void setUpTool() {
        testTool = Tool.builder()
                .name("测试工具")
                .category("扫描工具")
                .description("用于测试的工具")
                .repositoryUrl("https://github.com/test/tool")
                .isOfficial(false)
                .createdBy(null)
                .build();
        testTool = toolRepository.save(testTool);
    }

    @Nested
    @DisplayName("工具列表查询测试")
    class GetToolListTests {

        @Test
        @DisplayName("获取工具列表 - 成功")
        void getToolList_Success() throws Exception {
            mockMvc.perform(get("/api/v1/tools")
                    .header("Authorization", engineerToken)
                    .param("page", "0")
                    .param("size", "10"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取工具列表 - 按分类筛选")
        void getToolList_FilterByCategory() throws Exception {
            mockMvc.perform(get("/api/v1/tools")
                    .header("Authorization", engineerToken)
                    .param("category", "扫描工具"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("搜索工具 - 成功")
        void searchTools_Success() throws Exception {
            mockMvc.perform(get("/api/v1/tools")
                    .header("Authorization", engineerToken)
                    .param("keyword", "测试"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取热门工具 - 成功")
        void getPopularTools_Success() throws Exception {
            mockMvc.perform(get("/api/v1/tools/popular")
                    .header("Authorization", engineerToken)
                    .param("limit", "10"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("工具详情查询测试")
    class GetToolTests {

        @Test
        @DisplayName("获取工具详情 - 成功")
        void getTool_Success() throws Exception {
            mockMvc.perform(get("/api/v1/tools/" + testTool.getId())
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取工具详情 - 工具不存在")
        void getTool_NotFound() throws Exception {
            mockMvc.perform(get("/api/v1/tools/" + UUID.randomUUID())
                    .header("Authorization", engineerToken))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("创建工具测试")
    class CreateToolTests {

        @Test
        @DisplayName("创建工具 - 成功")
        void createTool_Success() throws Exception {
            String toolJson = "{\"name\":\"新工具\",\"category\":\"代理工具\",\"description\":\"新创建的工具\",\"repositoryUrl\":\"https://github.com/new/tool\"}";

            mockMvc.perform(post("/api/v1/tools")
                    .header("Authorization", engineerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toolJson))
                    .andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("更新工具测试")
    class UpdateToolTests {

        @Test
        @DisplayName("更新工具 - 成功")
        void updateTool_Success() throws Exception {
            String toolJson = "{\"name\":\"更新后的工具\",\"description\":\"更新后的描述\"}";

            mockMvc.perform(put("/api/v1/tools/" + testTool.getId())
                    .header("Authorization", engineerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(toolJson))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("工具下载测试")
    class DownloadToolTests {

        @Test
        @DisplayName("记录工具下载 - 成功")
        void recordDownload_Success() throws Exception {
            mockMvc.perform(post("/api/v1/tools/" + testTool.getId() + "/download")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取下载次数 - 成功")
        void getDownloadCount_Success() throws Exception {
            mockMvc.perform(get("/api/v1/tools/" + testTool.getId() + "/download-count")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("工具标签管理测试")
    class ToolTagTests {

        @Test
        @DisplayName("获取工具标签 - 成功")
        void getToolTags_Success() throws Exception {
            mockMvc.perform(get("/api/v1/tools/" + testTool.getId() + "/tags")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("添加工具标签 - 成功")
        void addToolTag_Success() throws Exception {
            mockMvc.perform(post("/api/v1/tools/" + testTool.getId() + "/tags")
                    .header("Authorization", engineerToken)
                    .param("tag", "常用"))
                    .andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("工具版本管理测试")
    class ToolVersionTests {

        @Test
        @DisplayName("获取工具版本列表 - 成功")
        void getToolVersions_Success() throws Exception {
            mockMvc.perform(get("/api/v1/tools/" + testTool.getId() + "/versions")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("删除工具测试")
    class DeleteToolTests {

        @Test
        @DisplayName("删除工具 - 成功")
        void deleteTool_Success() throws Exception {
            mockMvc.perform(delete("/api/v1/tools/" + testTool.getId())
                    .header("Authorization", adminToken))
                    .andExpect(status().isOk());
        }
    }
}
