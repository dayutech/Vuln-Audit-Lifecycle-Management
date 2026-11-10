package com.vulnaudit.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.vulnaudit.entity.Document;
import com.vulnaudit.entity.KnowledgeArticle;
import com.vulnaudit.repository.DocumentAttachmentRepository;
import com.vulnaudit.repository.DocumentRepository;
import com.vulnaudit.repository.KnowledgeArticleRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 知识库 / 文档附件 + 列表 status 筛选（t162）
 *
 * <p>替代前端「正文内 base64 附件块」（受 `content TEXT(64KB)` 限制，二进制实际只能 ≤40KB）：
 * 附件独立成行、文件落盘（UUID 前缀）、下载必须经归属校验。
 *
 * <p>另外把「草稿/已发布」筛选从**前端当前页过滤**改成**数据库过滤**（否则翻页后 total 错）。
 */
@DisplayName("知识库/文档附件测试")
@TestPropertySource(properties = {
        "app.attachments.dir=target/test-attachments",
        "app.attachments.max-bytes=1048576",
        "spring.jpa.show-sql=true"
})
public class DocumentAttachmentTest extends BaseTest {

    @Autowired
    private KnowledgeArticleRepository knowledgeArticleRepository;
    @Autowired
    private DocumentRepository documentRepository;
    @Autowired
    private DocumentAttachmentRepository attachmentRepository;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private KnowledgeArticle article;
    private Document document;

    @BeforeEach
    void setUpFixtures() {
        article = knowledgeArticleRepository.save(Objects.requireNonNull(KnowledgeArticle.builder()
                .title("附件测试文章")
                .content("正文")
                .category("规范")
                .status("published")
                .authorId(adminUserId)
                .build()));
        document = documentRepository.save(Objects.requireNonNull(Document.builder()
                .title("附件测试文档")
                .content("正文")
                .category("规范")
                .status("draft")
                .authorId(adminUserId)
                .createdBy(adminUserId)
                .build()));
    }

    // ------------------------------------------------------------------ 工具

    private MockMultipartFile pdf(String name) {
        byte[] payload = new byte[5 * 1024];
        java.util.Arrays.fill(payload, (byte) 'p');
        return new MockMultipartFile("file", name, "application/pdf", payload);
    }

    private JsonNode uploadKnowledge(String token, MockMultipartFile file) throws Exception {
        String body = mockMvc.perform(multipart("/api/v1/knowledge/" + article.getId() + "/attachments")
                        .file(file).header("Authorization", token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    private long queryCount() {
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        return stats.getPrepareStatementCount();
    }

    // ------------------------------------------------------------------ 一、知识库附件

    @Test
    @DisplayName("上传 + 列表：元数据完整（含 uploaderName），文件落盘")
    void uploadAndList() throws Exception {
        JsonNode data = uploadKnowledge(adminToken, pdf("报告-v1.pdf"));

        assertEquals("报告-v1.pdf", data.path("fileName").asText());
        assertEquals("application/pdf", data.path("contentType").asText());
        assertTrue(data.path("fileSize").asLong() > 0);
        assertEquals("KNOWLEDGE", data.path("ownerType").asText());
        assertEquals(usernameOf(adminUserId), data.path("uploaderName").asText());
        assertTrue(Files.exists(Paths.get("target/test-attachments/knowledge")
                .resolve(article.getId().toString())), "附件应落在 owner 目录下");

        mockMvc.perform(get("/api/v1/knowledge/" + article.getId() + "/attachments")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].fileName").value("报告-v1.pdf"))
                .andExpect(jsonPath("$.data[0].uploaderName").value(usernameOf(adminUserId)));
    }

    @Test
    @DisplayName("下载：Content-Disposition 支持中文（RFC 5987），内容正确")
    void downloadWithChineseFileName() throws Exception {
        JsonNode data = uploadKnowledge(adminToken, pdf("中文报告.pdf"));
        UUID attachmentId = UUID.fromString(data.path("id").asText());

        var response = mockMvc.perform(get("/api/v1/knowledge/" + article.getId()
                        + "/attachments/" + attachmentId + "/download")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        String disposition = response.getHeader("Content-Disposition");
        assertTrue(disposition.contains("filename*=UTF-8''"), "应有 RFC 5987 编码: " + disposition);
        assertTrue(disposition.contains("%E4%B8%AD%E6%96%87"), "中文应被 URL 编码: " + disposition);
        assertEquals(5 * 1024, response.getContentAsByteArray().length, "下载内容长度应与上传一致");
    }

    @Test
    @DisplayName("删除：软删 + 删物理文件；列表不含、下载 404")
    void deleteAttachment() throws Exception {
        JsonNode data = uploadKnowledge(adminToken, pdf("待删.pdf"));
        UUID attachmentId = UUID.fromString(data.path("id").asText());

        mockMvc.perform(delete("/api/v1/knowledge/" + article.getId() + "/attachments/" + attachmentId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/knowledge/" + article.getId() + "/attachments")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(get("/api/v1/knowledge/" + article.getId()
                        + "/attachments/" + attachmentId + "/download")
                        .header("Authorization", adminToken))
                .andExpect(status().isNotFound());

        var row = attachmentRepository.findById(attachmentId).orElseThrow();
        assertTrue(row.getDeletedAt() != null, "应为软删");
        assertFalse(Files.exists(Paths.get("target/test-attachments").resolve(row.getStoragePath())),
                "物理文件应删除");
    }

    @Test
    @DisplayName("归属校验：附件不属于该实体 → 下载 404")
    void downloadCrossOwnerNotFound() throws Exception {
        JsonNode data = uploadKnowledge(adminToken, pdf("跨实体.pdf"));
        UUID attachmentId = UUID.fromString(data.path("id").asText());

        // 用文档 id 去下载知识库的附件 → 404
        mockMvc.perform(get("/api/v1/documents/" + document.getId()
                        + "/attachments/" + attachmentId + "/download")
                        .header("Authorization", adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("上传：超过上限 → 413；空文件 → 400")
    void sizeValidation() throws Exception {
        byte[] tooBig = new byte[1024 * 1024 + 1];
        mockMvc.perform(multipart("/api/v1/knowledge/" + article.getId() + "/attachments")
                        .file(new MockMultipartFile("file", "big.pdf", "application/pdf", tooBig))
                        .header("Authorization", adminToken))
                .andExpect(status().isPayloadTooLarge());

        mockMvc.perform(multipart("/api/v1/knowledge/" + article.getId() + "/attachments")
                        .file(new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]))
                        .header("Authorization", adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("文件名净化：路径穿越名被截成 basename")
    void fileNameSanitized() throws Exception {
        JsonNode data = uploadKnowledge(adminToken, pdf("../../etc/evil.pdf"));
        assertEquals("evil.pdf", data.path("fileName").asText());
    }

    @Test
    @DisplayName("权限：非作者/非管理层上传 → 403（工程师）")
    void uploadForbiddenForOthers() throws Exception {
        mockMvc.perform(multipart("/api/v1/knowledge/" + article.getId() + "/attachments")
                        .file(pdf("forbidden.pdf")).header("Authorization", engineerToken))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ 二、文档附件

    @Test
    @DisplayName("文档附件：上传/列表/下载/删除全链路")
    void documentAttachmentCrud() throws Exception {
        String created = mockMvc.perform(multipart("/api/v1/documents/" + document.getId() + "/attachments")
                        .file(pdf("规范附件.pdf")).header("Authorization", adminToken))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID attachmentId = UUID.fromString(objectMapper.readTree(created).path("data").path("id").asText());

        mockMvc.perform(get("/api/v1/documents/" + document.getId() + "/attachments")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].ownerType").value("DOCUMENT"));

        mockMvc.perform(get("/api/v1/documents/" + document.getId()
                        + "/attachments/" + attachmentId + "/download").header("Authorization", adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/documents/" + document.getId() + "/attachments/" + attachmentId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/documents/" + document.getId() + "/attachments")
                        .header("Authorization", adminToken))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("uploaderName：一次批量解析，查询数不随附件条数增长（服务层口径）")
    void uploaderNamesBatchResolved() throws Exception {
        uploadKnowledge(adminToken, pdf("批量0.pdf"));
        // 预热（含实体类元数据/二级缓存等首次开销）
        attachmentService.list("KNOWLEDGE", article.getId());

        // 先落盘并清一级缓存：否则后面的 SELECT 会触发 auto-flush，把 INSERT 也计入统计
        entityManager.flush();
        entityManager.clear();
        long before1 = queryCount();
        var one = attachmentService.list("KNOWLEDGE", article.getId());
        long delta1 = queryCount() - before1;
        assertEquals(1, one.size());
        assertEquals(usernameOf(adminUserId), one.get(0).uploaderName());

        for (int i = 1; i < 5; i++) {
            uploadKnowledge(adminToken, pdf("批量" + i + ".pdf"));
        }

        entityManager.flush();
        entityManager.clear();
        long before5 = queryCount();
        var five = attachmentService.list("KNOWLEDGE", article.getId());
        long delta5 = queryCount() - before5;
        assertEquals(5, five.size());
        assertEquals(usernameOf(adminUserId), five.get(0).uploaderName());

        // 服务层口径：附件行 1 次 + 上传人批量 1 次 = 2，与条数无关
        assertEquals(delta1, delta5,
                "查询数必须与附件条数无关（1 条: " + delta1 + " 次，5 条: " + delta5 + " 次）");
        assertTrue(delta5 <= 2, "服务层 list() 应是常数 2 次查询，实测 " + delta5);
    }

    // ------------------------------------------------------------------ 三、列表 status 筛选

    @Test
    @DisplayName("知识库：status=DRAFT 在数据库过滤（跨页 total 正确）")
    void knowledgeStatusFilterUsesDatabase() throws Exception {
        for (int i = 0; i < 7; i++) {
            knowledgeArticleRepository.save(Objects.requireNonNull(KnowledgeArticle.builder()
                    .title("草稿" + i).content("x").category("规范").status("draft")
                    .authorId(adminUserId).build()));
        }
        // 现有 1 篇 published + 7 篇 draft

        String draft = mockMvc.perform(get("/api/v1/knowledge").header("Authorization", adminToken)
                        .param("status", "DRAFT").param("page", "0").param("size", "3"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(draft).path("data");
        assertEquals(7, data.path("totalElements").asInt(), "total 应是全部草稿（跨页正确）");
        assertEquals(3, data.path("content").size());
        assertEquals(3, data.path("totalPages").asInt());

        String published = mockMvc.perform(get("/api/v1/knowledge").header("Authorization", adminToken)
                        .param("status", "published"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(1, objectMapper.readTree(published).path("data").path("totalElements").asInt());
    }

    @Test
    @DisplayName("文档：status=PUBLISHED 过滤 + all 不过滤 + 非法值 400")
    void documentStatusFilterAndValidation() throws Exception {
        for (int i = 0; i < 4; i++) {
            documentRepository.save(Objects.requireNonNull(Document.builder()
                    .title("已发布文档" + i).content("x").category("规范").status("published")
                    .authorId(adminUserId).createdBy(adminUserId).build()));
        }

        String published = mockMvc.perform(get("/api/v1/documents").header("Authorization", adminToken)
                        .param("status", "PUBLISHED"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(4, objectMapper.readTree(published).path("data").path("totalElements").asInt());

        String all = mockMvc.perform(get("/api/v1/documents").header("Authorization", adminToken)
                        .param("status", "all"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(5, objectMapper.readTree(all).path("data").path("totalElements").asInt(), "all 不过滤");

        mockMvc.perform(get("/api/v1/documents").header("Authorization", adminToken)
                        .param("status", "ARCHIVED"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/knowledge").header("Authorization", adminToken)
                        .param("status", "whatever"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("迁移脚本 V33：建表 + 两个索引 + 显式 collation + 校验 SQL（幂等守卫）")
    void migrationScriptGuards() throws Exception {
        String sql = Files.readString(
                Paths.get("src/main/resources/db/migration/V33__document_attachments.sql"),
                StandardCharsets.UTF_8);
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS document_attachments"));
        assertTrue(sql.contains("COLLATE=utf8mb4_unicode_ci"));
        assertTrue(sql.contains("idx_document_attachments_owner"));
        assertTrue(sql.contains("idx_document_attachments_deleted"));
        assertTrue(sql.contains("deleted_at IS NULL"), "应有软删统计");
        assertTrue(Files.readString(Paths.get("../deploy/manual-ddl.sql"), StandardCharsets.UTF_8)
                .contains("V33：知识库 / 文档附件"), "manual-ddl 必须有等价段");
    }

    @Test
    @DisplayName("未登录：上传/列表/下载 → 401")
    void requiresLogin() throws Exception {
        mockMvc.perform(get("/api/v1/knowledge/" + article.getId() + "/attachments"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(multipart("/api/v1/knowledge/" + article.getId() + "/attachments").file(pdf("x.pdf")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/knowledge/" + article.getId() + "/attachments/"
                        + UUID.randomUUID() + "/download"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("归属隔离：知识库列表不含文档附件（ownerType 不串）")
    void ownerTypeIsolation() throws Exception {
        uploadKnowledge(adminToken, pdf("知识库附件.pdf"));
        mockMvc.perform(multipart("/api/v1/documents/" + document.getId() + "/attachments")
                        .file(pdf("文档附件.pdf")).header("Authorization", adminToken))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/knowledge/" + article.getId() + "/attachments")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].fileName").value("知识库附件.pdf"))
                .andExpect(jsonPath("$.data[0].ownerType").value("KNOWLEDGE"));

        mockMvc.perform(get("/api/v1/documents/" + document.getId() + "/attachments")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].fileName").value("文档附件.pdf"))
                .andExpect(jsonPath("$.data[0].ownerType").value("DOCUMENT"));
    }

    @Test
    @DisplayName("下载/删除不存在的附件 → 404")
    void missingAttachmentNotFound() throws Exception {
        UUID missing = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/knowledge/" + article.getId() + "/attachments/" + missing + "/download")
                        .header("Authorization", adminToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/knowledge/" + article.getId() + "/attachments/" + missing)
                        .header("Authorization", adminToken))
                .andExpect(status().isNotFound());
    }

    private String usernameOf(UUID userId) {
        return userRepository.findById(userId).orElseThrow().getUsername();
    }

    @Autowired
    private com.vulnaudit.repository.UserRepository userRepository;
    @Autowired
    private com.vulnaudit.service.DocumentAttachmentService attachmentService;
}
