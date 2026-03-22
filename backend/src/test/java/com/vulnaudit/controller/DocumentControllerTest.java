package com.vulnaudit.controller;

import com.vulnaudit.BaseTest;
import com.vulnaudit.entity.Document;
import com.vulnaudit.entity.User;
import com.vulnaudit.repository.DocumentRepository;
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
 * 文档管理模块测试类
 */
@DisplayName("文档管理模块测试")
public class DocumentControllerTest extends BaseTest {

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private UserRepository userRepository;

    private Document testDocument;
    private User testAuthor;

    @BeforeEach
    void setUpDocument() {
        // 使用唯一后缀避免数据冲突
        String uniqueSuffix = String.valueOf(System.currentTimeMillis());
        
        testAuthor = User.builder()
                .username("docauthor_" + uniqueSuffix)
                .passwordHash("$2a$10$encrypted")
                .email("author_" + uniqueSuffix + "@doc.com")
                .fullName("文档作者")
                .status("active")
                .build();
        testAuthor = userRepository.save(testAuthor);

        testDocument = Document.builder()
                .title("测试文档")
                .category("guide")
                .summary("测试文档摘要")
                .content("测试文档内容")
                .authorId(testAuthor.getId())
                .status("draft")
                .version(1)
                .wordCount(100)
                .createdBy(testAuthor.getId())
                .build();
        testDocument = documentRepository.save(testDocument);
    }

    @Nested
    @DisplayName("文档列表查询测试")
    class GetDocumentListTests {

        @Test
        @DisplayName("获取文档列表 - 成功")
        void getDocumentList_Success() throws Exception {
            mockMvc.perform(get("/api/v1/documents")
                    .header("Authorization", engineerToken)
                    .param("page", "0")
                    .param("size", "10"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取文档列表 - 按分类筛选")
        void getDocumentList_FilterByCategory() throws Exception {
            mockMvc.perform(get("/api/v1/documents")
                    .header("Authorization", engineerToken)
                    .param("category", "guide"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("搜索文档 - 成功")
        void searchDocuments_Success() throws Exception {
            mockMvc.perform(get("/api/v1/documents/search")
                    .header("Authorization", engineerToken)
                    .param("keyword", "测试"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("文档详情查询测试")
    class GetDocumentTests {

        @Test
        @DisplayName("获取文档详情 - 成功")
        void getDocument_Success() throws Exception {
            mockMvc.perform(get("/api/v1/documents/" + testDocument.getId())
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取文档详情 - 文档不存在")
        void getDocument_NotFound() throws Exception {
            mockMvc.perform(get("/api/v1/documents/" + UUID.randomUUID())
                    .header("Authorization", engineerToken))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("创建文档测试")
    class CreateDocumentTests {

        @Test
        @DisplayName("创建文档 - 成功")
        void createDocument_Success() throws Exception {
            String docJson = "{\"title\":\"新文档\",\"category\":\"report\",\"summary\":\"新文档摘要\",\"content\":\"新文档内容\"}";

            mockMvc.perform(post("/api/v1/documents")
                    .header("Authorization", engineerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(docJson))
                    .andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("更新文档测试")
    class UpdateDocumentTests {

        @Test
        @DisplayName("更新文档 - 成功")
        void updateDocument_Success() throws Exception {
            String docJson = "{\"title\":\"更新后的标题\",\"content\":\"更新后的内容\"}";

            mockMvc.perform(put("/api/v1/documents/" + testDocument.getId())
                    .header("Authorization", engineerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(docJson))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("文档发布测试")
    class PublishDocumentTests {

        @Test
        @DisplayName("发布文档 - 成功")
        void publishDocument_Success() throws Exception {
            mockMvc.perform(post("/api/v1/documents/" + testDocument.getId() + "/publish")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("取消发布文档 - 成功")
        void unpublishDocument_Success() throws Exception {
            mockMvc.perform(post("/api/v1/documents/" + testDocument.getId() + "/unpublish")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("文档版本管理测试")
    class DocumentVersionTests {

        @Test
        @DisplayName("获取文档版本历史 - 成功")
        void getDocumentVersions_Success() throws Exception {
            mockMvc.perform(get("/api/v1/documents/" + testDocument.getId() + "/versions")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("文档标签管理测试")
    class DocumentTagTests {

        @Test
        @DisplayName("获取文档标签 - 成功")
        void getDocumentTags_Success() throws Exception {
            mockMvc.perform(get("/api/v1/documents/" + testDocument.getId() + "/tags")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("添加文档标签 - 成功")
        void addDocumentTag_Success() throws Exception {
            mockMvc.perform(post("/api/v1/documents/" + testDocument.getId() + "/tags")
                    .header("Authorization", engineerToken)
                    .param("tag", "重要"))
                    .andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("删除文档测试")
    class DeleteDocumentTests {

        @Test
        @DisplayName("删除文档 - 成功")
        void deleteDocument_Success() throws Exception {
            mockMvc.perform(delete("/api/v1/documents/" + testDocument.getId())
                    .header("Authorization", adminToken))
                    .andExpect(status().isOk());
        }
    }
}
