package com.vulnaudit.controller;

import com.vulnaudit.BaseTest;
import com.vulnaudit.entity.KnowledgeArticle;
import com.vulnaudit.entity.User;
import com.vulnaudit.repository.KnowledgeArticleRepository;
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
 * 知识库模块测试类
 */
@DisplayName("知识库模块测试")
public class KnowledgeControllerTest extends BaseTest {

    @Autowired
    private KnowledgeArticleRepository articleRepository;

    @Autowired
    private UserRepository userRepository;

    private KnowledgeArticle testArticle;
    private User testAuthor;

    @BeforeEach
    void setUpArticle() {
        // 使用唯一后缀避免数据冲突
        String uniqueSuffix = String.valueOf(System.currentTimeMillis());
        
        testAuthor = User.builder()
                .username("knowledgeauthor_" + uniqueSuffix)
                .passwordHash("$2a$10$encrypted")
                .email("author_" + uniqueSuffix + "@knowledge.com")
                .fullName("知识库作者")
                .status("active")
                .build();
        testAuthor = userRepository.save(testAuthor);

        testArticle = KnowledgeArticle.builder()
                .title("测试文章")
                .category("漏洞原理")
                .summary("测试文章摘要")
                .content("测试文章内容")
                .authorId(testAuthor.getId())
                .status("published")
                .viewCount(0)
                .build();
        testArticle = articleRepository.save(testArticle);
    }

    @Nested
    @DisplayName("文章列表查询测试")
    class GetArticleListTests {

        @Test
        @DisplayName("获取文章列表 - 成功")
        void getArticleList_Success() throws Exception {
            mockMvc.perform(get("/api/v1/knowledge")
                    .header("Authorization", engineerToken)
                    .param("page", "0")
                    .param("size", "10"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取文章列表 - 按分类筛选")
        void getArticleList_FilterByCategory() throws Exception {
            mockMvc.perform(get("/api/v1/knowledge")
                    .header("Authorization", engineerToken)
                    .param("category", "漏洞原理"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("搜索文章 - 成功")
        void searchArticles_Success() throws Exception {
            mockMvc.perform(get("/api/v1/knowledge/search")
                    .header("Authorization", engineerToken)
                    .param("keyword", "测试"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取热门文章 - 成功")
        void getPopularArticles_Success() throws Exception {
            mockMvc.perform(get("/api/v1/knowledge/popular")
                    .header("Authorization", engineerToken)
                    .param("limit", "10"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("文章详情查询测试")
    class GetArticleTests {

        @Test
        @DisplayName("获取文章详情 - 成功")
        void getArticle_Success() throws Exception {
            mockMvc.perform(get("/api/v1/knowledge/" + testArticle.getId())
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("获取文章详情 - 文章不存在")
        void getArticle_NotFound() throws Exception {
            mockMvc.perform(get("/api/v1/knowledge/" + UUID.randomUUID())
                    .header("Authorization", engineerToken))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("创建文章测试")
    class CreateArticleTests {

        @Test
        @DisplayName("创建文章 - 成功")
        void createArticle_Success() throws Exception {
            String articleJson = "{\"title\":\"新文章\",\"category\":\"测试方法\",\"summary\":\"新文章摘要\",\"content\":\"新文章内容\"}";

            mockMvc.perform(post("/api/v1/knowledge")
                    .header("Authorization", engineerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(articleJson))
                    .andExpect(status().isCreated());
        }
    }

    @Nested
    @DisplayName("更新文章测试")
    class UpdateArticleTests {

        @Test
        @DisplayName("更新文章 - 成功")
        void updateArticle_Success() throws Exception {
            String articleJson = "{\"title\":\"更新后的标题\",\"content\":\"更新后的内容\"}";

            mockMvc.perform(put("/api/v1/knowledge/" + testArticle.getId())
                    .header("Authorization", engineerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(articleJson))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("文章评论测试")
    class ArticleCommentTests {

        @Test
        @DisplayName("添加文章评论 - 成功")
        void addArticleComment_Success() throws Exception {
            mockMvc.perform(post("/api/v1/knowledge/" + testArticle.getId() + "/comments")
                    .header("Authorization", engineerToken)
                    .param("content", "这是一条测试评论"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("文章标签管理测试")
    class ArticleTagTests {

        @Test
        @DisplayName("获取文章标签 - 成功")
        void getArticleTags_Success() throws Exception {
            mockMvc.perform(get("/api/v1/knowledge/" + testArticle.getId() + "/tags")
                    .header("Authorization", engineerToken))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("添加文章标签 - 成功")
        void addArticleTag_Success() throws Exception {
            mockMvc.perform(post("/api/v1/knowledge/" + testArticle.getId() + "/tags")
                    .header("Authorization", engineerToken)
                    .param("tag", "重要"))
                    .andExpect(status().isOk());
        }
    }

    @Nested
    @DisplayName("删除文章测试")
    class DeleteArticleTests {

        @Test
        @DisplayName("删除文章 - 成功")
        void deleteArticle_Success() throws Exception {
            mockMvc.perform(delete("/api/v1/knowledge/" + testArticle.getId())
                    .header("Authorization", adminToken))
                    .andExpect(status().isOk());
        }
    }
}
