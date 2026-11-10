package com.vulnaudit.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.AssetProjectLink;
import com.vulnaudit.entity.Customer;
import com.vulnaudit.entity.Project;
import com.vulnaudit.repository.AssetProjectLinkRepository;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.AssetTagRepository;
import com.vulnaudit.repository.AuditLogRepository;
import com.vulnaudit.repository.CustomerRepository;
import com.vulnaudit.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 项目侧批量添加/移除资产 + 可选资产列表（t154）
 *
 * <p>与「资产侧挂靠」相对：项目详情页需要一次挂多个资产、一次摘多个资产，以及「还没挂靠本项目」的选择器数据。
 *
 * <p>关键语义：
 * <ul>
 *   <li>**不整体失败**：资产不存在/已软删/无权限 → 记 {@code failed}，其余照常；</li>
 *   <li>**幂等**：已挂靠同项目同期次 → {@code skipped}；</li>
 *   <li>**不变式**：移除后某资产再无挂靠 → **409 且一条都不删**；</li>
 *   <li>可见性/软删口径不绕过：batch 结果与选择器都按 {@code visibleAssetIds} 收敛，已软删资产视为不存在。</li>
 * </ul>
 */
@DisplayName("项目批量资产操作测试")
public class ProjectBulkAssetsTest extends BaseTest {

    private static final String PROJECTS = "/api/v1/projects";

    @Autowired
    private AssetRepository assetRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private AssetProjectLinkRepository linkRepository;
    @Autowired
    private AssetTagRepository assetTagRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private com.vulnaudit.repository.TeamRepository teamRepository;

    private Project project;
    private Customer customer;
    private UUID teamId;

    @BeforeEach
    void setUpFixtures() {
        customer = customerRepository.save(Objects.requireNonNull(Customer.builder()
                .code("CUST-T154-" + System.nanoTime())
                .name("批量客户")
                .status("active")
                .createdBy(adminUserId)
                .build()));
        project = projectRepository.save(Objects.requireNonNull(Project.builder()
                .code("PRJ-T154-" + System.nanoTime())
                .customerId(customer.getId())
                .name("批量项目")
                .managerId(adminUserId)
                .status("ACTIVE")
                .build()));
        teamId = teamRepository.save(Objects.requireNonNull(com.vulnaudit.entity.Team.builder()
                .name("批量团队_" + System.nanoTime())
                .description("t154")
                .leaderId(adminUserId)
                .createdBy(adminUserId)
                .build())).getId();
    }

    // ------------------------------------------------------------------ 工具

    private Asset newAsset(String name) {
        return assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name(name)
                .code("A154-" + System.nanoTime())
                .targetType("web")
                .targetSource("internal")
                .priority("P2")
                .status("active")
                .teamId(teamId)
                .ownerId(adminUserId)
                .build()));
    }

    /** 资产已在其它项目挂靠，保证「移除后仍有挂靠」 */
    private void linkToOtherProject(Asset asset) {
        Project other = projectRepository.save(Objects.requireNonNull(Project.builder()
                .code("PRJ-T154-O-" + System.nanoTime())
                .customerId(customer.getId())
                .name("其它项目")
                .managerId(adminUserId)
                .status("ACTIVE")
                .build()));
        linkRepository.saveAndFlush(Objects.requireNonNull(AssetProjectLink.builder()
                .assetId(asset.getId())
                .projectId(other.getId())
                .customerId(customer.getId())
                .linkType("TARGET")
                .linkedBy(adminUserId)
                .build()));
    }

    private JsonNode bulkAdd(String token, String body) throws Exception {
        String response = mockMvc.perform(post(PROJECTS + "/" + project.getId() + "/assets")
                        .header("Authorization", token)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    private String idsBody(List<UUID> ids) {
        StringBuilder body = new StringBuilder("{\"assetIds\":[");
        for (int i = 0; i < ids.size(); i++) {
            if (i > 0) {
                body.append(",");
            }
            body.append("\"").append(ids.get(i)).append("\"");
        }
        return body.append("]}").toString();
    }

    // ------------------------------------------------------------------ 一、批量添加

    @Test
    @DisplayName("批量添加：2 个资产 → added=2，挂靠落库，且自动打客户名/项目名标签")
    void bulkAddCreatesLinksAndTags() throws Exception {
        Asset a = newAsset("批量资产A");
        Asset b = newAsset("批量资产B");

        JsonNode result = bulkAdd(adminToken, idsBody(List.of(a.getId(), b.getId())));

        assertEquals(2, result.path("added").asInt());
        assertEquals(0, result.path("skipped").asInt());
        assertEquals(0, result.path("failed").size());
        assertTrue(linkRepository.existsByAssetIdAndProjectIdAndPhaseId(a.getId(), project.getId(), null));
        assertTrue(linkRepository.existsByAssetIdAndProjectIdAndPhaseId(b.getId(), project.getId(), null));

        // t141 自动标签：客户名 + 项目名
        List<String> tags = assetTagRepository.findTagsByAssetId(a.getId());
        assertTrue(tags.contains(customer.getName()), "应自动打客户名标签: " + tags);
        assertTrue(tags.contains(project.getName()), "应自动打项目名标签: " + tags);
    }

    @Test
    @DisplayName("批量添加：不存在/已软删资产 → 计入 failed，其余照常成功")
    void bulkAddToleratesMissingAssets() throws Exception {
        Asset ok = newAsset("存在资产");
        Asset softDeleted = newAsset("已删资产");
        assetRepository.deleteById(softDeleted.getId());
        // 用原生 UPDATE 模拟软删（t140：资产软删写 deleted_at）
        entityManager.createNativeQuery("update assets set deleted_at = now() where id = :id")
                .setParameter("id", softDeleted.getId()).executeUpdate();
        entityManager.clear();

        JsonNode result = bulkAdd(adminToken, idsBody(List.of(ok.getId(), softDeleted.getId(), UUID.randomUUID())));

        assertEquals(1, result.path("added").asInt());
        assertEquals(2, result.path("failed").size(), "不存在 + 已软删 各一条: " + result);
        for (JsonNode failed : result.path("failed")) {
            assertTrue(failed.path("reason").asText().contains("不存在"),
                    "原因应说明资产不存在/已删除: " + failed);
        }
        assertTrue(linkRepository.existsByAssetIdAndProjectIdAndPhaseId(ok.getId(), project.getId(), null),
                "其余资产仍应成功挂靠");
    }

    @Test
    @DisplayName("批量添加：重复挂靠同项目 → skipped（幂等，不报错）")
    void bulkAddIsIdempotent() throws Exception {
        Asset asset = newAsset("重复挂靠资产");
        String body = idsBody(List.of(asset.getId()));

        assertEquals(1, bulkAdd(adminToken, body).path("added").asInt());
        JsonNode second = bulkAdd(adminToken, body);

        assertEquals(0, second.path("added").asInt());
        assertEquals(1, second.path("skipped").asInt());
        assertEquals(0, second.path("failed").size());
        assertEquals(1, linkRepository.findByAssetId(asset.getId()).size(), "不应产生重复挂靠行");
    }

    @Test
    @DisplayName("批量添加：assetIds 为空 / 超 100 → 400")
    void bulkAddValidatesSize() throws Exception {
        mockMvc.perform(post(PROJECTS + "/" + project.getId() + "/assets")
                        .header("Authorization", adminToken).contentType(APPLICATION_JSON)
                        .content("{\"assetIds\":[]}"))
                .andExpect(status().isBadRequest());

        StringBuilder big = new StringBuilder("{\"assetIds\":[");
        for (int i = 0; i < 101; i++) {
            if (i > 0) {
                big.append(",");
            }
            big.append("\"").append(UUID.randomUUID()).append("\"");
        }
        big.append("]}");
        mockMvc.perform(post(PROJECTS + "/" + project.getId() + "/assets")
                        .header("Authorization", adminToken).contentType(APPLICATION_JSON)
                        .content(big.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("批量添加：项目不存在 → 404")
    void bulkAddRequiresProject() throws Exception {
        Asset asset = newAsset("项目不存在资产");
        mockMvc.perform(post(PROJECTS + "/" + UUID.randomUUID() + "/assets")
                        .header("Authorization", adminToken).contentType(APPLICATION_JSON)
                        .content(idsBody(List.of(asset.getId()))))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("批量添加：无写权限的资产 → failed（原因可读），有权限的仍成功")
    void bulkAddReportsPermissionFailures() throws Exception {
        Asset adminOwned = newAsset("管理员资产");
        Asset engineerOwned = assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name("工程师资产")
                .code("A154-E-" + System.nanoTime())
                .targetType("web").targetSource("internal").priority("P2").status("active")
                .teamId(teamId).ownerId(engineerUserId)
                .build()));

        // 工程师只能挂自己负责的资产
        JsonNode mixed = bulkAdd(engineerToken, idsBody(List.of(engineerOwned.getId(), adminOwned.getId())));

        assertEquals(1, mixed.path("added").asInt(), "自己的资产应成功: " + mixed);
        assertEquals(1, mixed.path("failed").size());
        assertTrue(mixed.path("failed").get(0).path("reason").asText().length() > 0);
    }

    // ------------------------------------------------------------------ 二、批量移除

    @Test
    @DisplayName("批量移除：removed=1，挂靠消失，其它项目挂靠不受影响")
    void bulkRemoveDeletesOnlyThisProjectLinks() throws Exception {
        Asset asset = newAsset("可移除资产");
        bulkAdd(adminToken, idsBody(List.of(asset.getId())));
        linkToOtherProject(asset);

        String body = mockMvc.perform(delete(PROJECTS + "/" + project.getId() + "/assets")
                        .header("Authorization", adminToken).contentType(APPLICATION_JSON)
                        .content(idsBody(List.of(asset.getId()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertEquals(1, objectMapper.readTree(body).path("data").path("removed").asInt());
        assertFalse(linkRepository.existsByAssetIdAndProjectIdAndPhaseId(asset.getId(), project.getId(), null));
        assertEquals(1, linkRepository.findByAssetId(asset.getId()).size(), "其它项目的挂靠应保留");
    }

    @Test
    @DisplayName("批量移除：会导致资产零挂靠 → 409 且**未删除任何记录**（整体回滚）")
    void bulkRemoveRejectsZeroLinkAsset() throws Exception {
        Asset lonely = newAsset("零挂靠资产");
        Asset other = newAsset("正常资产");
        bulkAdd(adminToken, idsBody(List.of(lonely.getId(), other.getId())));
        linkToOtherProject(other); // other 移除后仍有挂靠，lonely 没有

        mockMvc.perform(delete(PROJECTS + "/" + project.getId() + "/assets")
                        .header("Authorization", adminToken).contentType(APPLICATION_JSON)
                        .content(idsBody(List.of(lonely.getId(), other.getId()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(lonely.getCode())));

        assertTrue(linkRepository.existsByAssetIdAndProjectIdAndPhaseId(lonely.getId(), project.getId(), null),
                "409 时不应删除任何挂靠（lonely 仍在）");
        assertTrue(linkRepository.existsByAssetIdAndProjectIdAndPhaseId(other.getId(), project.getId(), null),
                "409 时不应删除任何挂靠（other 仍在）");
    }

    @Test
    @DisplayName("批量移除：未挂靠本项目的资产 → 幂等跳过（removed=0，不报错）")
    void bulkRemoveOfUnlinkedIsNoop() throws Exception {
        Asset asset = newAsset("未挂靠资产");
        linkToOtherProject(asset);

        String body = mockMvc.perform(delete(PROJECTS + "/" + project.getId() + "/assets")
                        .header("Authorization", adminToken).contentType(APPLICATION_JSON)
                        .content(idsBody(List.of(asset.getId()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(0, objectMapper.readTree(body).path("data").path("removed").asInt());
    }

    // ------------------------------------------------------------------ 三、可选资产列表

    @Test
    @DisplayName("可选资产：只返回未挂靠本项目且可见的资产，linkCount 正确")
    void availableAssetsExcludesLinked() throws Exception {
        Asset free = newAsset("可选资产");
        Asset linked = newAsset("已挂靠资产");
        bulkAdd(adminToken, idsBody(List.of(linked.getId())));
        linkToOtherProject(free); // free 挂在别的项目 → 仍应出现在候选里，linkCount=1

        String body = mockMvc.perform(get(PROJECTS + "/" + project.getId() + "/available-assets")
                        .param("size", "100").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode content = objectMapper.readTree(body).path("data").path("content");

        boolean sawFree = false;
        for (JsonNode item : content) {
            assertFalse(item.path("id").asText().equals(linked.getId().toString()),
                    "已挂靠本项目的资产不应出现在候选里");
            if (item.path("id").asText().equals(free.getId().toString())) {
                sawFree = true;
                assertEquals(1, item.path("linkCount").asInt(), "应带上当前挂靠数");
                assertTrue(item.has("code") && item.has("name") && item.has("targetType") && item.has("status"));
            }
        }
        assertTrue(sawFree, "未挂靠本项目的资产应出现在候选里");
    }

    @Test
    @DisplayName("可选资产：可见性口径不被绕过（工程师看不到管理员专属资产）")
    void availableAssetsRespectsVisibility() throws Exception {
        Asset adminOnly = newAsset("管理员专属候选");

        String adminBody = mockMvc.perform(get(PROJECTS + "/" + project.getId() + "/available-assets")
                        .param("size", "100").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertTrue(adminBody.contains(adminOnly.getId().toString()), "管理员应能看到自己的资产");

        String engineerBody = mockMvc.perform(get(PROJECTS + "/" + project.getId() + "/available-assets")
                        .param("size", "100").header("Authorization", engineerToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertFalse(engineerBody.contains(adminOnly.getId().toString()),
                "工程师不应看到不可见资产: " + engineerBody);
    }

    @Test
    @DisplayName("可选资产：分页正确（total/content 一致）")
    void availableAssetsPaginates() throws Exception {
        for (int i = 0; i < 5; i++) {
            newAsset("候选资产" + i);
        }
        String first = mockMvc.perform(get(PROJECTS + "/" + project.getId() + "/available-assets")
                        .param("page", "0").param("size", "2").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(first).path("data");
        assertTrue(data.path("totalElements").asInt() >= 5);
        assertEquals(2, data.path("content").size());
        assertEquals(0, data.path("currentPage").asInt());
        assertEquals(2, data.path("pageSize").asInt());
    }

    @Test
    @DisplayName("可选资产：keyword 按名称/编号模糊筛选")
    void availableAssetsFiltersByKeyword() throws Exception {
        Asset target = assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name("关键字唯一名TT")
                .code("KW-T154-" + System.nanoTime())
                .targetType("web").targetSource("internal").priority("P2").status("active")
                .teamId(teamId).ownerId(adminUserId)
                .build()));
        newAsset("其它候选");

        String body = mockMvc.perform(get(PROJECTS + "/" + project.getId() + "/available-assets")
                        .param("keyword", "唯一名TT").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode content = objectMapper.readTree(body).path("data").path("content");
        assertEquals(1, content.size());
        assertEquals(target.getId().toString(), content.get(0).path("id").asText());
    }

    // ------------------------------------------------------------------ 四、审计

    @Test
    @DisplayName("审计：批量添加/移除各写 1 条（details 含数量）")
    void bulkOperationsWriteOneAuditEach() throws Exception {
        Asset a = newAsset("审计批量A");
        Asset b = newAsset("审计批量B");
        bulkAdd(adminToken, idsBody(List.of(a.getId(), b.getId())));
        linkToOtherProject(b);
        mockMvc.perform(delete(PROJECTS + "/" + project.getId() + "/assets")
                        .header("Authorization", adminToken).contentType(APPLICATION_JSON)
                        .content(idsBody(List.of(b.getId()))))
                .andExpect(status().isOk());

        var addLogs = auditLogRepository.findByAction("BULK_ADD_PROJECT_ASSETS");
        var removeLogs = auditLogRepository.findByAction("BULK_REMOVE_PROJECT_ASSETS");
        assertEquals(1, addLogs.size(), "批量添加应恰好 1 条审计");
        assertEquals(1, removeLogs.size(), "批量移除应恰好 1 条审计");
        assertTrue(addLogs.get(0).getDetails().contains("added=2"), addLogs.get(0).getDetails());
        assertTrue(removeLogs.get(0).getDetails().contains("removed=1"), removeLogs.get(0).getDetails());
    }
}
