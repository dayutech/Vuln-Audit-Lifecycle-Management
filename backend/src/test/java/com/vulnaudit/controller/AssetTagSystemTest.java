package com.vulnaudit.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.AssetGroup;
import com.vulnaudit.entity.AssetGroupRelation;
import com.vulnaudit.entity.Customer;
import com.vulnaudit.entity.Project;
import com.vulnaudit.entity.TagDefinition;
import com.vulnaudit.repository.AssetGroupRelationRepository;
import com.vulnaudit.repository.AssetGroupRepository;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.AssetTagRepository;
import com.vulnaudit.repository.AuditLogRepository;
import com.vulnaudit.repository.CustomerRepository;
import com.vulnaudit.repository.ProjectRepository;
import com.vulnaudit.repository.TagDefinitionRepository;
import com.vulnaudit.service.TagDefinitionService;
import com.vulnaudit.service.impl.GroupToTagMigrator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 标签体系（t141）：分组迁移 + 管理员 CRUD + 挂靠自动打标签 + 标签筛选
 *
 * <p>迁移口径：生产走 {@code V30__asset_groups_to_tags.sql}；本类用 {@link GroupToTagMigrator}
 * （同一套规则、同样幂等的 Java 实现）驱动，因为 H2 测试不执行迁移脚本。两者语义一致，已在 README §64.2 注明。
 */
@DisplayName("标签体系测试")
public class AssetTagSystemTest extends BaseTest {

    private static final String TAGS = "/api/v1/tags";
    private static final String ADMIN_TAGS = "/api/v1/admin/tag-definitions";
    private static final String ASSETS = "/api/v1/assets";
    private static final String CUSTOMERS = "/api/v1/customers";
    private static final String PROJECTS = "/api/v1/projects";

    @Autowired
    private TagDefinitionRepository tagDefinitionRepository;
    @Autowired
    private AssetTagRepository assetTagRepository;
    @Autowired
    private AssetGroupRepository groupRepository;
    @Autowired
    private AssetGroupRelationRepository relationRepository;
    @Autowired
    private AssetRepository assetRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private TagDefinitionService tagDefinitionService;
    @Autowired
    private GroupToTagMigrator migrator;

    private UUID teamId;

    @BeforeEach
    void setUpFixtures() {
        tagDefinitionService.ensureBuiltin();
        com.vulnaudit.entity.Team team = teamRepository.save(Objects.requireNonNull(
                com.vulnaudit.entity.Team.builder()
                        .name("标签团队_" + System.nanoTime())
                        .description("标签用例团队")
                        .leaderId(adminUserId)
                        .createdBy(adminUserId)
                        .build()));
        teamId = team.getId();
    }

    @Autowired
    private com.vulnaudit.repository.TeamRepository teamRepository;

    // ------------------------------------------------------------------ 工具

    private Asset createAssetEntity(String name) {
        return assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name(name)
                .code("T141-" + System.nanoTime())
                .targetType("web")
                .targetSource("internal")
                .priority("P2")
                .status("active")
                .teamId(teamId)
                .ownerId(adminUserId)
                .build()));
    }

    private JsonNode createCustomerViaApi(String name) throws Exception {
        String created = mockMvc.perform(post(CUSTOMERS).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).path("data");
    }

    private JsonNode createProjectViaApi(UUID customerId, String name) throws Exception {
        String created = mockMvc.perform(post(PROJECTS).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"customerId\":\"" + customerId + "\",\"name\":\"" + name
                                + "\",\"managerId\":\"" + adminUserId + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).path("data");
    }

    private String createTagDefinition(String name) throws Exception {
        String created = mockMvc.perform(post(ADMIN_TAGS).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"" + name + "\",\"color\":\"#123456\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).path("data").path("id").asText();
    }

    private TagDefinition builtinTag() {
        return tagDefinitionRepository.findAll().stream().filter(TagDefinition::isBuiltin).findFirst().orElseThrow();
    }

    private boolean assetHasTag(UUID assetId, String tag) {
        return assetTagRepository.findByAssetId(assetId).stream().anyMatch(t -> tag.equals(t.getTag()));
    }

    // ------------------------------------------------------------------ 一、分组 → 标签迁移

    @Test
    @DisplayName("迁移：分组生成同名标签定义，关系转成资产标签，migrated_at 落库")
    void migratesGroupsToTags() {
        Asset asset = createAssetEntity("迁移资产");
        AssetGroup group = groupRepository.save(Objects.requireNonNull(AssetGroup.builder()
                .name("历史分组A").type("self").build()));
        relationRepository.save(Objects.requireNonNull(AssetGroupRelation.builder()
                .assetId(asset.getId()).groupId(group.getId()).build()));

        GroupToTagMigrator.Result result = migrator.migrateOnce();

        assertEquals(1, result.definitionsCreated(), "应新建 1 条标签定义");
        assertEquals(1, result.linksCreated(), "应新建 1 条资产标签");
        assertTrue(tagDefinitionRepository.findByName("历史分组A").isPresent());
        assertFalse(tagDefinitionRepository.findByName("历史分组A").orElseThrow().isBuiltin(),
                "分组迁移出的标签是自定义标签（builtin=false）");
        assertTrue(assetHasTag(asset.getId(), "历史分组A"), "资产应保留分组对应的标签");
        assertNotNull(groupRepository.findById(group.getId()).orElseThrow().getMigratedAt(), "应写迁移水位");
    }

    @Test
    @DisplayName("迁移：重复执行幂等（不重复建定义/关联，不重复标记）")
    void migrationIsIdempotent() {
        Asset asset = createAssetEntity("迁移幂等资产");
        AssetGroup group = groupRepository.save(Objects.requireNonNull(AssetGroup.builder()
                .name("历史分组B").type("self").build()));
        relationRepository.save(Objects.requireNonNull(AssetGroupRelation.builder()
                .assetId(asset.getId()).groupId(group.getId()).build()));

        migrator.migrateOnce();
        GroupToTagMigrator.Result second = migrator.migrateOnce();

        assertEquals(0, second.definitionsCreated(), "第二次不应再建定义");
        assertEquals(0, second.linksCreated(), "第二次不应再建关联");
        assertEquals(0, second.groupsMarked(), "第二次没有待迁移分组");
        assertEquals(1, tagDefinitionRepository.countByName("历史分组B"));
        assertEquals(1, assetTagRepository.findByAssetId(asset.getId()).stream()
                .filter(t -> "历史分组B".equals(t.getTag())).count(), "同一资产同一标签只有一行");
    }

    @Test
    @DisplayName("迁移：已软删的分组不迁移")
    void softDeletedGroupSkipped() {
        AssetGroup group = groupRepository.save(Objects.requireNonNull(AssetGroup.builder()
                .name("已删分组C").type("self").deletedAt(java.time.LocalDateTime.now()).build()));

        GroupToTagMigrator.Result result = migrator.migrateOnce();

        assertEquals(0, result.groupsMarked());
        assertTrue(tagDefinitionRepository.findByName("已删分组C").isEmpty(), "软删分组不应生成标签");
        assertNotNull(groupRepository.findById(group.getId()).orElseThrow().getDeletedAt());
    }

    @Test
    @DisplayName("迁移脚本：V30 具备幂等守卫（建表/唯一索引/NOT EXISTS/迁移水位）")
    void migrationScriptGuards() throws Exception {
        String sql = java.nio.file.Files.readString(
                java.nio.file.Path.of("src/main/resources/db/migration/V30__asset_groups_to_tags.sql"),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS tag_definitions"));
        assertTrue(sql.contains("CREATE UNIQUE INDEX IF NOT EXISTS uk_tag_definitions_name"));
        assertTrue(sql.contains("COLLATE=utf8mb4_unicode_ci"), "遵守 t134 硬规则");
        // t147：分组名 = 标签名是「列 = 列」比较，两侧必须显式 COLLATE（否则默认 general_ci 的实例上 ERROR 1267）
        assertTrue(sql.contains("NOT EXISTS (SELECT 1 FROM tag_definitions t"), "分组→标签定义 去重守卫在");
        assertTrue(sql.contains("t.name COLLATE utf8mb4_unicode_ci = g.name COLLATE utf8mb4_unicode_ci"),
                "分组→标签定义 比较两侧显式 COLLATE");
        assertTrue(sql.contains("t.tag COLLATE utf8mb4_unicode_ci = g.name COLLATE utf8mb4_unicode_ci"),
                "分组关系→资产标签 比较两侧显式 COLLATE");
        assertTrue(sql.contains("NOT EXISTS (SELECT 1 FROM asset_tags t"));
        assertTrue(sql.contains("WHERE deleted_at IS NULL AND migrated_at IS NULL"),
                "只标记未软删、未迁过的分组");
    }

    // ------------------------------------------------------------------ 二、管理员 CRUD

    @Test
    @DisplayName("管理：内置标签改 name → 403；改 color/sortOrder → 200")
    void builtinRenameForbidden() throws Exception {
        TagDefinition builtin = builtinTag();

        mockMvc.perform(put(ADMIN_TAGS + "/" + builtin.getId()).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"被篡改的标签\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(put(ADMIN_TAGS + "/" + builtin.getId()).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"color\":\"#999999\",\"sortOrder\":9}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.color").value("#999999"))
                .andExpect(jsonPath("$.data.sortOrder").value(9));
    }

    @Test
    @DisplayName("管理：新建重名标签 → 409；非 ADMIN 写 → 403")
    void createDuplicateAndPermissions() throws Exception {
        createTagDefinition("重复标签X");
        mockMvc.perform(post(ADMIN_TAGS).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"重复标签X\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post(ADMIN_TAGS).header("Authorization", leaderToken)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"PL标签\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(ADMIN_TAGS + "/" + UUID.randomUUID()).header("Authorization", engineerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(ADMIN_TAGS).header("Authorization", leaderToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("管理：删除内置 → 403；删除被资产引用 → 409 带引用数")
    void deleteRules() throws Exception {
        TagDefinition builtin = builtinTag();
        mockMvc.perform(delete(ADMIN_TAGS + "/" + builtin.getId()).header("Authorization", adminToken))
                .andExpect(status().isForbidden());

        String id = createTagDefinition("被引用标签Y");
        Asset asset = createAssetEntity("引用标签资产");
        tagDefinitionService.applyTags(asset.getId(), java.util.List.of("被引用标签Y"), adminUserId);

        mockMvc.perform(delete(ADMIN_TAGS + "/" + id).header("Authorization", adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("1 个资产")));
    }

    @Test
    @DisplayName("管理：软删后不在 /tags 出现；含软删列表可见；同名不可复用")
    void softDeletedTagHidden() throws Exception {
        String id = createTagDefinition("待删标签Z");
        mockMvc.perform(delete(ADMIN_TAGS + "/" + id).header("Authorization", adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get(TAGS).header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name=='待删标签Z')]", org.hamcrest.Matchers.hasSize(0)));

        mockMvc.perform(get(ADMIN_TAGS).header("Authorization", adminToken)
                        .param("includeDeleted", "true").param("keyword", "待删标签Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(post(ADMIN_TAGS).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"待删标签Z\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("管理：写操作恰好 1 条 UPDATE_TAG_DEFINITION 审计（details 含名称）")
    void tagWritesAuditedOnce() throws Exception {
        createTagDefinition("审计标签W");
        var logs = auditLogRepository.findByAction("UPDATE_TAG_DEFINITION");
        assertEquals(1, logs.size(), "一次写操作应恰好 1 条审计");
        assertTrue(logs.get(0).getDetails().contains("审计标签W"), logs.get(0).getDetails());
    }

    // ------------------------------------------------------------------ 三、自动标签

    @Test
    @DisplayName("自动标签：建资产带 projectId → 自动打上客户名与项目名两个标签")
    void autoTagOnAssetCreate() throws Exception {
        String customerName = "客户名-" + System.nanoTime();
        JsonNode customer = createCustomerViaApi(customerName);
        String projectName = "项目名-" + System.nanoTime();
        JsonNode project = createProjectViaApi(UUID.fromString(customer.path("id").asText()), projectName);

        String created = mockMvc.perform(post(ASSETS).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"自动标签资产\",\"targetType\":\"web\",\"targetSource\":\"internal\","
                                + "\"priority\":\"P2\",\"status\":\"active\",\"teamId\":\"" + teamId + "\","
                                + "\"projectId\":\"" + project.path("id").asText() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        UUID assetId = UUID.fromString(objectMapper.readTree(created).path("data").path("id").asText());

        assertTrue(assetHasTag(assetId, customerName), "应自动打上客户名标签");
        assertTrue(assetHasTag(assetId, projectName), "应自动打上项目名标签");
        assertTrue(tagDefinitionRepository.findByName(customerName).isPresent(), "定义不存在时应自动创建");
        assertFalse(tagDefinitionRepository.findByName(customerName).orElseThrow().isBuiltin(),
                "自动创建的标签是自定义标签");

        // /assets/{id}/tags 也能看到这两个标签
        mockMvc.perform(get(ASSETS + "/" + assetId + "/tags").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.tag=='" + customerName + "')]", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.data[?(@.tag=='" + projectName + "')]", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    @DisplayName("自动标签：追加挂靠同样打标签，且重复挂靠/解绑后重挂不产生重复标签行")
    void autoTagOnLinkIsIdempotent() throws Exception {
        String customerName = "追加客户-" + System.nanoTime();
        JsonNode customer = createCustomerViaApi(customerName);
        JsonNode project = createProjectViaApi(UUID.fromString(customer.path("id").asText()),
                "追加项目-" + System.nanoTime());
        UUID projectId = UUID.fromString(project.path("id").asText());
        // 第二个项目：解除挂靠有「资产必须至少挂靠一个项目」的不变式（t69），故先多挂一个
        JsonNode otherProject = createProjectViaApi(UUID.fromString(customer.path("id").asText()),
                "追加项目2-" + System.nanoTime());

        Asset asset = createAssetEntity("追加挂靠资产");
        String linkBody = "{\"projectId\":\"" + projectId + "\"}";

        String first = mockMvc.perform(post(ASSETS + "/" + asset.getId() + "/project-links")
                        .header("Authorization", adminToken).contentType(APPLICATION_JSON).content(linkBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String linkId = objectMapper.readTree(first).path("data").path("linkId").asText();

        assertTrue(assetHasTag(asset.getId(), customerName));

        mockMvc.perform(post(ASSETS + "/" + asset.getId() + "/project-links")
                        .header("Authorization", adminToken).contentType(APPLICATION_JSON)
                        .content("{\"projectId\":\"" + otherProject.path("id").asText() + "\"}"))
                .andExpect(status().isCreated());

        // 解绑后重挂：标签不自动删除（可能手工加过），重挂不新增重复行
        mockMvc.perform(delete(ASSETS + "/" + asset.getId() + "/project-links/" + linkId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk());
        assertTrue(assetHasTag(asset.getId(), customerName), "解绑不自动删标签");

        mockMvc.perform(post(ASSETS + "/" + asset.getId() + "/project-links")
                        .header("Authorization", adminToken).contentType(APPLICATION_JSON).content(linkBody))
                .andExpect(status().isCreated());

        assertEquals(1, assetTagRepository.findByAssetId(asset.getId()).stream()
                .filter(t -> customerName.equals(t.getTag())).count(), "重复挂靠不应产生重复标签行");
    }

    // ------------------------------------------------------------------ 四、筛选与可见性

    @Test
    @DisplayName("筛选：tags=a,b 为 AND 语义（两标签都命中才返回）")
    void filterByTagsAnd() throws Exception {
        Asset both = createAssetEntity("双标签资产");
        Asset onlyOne = createAssetEntity("单标签资产");
        tagDefinitionService.applyTags(both.getId(), java.util.List.of("AND标签1", "AND标签2"), adminUserId);
        tagDefinitionService.applyTags(onlyOne.getId(), java.util.List.of("AND标签1"), adminUserId);

        mockMvc.perform(get(ASSETS).header("Authorization", adminToken)
                        .param("tags", "AND标签1,AND标签2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id=='" + both.getId() + "')]",
                        org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.data.content[?(@.id=='" + onlyOne.getId() + "')]",
                        org.hamcrest.Matchers.hasSize(0)));

        // 单个标签命中两条
        mockMvc.perform(get(ASSETS).header("Authorization", adminToken).param("tags", "AND标签1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id=='" + both.getId() + "')]",
                        org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.data.content[?(@.id=='" + onlyOne.getId() + "')]",
                        org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    @DisplayName("筛选：tagsAny=a,b 为 OR 语义")
    void filterByTagsAny() throws Exception {
        Asset a = createAssetEntity("OR资产1");
        Asset b = createAssetEntity("OR资产2");
        Asset c = createAssetEntity("OR资产3");
        tagDefinitionService.applyTags(a.getId(), java.util.List.of("OR标签A"), adminUserId);
        tagDefinitionService.applyTags(b.getId(), java.util.List.of("OR标签B"), adminUserId);
        tagDefinitionService.applyTags(c.getId(), java.util.List.of("OR无关"), adminUserId);

        mockMvc.perform(get(ASSETS).header("Authorization", adminToken).param("tagsAny", "OR标签A,OR标签B"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id=='" + a.getId() + "')]",
                        org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.data.content[?(@.id=='" + b.getId() + "')]",
                        org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.data.content[?(@.id=='" + c.getId() + "')]",
                        org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    @DisplayName("筛选：标签结果与可见性取交集（工程师看不到他人资产的标签结果）")
    void filterByTagsRespectsVisibility() throws Exception {
        // 工程师的可见范围来自资产挂靠/团队关系；此处创建一条只属于 admin 团队的资产
        Asset adminOnly = createAssetEntity("管理员专属资产");
        tagDefinitionService.applyTags(adminOnly.getId(), java.util.List.of("可见性标签V"), adminUserId);

        mockMvc.perform(get(ASSETS).header("Authorization", adminToken).param("tags", "可见性标签V"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.id=='" + adminOnly.getId() + "')]",
                        org.hamcrest.Matchers.hasSize(1)));

        mockMvc.perform(get(ASSETS).header("Authorization", engineerToken).param("tags", "可见性标签V"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", org.hamcrest.Matchers.hasSize(0)));

        // /tags 的 assetCount 同样按可见范围收敛
        String adminBody = mockMvc.perform(get(TAGS).header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long adminCount = countTag(adminBody, "可见性标签V");
        assertTrue(adminCount >= 1, "管理员应看到计数: " + adminBody);

        String engineerBody = mockMvc.perform(get(TAGS).header("Authorization", engineerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(0, countTag(engineerBody, "可见性标签V"),
                "工程师的 assetCount 应为 0: " + engineerBody);
    }

    private long countTag(String body, String name) throws Exception {
        JsonNode array = objectMapper.readTree(body).path("data");
        for (JsonNode node : array) {
            if (name.equals(node.path("name").asText())) {
                return node.path("assetCount").asLong(-1);
            }
        }
        return 0;
    }

    @Test
    @DisplayName("下拉：GET /tags 登录即可，内置标签存在且返回 id/name/color/builtin/assetCount")
    void tagsDropdownShape() throws Exception {
        mockMvc.perform(get(TAGS).header("Authorization", engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name=='核心系统')]", org.hamcrest.Matchers.hasSize(1)))
                .andExpect(jsonPath("$.data[?(@.name=='核心系统')].builtin").value(true))
                .andExpect(jsonPath("$.data[?(@.name=='核心系统')].assetCount", org.hamcrest.Matchers.hasSize(1)));
    }

    @Test
    @DisplayName("自动标签：重复挂靠不同项目时标签累积（客户名不重复）")
    void autoTagAccumulatesAcrossProjects() throws Exception {
        String customerName = "多项目客户-" + System.nanoTime();
        JsonNode customer = createCustomerViaApi(customerName);
        UUID customerId = UUID.fromString(customer.path("id").asText());
        JsonNode project1 = createProjectViaApi(customerId, "多项目1-" + System.nanoTime());
        JsonNode project2 = createProjectViaApi(customerId, "多项目2-" + System.nanoTime());

        Asset asset = createAssetEntity("多项目资产");
        for (JsonNode project : java.util.List.of(project1, project2)) {
            mockMvc.perform(post(ASSETS + "/" + asset.getId() + "/project-links")
                            .header("Authorization", adminToken).contentType(APPLICATION_JSON)
                            .content("{\"projectId\":\"" + project.path("id").asText() + "\"}"))
                    .andExpect(status().isCreated());
        }

        assertEquals(1, assetTagRepository.findByAssetId(asset.getId()).stream()
                .filter(t -> customerName.equals(t.getTag())).count(), "客户名标签只应有一行");
        assertTrue(assetHasTag(asset.getId(), project1.path("name").asText()));
        assertTrue(assetHasTag(asset.getId(), project2.path("name").asText()));
    }

    @Test
    @DisplayName("迁移 + 自动标签不冲突：迁移出的标签与自动标签同表同口径")
    void migratedAndAutoTagsCoexist() {
        Asset asset = createAssetEntity("共存资产");
        AssetGroup group = groupRepository.save(Objects.requireNonNull(AssetGroup.builder()
                .name("共存分组").type("self").build()));
        relationRepository.save(Objects.requireNonNull(AssetGroupRelation.builder()
                .assetId(asset.getId()).groupId(group.getId()).build()));

        migrator.migrateOnce();
        int added = tagDefinitionService.applyTags(asset.getId(),
                java.util.List.of("共存分组", "自动补充标签"), adminUserId);

        assertEquals(1, added, "已有标签不重复添加，只加新的那条");
        assertTrue(assetHasTag(asset.getId(), "共存分组"));
        assertTrue(assetHasTag(asset.getId(), "自动补充标签"));
    }

    @Test
    @DisplayName("客户/项目实体存在性：自动标签取的是实体 name 而不是 code")
    void autoTagUsesNames() throws Exception {
        Customer customer = customerRepository.save(Objects.requireNonNull(Customer.builder()
                .code("CUST-T141-" + System.nanoTime())
                .name("标签用客户名")
                .status("active")
                .createdBy(adminUserId)
                .build()));
        Project project = projectRepository.save(Objects.requireNonNull(Project.builder()
                .code("PRJ-T141-" + System.nanoTime())
                .customerId(customer.getId())
                .name("标签用项目名")
                .managerId(adminUserId)
                .status("DRAFT")
                .build()));

        Asset asset = createAssetEntity("取名资产");
        mockMvc.perform(post(ASSETS + "/" + asset.getId() + "/project-links")
                        .header("Authorization", adminToken).contentType(APPLICATION_JSON)
                        .content("{\"projectId\":\"" + project.getId() + "\"}"))
                .andExpect(status().isCreated());

        assertTrue(assetHasTag(asset.getId(), "标签用客户名"));
        assertTrue(assetHasTag(asset.getId(), "标签用项目名"));
        assertFalse(assetHasTag(asset.getId(), customer.getCode()), "不应把 code 当标签");
        assertFalse(assetHasTag(asset.getId(), project.getCode()), "不应把 code 当标签");
    }
}
