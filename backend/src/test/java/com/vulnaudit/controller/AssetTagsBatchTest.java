package com.vulnaudit.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.Customer;
import com.vulnaudit.entity.Project;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.AssetTagRepository;
import com.vulnaudit.repository.CustomerRepository;
import com.vulnaudit.repository.ProjectRepository;
import com.vulnaudit.service.TagDefinitionService;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 资产列表内联标签（t145）
 *
 * <p>核心约束：**列表接口的总查询数不随页大小增长**（t143 报告：前端一页 20 行 = 20 个 `GET /assets/{id}/tags`）。
 * 实现口径：`asset_tags` 按 assetId 集合批量 + `tag_definitions` 按 name 集合批量，固定 2 次查询。
 *
 * <p>查询数用 Hibernate Statistics（`generate_statistics: true`，见 `application-test.yml`）在**同一事务内**
 * 对比 size=5 与 size=20 两次请求的 `prepareStatementCount` 增量。
 */
@DisplayName("资产标签批量内联测试")
public class AssetTagsBatchTest extends BaseTest {

    private static final String ASSETS = "/api/v1/assets";
    private static final String CUSTOMERS = "/api/v1/customers";
    private static final String PROJECTS = "/api/v1/projects";

    @Autowired
    private AssetRepository assetRepository;
    @Autowired
    private AssetTagRepository assetTagRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private TagDefinitionService tagDefinitionService;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private UUID teamId;

    @BeforeEach
    void setUpFixtures() {
        tagDefinitionService.ensureBuiltin();
        com.vulnaudit.entity.Team team = teamRepository.save(Objects.requireNonNull(
                com.vulnaudit.entity.Team.builder()
                        .name("批量标签团队_" + System.nanoTime())
                        .description("t145")
                        .leaderId(adminUserId)
                        .createdBy(adminUserId)
                        .build()));
        teamId = team.getId();
    }

    @Autowired
    private com.vulnaudit.repository.TeamRepository teamRepository;

    // ------------------------------------------------------------------ 工具

    private Asset createAsset(String name) {
        return assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name(name)
                .code("T145-" + System.nanoTime())
                .targetType("web")
                .targetSource("internal")
                .priority("P2")
                .status("active")
                .teamId(teamId)
                .ownerId(adminUserId)
                .build()));
    }

    private JsonNode listAssets(String query) throws Exception {
        String body = mockMvc.perform(get(ASSETS + query).header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    private long queryCount() {
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        return stats.getPrepareStatementCount();
    }

    // ------------------------------------------------------------------ 一、列表内联

    @Test
    @DisplayName("列表：每项都有 tags 字段（无标签为空数组，不是 null）")
    void listIncludesTagsArrayForEveryRow() throws Exception {
        Asset tagged = createAsset("有标签资产");
        Asset untagged = createAsset("无标签资产");
        tagDefinitionService.applyTags(tagged.getId(), List.of("内联标签A"), adminUserId);

        JsonNode data = listAssets("?page=0&size=50");

        for (JsonNode item : data.path("content")) {
            assertTrue(item.has("tags"), "每项都必须有 tags 字段: " + item);
            assertTrue(item.path("tags").isArray(), "tags 必须是数组: " + item);
            if (item.path("id").asText().equals(untagged.getId().toString())) {
                assertEquals(0, item.path("tags").size(), "无标签资产返回空数组");
            }
        }
        JsonNode taggedItem = findById(data.path("content"), tagged.getId());
        assertNotNull(taggedItem, "应能在列表里找到有标签资产");
        assertEquals(1, taggedItem.path("tags").size());
        assertEquals("内联标签A", taggedItem.path("tags").get(0).path("name").asText());
        assertTrue(taggedItem.path("tags").get(0).has("builtin"), "应含 builtin");
        assertTrue(taggedItem.path("tags").get(0).has("color"), "应含 color（可为 null）");
    }

    @Test
    @DisplayName("列表内联的 tags 与 GET /assets/{id}/tags 一致")
    void listTagsMatchSingleAssetEndpoint() throws Exception {
        Asset asset = createAsset("一致性资产");
        tagDefinitionService.applyTags(asset.getId(), List.of("一致性标签1", "一致性标签2"), adminUserId);

        JsonNode listItem = findById(listAssets("?page=0&size=50").path("content"), asset.getId());
        assertNotNull(listItem);

        String single = mockMvc.perform(get(ASSETS + "/" + asset.getId() + "/tags")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<String> singleNames = new ArrayList<>();
        objectMapper.readTree(single).path("data").forEach(node -> singleNames.add(node.path("tag").asText()));

        List<String> listNames = new ArrayList<>();
        listItem.path("tags").forEach(node -> listNames.add(node.path("name").asText()));

        assertEquals(singleNames.size(), listNames.size(), "两边标签数应一致");
        assertTrue(listNames.containsAll(singleNames) && singleNames.containsAll(listNames),
                "两边标签名集合应一致: list=" + listNames + " single=" + singleNames);
    }

    @Test
    @DisplayName("性能：页大小 5 与 20 的查询数相同（不随页大小增长）")
    void queryCountDoesNotGrowWithPageSize() throws Exception {
        for (int i = 0; i < 25; i++) {
            Asset asset = createAsset("批量资产" + i);
            tagDefinitionService.applyTags(asset.getId(), List.of("批标签" + (i % 3)), adminUserId);
        }

        // 预热（避免首次请求的元数据/统计初始化差异）
        listAssets("?page=0&size=5");

        long before5 = queryCount();
        JsonNode small = listAssets("?page=0&size=5");
        long delta5 = queryCount() - before5;

        long before20 = queryCount();
        JsonNode large = listAssets("?page=0&size=20");
        long delta20 = queryCount() - before20;

        assertEquals(5, small.path("content").size(), "size=5 应返回 5 条");
        assertEquals(20, large.path("content").size(), "size=20 应返回 20 条");
        assertEquals(delta5, delta20,
                "查询数必须与页大小无关（size=5: " + delta5 + " 次，size=20: " + delta20 + " 次）");
        assertTrue(delta20 <= 15, "单次列表请求查询数应保持常数级，实测 " + delta20 + " 次");
    }

    @Test
    @DisplayName("列表：与 tags 筛选同时使用时 tags 字段仍正确")
    void listWithTagFilterStillHasTags() throws Exception {
        Asset both = createAsset("筛选双标签");
        Asset onlyOne = createAsset("筛选单标签");
        tagDefinitionService.applyTags(both.getId(), List.of("筛选标签X", "筛选标签Y"), adminUserId);
        tagDefinitionService.applyTags(onlyOne.getId(), List.of("筛选标签X"), adminUserId);

        JsonNode data = listAssets("?tags=筛选标签X&page=0&size=50");
        JsonNode bothItem = findById(data.path("content"), both.getId());
        JsonNode oneItem = findById(data.path("content"), onlyOne.getId());
        assertNotNull(bothItem);
        assertNotNull(oneItem);
        assertEquals(2, bothItem.path("tags").size());
        assertEquals(1, oneItem.path("tags").size());
    }

    @Test
    @DisplayName("列表：与 tagsAny 筛选同时使用时 tags 字段仍正确")
    void listWithTagsAnyFilterStillHasTags() throws Exception {
        Asset a = createAsset("OR批量A");
        Asset b = createAsset("OR批量B");
        tagDefinitionService.applyTags(a.getId(), List.of("批量OR标签1"), adminUserId);
        tagDefinitionService.applyTags(b.getId(), List.of("批量OR标签2"), adminUserId);

        JsonNode data = listAssets("?tagsAny=批量OR标签1,批量OR标签2&page=0&size=50");
        assertNotNull(findById(data.path("content"), a.getId()));
        assertNotNull(findById(data.path("content"), b.getId()));
        assertEquals(1, findById(data.path("content"), a.getId()).path("tags").size());
    }

    // ------------------------------------------------------------------ 二、批量端点

    @Test
    @DisplayName("批量端点：返回 {assetId: [tags]}，多 id 一次取回")
    void batchEndpointReturnsMap() throws Exception {
        Asset a = createAsset("批量端点A");
        Asset b = createAsset("批量端点B");
        tagDefinitionService.applyTags(a.getId(), List.of("端点标签1", "端点标签2"), adminUserId);
        tagDefinitionService.applyTags(b.getId(), List.of("端点标签3"), adminUserId);

        String body = mockMvc.perform(get(ASSETS + "/tags")
                        .header("Authorization", adminToken)
                        .param("ids", a.getId() + "," + b.getId()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode data = objectMapper.readTree(body).path("data");
        assertEquals(2, data.path(a.getId().toString()).size());
        assertEquals(1, data.path(b.getId().toString()).size());
        assertEquals("端点标签1", data.path(a.getId().toString()).get(0).path("name").asText());
    }

    @Test
    @DisplayName("批量端点：超过 100 个 id → 400")
    void batchEndpointRejectsMoreThan100Ids() throws Exception {
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < 101; i++) {
            if (i > 0) {
                ids.append(",");
            }
            ids.append(UUID.randomUUID());
        }
        mockMvc.perform(get(ASSETS + "/tags").header("Authorization", adminToken)
                        .param("ids", ids.toString()))
                .andExpect(status().isBadRequest());

        // 正好 100 个不报错（全部不存在 → 空 map，不泄露存在性）
        StringBuilder hundred = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            if (i > 0) {
                hundred.append(",");
            }
            hundred.append(UUID.randomUUID());
        }
        mockMvc.perform(get(ASSETS + "/tags").header("Authorization", adminToken)
                        .param("ids", hundred.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isMap());
    }

    @Test
    @DisplayName("批量端点：不可见/不存在的 id 被跳过（不报错、不泄露）")
    void batchEndpointSkipsInvisibleAssets() throws Exception {
        Asset adminOnly = createAsset("不可见标签资产");
        tagDefinitionService.applyTags(adminOnly.getId(), List.of("不可见标签V"), adminUserId);

        // 工程师看不到该资产 → 结果里没有这个 key，也不报错
        String body = mockMvc.perform(get(ASSETS + "/tags").header("Authorization", engineerToken)
                        .param("ids", adminOnly.getId() + "," + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).path("data");
        assertEquals(0, data.size(), "不可见与不存在的 id 都应被跳过: " + data);

        // 管理员能看到
        String adminBody = mockMvc.perform(get(ASSETS + "/tags").header("Authorization", adminToken)
                        .param("ids", adminOnly.getId().toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(1, objectMapper.readTree(adminBody).path("data").path(adminOnly.getId().toString()).size());
    }

    @Test
    @DisplayName("批量端点：非法 id → 400；空 ids → 空 map")
    void batchEndpointValidation() throws Exception {
        mockMvc.perform(get(ASSETS + "/tags").header("Authorization", adminToken)
                        .param("ids", "not-a-uuid"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(ASSETS + "/tags").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isMap());
    }

    // ------------------------------------------------------------------ 三、其他列表

    @Test
    @DisplayName("项目资产列表：每项也带 tags（一次批量）")
    void projectAssetsListIncludesTags() throws Exception {
        Customer customer = customerRepository.save(Objects.requireNonNull(Customer.builder()
                .code("CUST-T145-" + System.nanoTime()).name("批量客户").status("active")
                .createdBy(adminUserId).build()));
        Project project = projectRepository.save(Objects.requireNonNull(Project.builder()
                .code("PRJ-T145-" + System.nanoTime()).customerId(customer.getId()).name("批量项目")
                .managerId(adminUserId).status("ACTIVE").build()));

        Asset asset = createAsset("项目资产带标签");
        mockMvc.perform(post(ASSETS + "/" + asset.getId() + "/project-links")
                        .header("Authorization", adminToken).contentType(APPLICATION_JSON)
                        .content("{\"projectId\":\"" + project.getId() + "\"}"))
                .andExpect(status().isCreated());

        String body = mockMvc.perform(get(PROJECTS + "/" + project.getId() + "/assets")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode content = objectMapper.readTree(body).path("data").path("content");
        assertTrue(content.size() >= 1);
        JsonNode item = content.get(0);
        assertTrue(item.has("tags"), "项目资产项必须有 tags: " + item);
        assertTrue(item.path("tags").size() >= 2, "应至少含客户名与项目名两个自动标签: " + item);
    }

    @Test
    @DisplayName("客户 summary：recentAssets 每项也带 tags")
    void customerSummaryRecentAssetsIncludeTags() throws Exception {
        Customer customer = customerRepository.save(Objects.requireNonNull(Customer.builder()
                .code("CUST-T145S-" + System.nanoTime()).name("汇总批量客户").status("active")
                .createdBy(adminUserId).build()));
        Project project = projectRepository.save(Objects.requireNonNull(Project.builder()
                .code("PRJ-T145S-" + System.nanoTime()).customerId(customer.getId()).name("汇总批量项目")
                .managerId(adminUserId).status("ACTIVE").build()));
        Asset asset = createAsset("汇总带标签资产");
        mockMvc.perform(post(ASSETS + "/" + asset.getId() + "/project-links")
                        .header("Authorization", adminToken).contentType(APPLICATION_JSON)
                        .content("{\"projectId\":\"" + project.getId() + "\"}"))
                .andExpect(status().isCreated());

        String body = mockMvc.perform(get(CUSTOMERS + "/" + customer.getId() + "/summary")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode recent = objectMapper.readTree(body).path("data").path("recentAssets");
        assertTrue(recent.size() >= 1, "应有 recentAssets");
        JsonNode item = recent.get(0);
        assertTrue(item.has("tags"), "recentAssets 项必须有 tags: " + item);
        assertTrue(item.path("tags").size() >= 2, "应含客户名与项目名自动标签");
    }

    // ------------------------------------------------------------------ 辅助

    private JsonNode findById(JsonNode content, UUID id) {
        for (JsonNode node : content) {
            if (id.toString().equals(node.path("id").asText())) {
                return node;
            }
        }
        return null;
    }
}
