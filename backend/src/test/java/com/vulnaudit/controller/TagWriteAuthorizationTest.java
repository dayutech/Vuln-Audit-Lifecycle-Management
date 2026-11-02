package com.vulnaudit.controller;

import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.Document;
import com.vulnaudit.entity.KnowledgeArticle;
import com.vulnaudit.entity.Tool;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.DocumentRepository;
import com.vulnaudit.repository.KnowledgeArticleRepository;
import com.vulnaudit.repository.ToolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Objects;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 四类资源标签写权限统一测试（t61）。
 *
 * <p>统一规则（README §29 / 验收报告 §2.3.1）：
 * <table>
 *   <tr><th>资源</th><th>写标签所需权限</th></tr>
 *   <tr><td>资产</td><td>{@code canWriteAsset}（负责人 或 管理层，t49）</td></tr>
 *   <tr><td>工具</td><td>管理层（ADMIN/PROJECT_LEADER/TEAM_LEADER，t55）</td></tr>
 *   <tr><td>知识库文章</td><td>作者本人 或 管理层（t61）</td></tr>
 *   <tr><td>文档</td><td>作者本人 或 管理层（t61）</td></tr>
 * </table>
 *
 * <p>另外两条不变量：<b>查询类标签接口对所有登录用户开放</b>（列表要用）；
 * <b>资源不存在返回 404</b>（不用 403 暴露存在性）。
 */
@DisplayName("标签写权限统一测试")
public class TagWriteAuthorizationTest extends BaseTest {

    @Autowired
    private KnowledgeArticleRepository knowledgeArticleRepository;

    @Autowired
    private DocumentRepository documentRepository;

    @Autowired
    private ToolRepository toolRepository;

    @Autowired
    private AssetRepository assetRepository;

    private KnowledgeArticle engineerArticle;
    private KnowledgeArticle leaderArticle;
    private Document engineerDocument;
    private Document leaderDocument;
    private Tool tool;
    private Asset engineerAsset;
    private Asset leaderAsset;

    @BeforeEach
    void setUpTagData() {
        String suffix = String.valueOf(System.currentTimeMillis());

        engineerArticle = knowledgeArticleRepository.save(Objects.requireNonNull(KnowledgeArticle.builder()
                .title("t61-工程师文章_" + suffix)
                .category("漏洞分析")
                .summary("标签写权用例")
                .content("正文")
                .authorId(engineerUserId)
                .status("published")
                .build()));

        leaderArticle = knowledgeArticleRepository.save(Objects.requireNonNull(KnowledgeArticle.builder()
                .title("t61-组长文章_" + suffix)
                .category("漏洞分析")
                .summary("标签写权用例")
                .content("正文")
                .authorId(leaderUserId)
                .status("published")
                .build()));

        engineerDocument = documentRepository.save(Objects.requireNonNull(Document.builder()
                .title("t61-工程师文档_" + suffix)
                .category("报告")
                .summary("标签写权用例")
                .content("正文")
                .authorId(engineerUserId)
                .createdBy(engineerUserId)
                .status("draft")
                .build()));

        leaderDocument = documentRepository.save(Objects.requireNonNull(Document.builder()
                .title("t61-组长文档_" + suffix)
                .category("报告")
                .summary("标签写权用例")
                .content("正文")
                .authorId(leaderUserId)
                .createdBy(leaderUserId)
                .status("draft")
                .build()));

        tool = toolRepository.save(Objects.requireNonNull(Tool.builder()
                .name("t61-工具_" + suffix)
                .category("分析工具")
                .description("标签写权用例")
                .isOfficial(false)
                .createdBy(adminUserId)
                .build()));

        engineerAsset = assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name("t61-工程师资产_" + suffix)
                .code("T61-ENG-" + suffix)
                .targetType("Web应用")
                .targetSource("internal")
                .priority("P1")
                .status("active")
                .ownerId(engineerUserId)
                .build()));

        leaderAsset = assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name("t61-组长资产_" + suffix)
                .code("T61-LEAD-" + suffix)
                .targetType("Web应用")
                .targetSource("internal")
                .priority("P2")
                .status("active")
                .ownerId(leaderUserId)
                .build()));
    }

    // ------------------------------------------------------------------ 知识库

    @Test
    @DisplayName("知识库标签：工程师改他人文章 403、改自己文章 200、管理层改他人 200")
    void knowledgeTags_authorOrManager() throws Exception {
        mockMvc.perform(post("/api/v1/knowledge/" + leaderArticle.getId() + "/tags")
                        .header("Authorization", engineerToken)
                        .param("tag", "t61-越权"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/v1/knowledge/" + leaderArticle.getId() + "/tags")
                        .header("Authorization", engineerToken)
                        .param("tag", "t61-越权"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/knowledge/" + engineerArticle.getId() + "/tags")
                        .header("Authorization", engineerToken)
                        .param("tag", "t61-自有"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/knowledge/" + engineerArticle.getId() + "/tags")
                        .header("Authorization", leaderToken)
                        .param("tag", "t61-组长代加"))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/knowledge/" + engineerArticle.getId() + "/tags")
                        .header("Authorization", adminToken)
                        .param("tag", "t61-组长代加"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("知识库标签：文章不存在 → 404（不是 403）")
    void knowledgeTags_missingArticleReturns404() throws Exception {
        mockMvc.perform(post("/api/v1/knowledge/" + UUID.randomUUID() + "/tags")
                        .header("Authorization", engineerToken)
                        .param("tag", "t61-孤儿"))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/knowledge/" + UUID.randomUUID() + "/tags")
                        .header("Authorization", adminToken)
                        .param("tag", "t61-孤儿"))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ 文档

    @Test
    @DisplayName("文档标签：工程师改他人文档 403、改自己文档 201、管理层改他人 200")
    void documentTags_authorOrManager() throws Exception {
        mockMvc.perform(post("/api/v1/documents/" + leaderDocument.getId() + "/tags")
                        .header("Authorization", engineerToken)
                        .param("tag", "t61-越权"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/documents/" + engineerDocument.getId() + "/tags")
                        .header("Authorization", engineerToken)
                        .param("tag", "t61-自有"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/documents/" + engineerDocument.getId() + "/tags")
                        .header("Authorization", adminToken)
                        .param("tag", "t61-管理员代加"))
                .andExpect(status().isCreated());

        // t65：标签必须存在且属于该文档；不存在的 tagId 现在返回 404（此前 deleteById 恒 200）
        mockMvc.perform(delete("/api/v1/documents/" + engineerDocument.getId() + "/tags/" + UUID.randomUUID())
                        .header("Authorization", engineerToken))
                .andExpect(status().isNotFound());

        // 正常路径不变：删除真实存在的标签 → 200
        String createdTagId = objectMapper.readTree(
                        mockMvc.perform(post("/api/v1/documents/" + engineerDocument.getId() + "/tags")
                                        .header("Authorization", engineerToken)
                                        .param("tag", "t65-可删除"))
                                .andReturn().getResponse().getContentAsString())
                .path("data").path("id").asText();
        mockMvc.perform(delete("/api/v1/documents/" + engineerDocument.getId() + "/tags/" + createdTagId)
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("文档标签：非作者删他人文档标签 403；文档不存在 → 404")
    void documentTags_forbiddenAndMissing() throws Exception {
        mockMvc.perform(delete("/api/v1/documents/" + leaderDocument.getId() + "/tags/" + UUID.randomUUID())
                        .header("Authorization", engineerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/documents/" + UUID.randomUUID() + "/tags")
                        .header("Authorization", engineerToken)
                        .param("tag", "t61-孤儿"))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ 工具 / 资产（口径不回归）

    @Test
    @DisplayName("工具标签维持 t55 口径：工程师 403、管理员 200")
    void toolTags_unchangedFromT55() throws Exception {
        mockMvc.perform(post("/api/v1/tools/" + tool.getId() + "/tags")
                        .header("Authorization", engineerToken)
                        .param("tag", "t61-tool"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/tools/" + tool.getId() + "/tags")
                        .header("Authorization", adminToken)
                        .param("tag", "t61-tool"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("资产标签维持 t49 口径：工程师改他人资产 403、改自己资产 201")
    void assetTags_unchangedFromT49() throws Exception {
        mockMvc.perform(post("/api/v1/assets/" + leaderAsset.getId() + "/tags")
                        .header("Authorization", engineerToken)
                        .param("tag", "t61-asset"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/assets/" + engineerAsset.getId() + "/tags")
                        .header("Authorization", engineerToken)
                        .param("tag", "t61-asset"))
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------ 查询保持开放

    @Test
    @DisplayName("标签查询：知识库/文档/工具对工程师 200；资产标签跟随资产读范围（t46），不可见资产 403")
    void tagQueries_openToAllUsers() throws Exception {
        mockMvc.perform(get("/api/v1/knowledge/" + leaderArticle.getId() + "/tags")
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/documents/" + leaderDocument.getId() + "/tags")
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/tools/" + tool.getId() + "/tags")
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/assets/" + leaderAsset.getId() + "/tags")
                        .header("Authorization", engineerToken))
                .andExpect(status().isForbidden());
    }
}
