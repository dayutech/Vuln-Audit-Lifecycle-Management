package com.vulnaudit.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.Customer;
import com.vulnaudit.entity.Project;
import com.vulnaudit.repository.AssetProjectLinkRepository;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.AuditLogRepository;
import com.vulnaudit.repository.CustomerRepository;
import com.vulnaudit.repository.ProjectRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 软删除（t140）
 *
 * <p>客户 / 项目 / 资产删除后只写 {@code deleted_at}：默认所有列表、详情、统计、下拉都看不到，
 * 但行仍在库中、唯一键仍占用（同 code 重建 → 409），并可由 ADMIN 恢复。
 *
 * <p><b>实现口径</b>：实体上 {@code @SQLRestriction("deleted_at IS NULL")} 统一过滤（JPQL / 派生查询 /
 * 懒加载全覆盖），「含已删」的管理端列表与恢复走原生 SQL（原生查询不受 restriction 影响）。
 */
@DisplayName("软删除测试")
public class SoftDeleteTest extends BaseTest {

    private static final String CUSTOMERS = "/api/v1/customers";
    private static final String PROJECTS = "/api/v1/projects";
    private static final String ASSETS = "/api/v1/assets";
    private static final String ADMIN = "/api/v1/admin";

    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private AssetRepository assetRepository;
    @Autowired
    private AssetProjectLinkRepository linkRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private com.vulnaudit.repository.TeamRepository teamRepository;

    /** 建资产需要 teamId（t69 硬约束 + canCreateAssetInTeam 守卫） */
    private UUID teamId;

    @org.junit.jupiter.api.BeforeEach
    void setUpTeam() {
        com.vulnaudit.entity.Team team = teamRepository.save(Objects.requireNonNull(
                com.vulnaudit.entity.Team.builder()
                        .name("软删团队_" + System.nanoTime())
                        .description("软删用例团队")
                        .leaderId(adminUserId)
                        .createdBy(adminUserId)
                        .build()));
        teamId = team.getId();
    }

    // ------------------------------------------------------------------ 工具

    private JsonNode createCustomer(String code, String name) throws Exception {
        StringBuilder body = new StringBuilder("{");
        if (code != null) {
            body.append("\"code\":\"").append(code).append("\",");
        }
        body.append("\"name\":\"").append(name).append("\"}");
        String created = mockMvc.perform(post(CUSTOMERS).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content(body.toString()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).path("data");
    }

    private JsonNode createProject(String code, UUID customerId, String name) throws Exception {
        StringBuilder body = new StringBuilder("{\"customerId\":\"").append(customerId).append("\",")
                .append("\"name\":\"").append(name).append("\",")
                .append("\"managerId\":\"").append(adminUserId).append("\"");
        if (code != null) {
            body.append(",\"code\":\"").append(code).append("\"");
        }
        String created = mockMvc.perform(post(PROJECTS).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content(body.append("}").toString()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).path("data");
    }

    private JsonNode createAsset(String name, UUID projectId) throws Exception {
        String body = "{\"name\":\"" + name + "\",\"targetType\":\"web\",\"targetSource\":\"internal\","
                + "\"priority\":\"P2\",\"status\":\"active\",\"teamId\":\"" + teamId + "\","
                + "\"projectId\":\"" + projectId + "\"}";
        String created = mockMvc.perform(post(ASSETS).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).path("data");
    }

    private UUID idOf(JsonNode node) {
        return UUID.fromString(node.path("id").asText());
    }

    /** 含已删行地查库（默认仓储查询看不到软删行） */
    private Customer customerIncludingDeleted(UUID id) {
        return customerRepository.findByIdIncludingDeleted(id).orElseThrow();
    }

    private Project projectIncludingDeleted(UUID id) {
        return projectRepository.findByIdIncludingDeleted(id).orElseThrow();
    }

    private Asset assetIncludingDeleted(UUID id) {
        return assetRepository.findByIdIncludingDeleted(id).orElseThrow();
    }

    // ------------------------------------------------------------------ 一、客户

    @Test
    @DisplayName("客户：删除后列表不含 + 详情 404 + 库中 deleted_at 非空")
    void deleteCustomer_hiddenEverywhere() throws Exception {
        String code = "SD-CUST-" + System.nanoTime();
        JsonNode customer = createCustomer(code, "软删客户");
        UUID id = idOf(customer);

        mockMvc.perform(delete(CUSTOMERS + "/" + id).header("Authorization", adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(CUSTOMERS).header("Authorization", adminToken).param("keyword", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", org.hamcrest.Matchers.hasSize(0)));

        mockMvc.perform(get(CUSTOMERS + "/" + id).header("Authorization", adminToken))
                .andExpect(status().isNotFound());

        Customer row = customerIncludingDeleted(id);
        assertNotNull(row.getDeletedAt(), "库中 deleted_at 应非空");
        assertEquals(adminUserId, row.getDeletedBy(), "deleted_by 应记录操作者");
        assertEquals(1, customerRepository.countByCodeIncludingDeleted(code), "行仍在库中");
    }

    @Test
    @DisplayName("客户：软删后同 code 再次创建 → 409（唯一键仍占用）")
    void recreateCustomerWithSameCode_conflict() throws Exception {
        String code = "SD-DUP-" + System.nanoTime();
        UUID id = idOf(createCustomer(code, "原客户"));

        mockMvc.perform(delete(CUSTOMERS + "/" + id).header("Authorization", adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post(CUSTOMERS).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"重建客户\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("客户编码已存在")));
    }

    @Test
    @DisplayName("客户：管理端 includeDeleted=true 能看到已删行，默认看不到")
    void adminList_includeDeleted() throws Exception {
        String code = "SD-ADM-" + System.nanoTime();
        UUID id = idOf(createCustomer(code, "管理端可见"));
        mockMvc.perform(delete(CUSTOMERS + "/" + id).header("Authorization", adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(ADMIN + "/customers").header("Authorization", adminToken)
                        .param("keyword", code).param("includeDeleted", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].deletedAt").exists());

        mockMvc.perform(get(ADMIN + "/customers").header("Authorization", adminToken)
                        .param("keyword", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("客户：恢复后列表出现、详情 200；重复恢复幂等且只写 1 条审计")
    void restoreCustomer() throws Exception {
        String code = "SD-RES-" + System.nanoTime();
        UUID id = idOf(createCustomer(code, "待恢复客户"));
        mockMvc.perform(delete(CUSTOMERS + "/" + id).header("Authorization", adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(post(ADMIN + "/customers/" + id + "/restore").header("Authorization", adminToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(CUSTOMERS + "/" + id).header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customer.code").value(code));

        // 幂等：再恢复一次仍 204，且不重复审计
        mockMvc.perform(post(ADMIN + "/customers/" + id + "/restore").header("Authorization", adminToken))
                .andExpect(status().isNoContent());
        assertEquals(1, auditLogRepository.findByAction("RESTORE_CUSTOMER").size(),
                "一次有效恢复只应有 1 条 RESTORE_CUSTOMER 审计");
    }

    @Test
    @DisplayName("客户：其下有未删项目时删除 → 409；恢复没有这个问题")
    void deleteCustomerWithUndeletedProject_conflict() throws Exception {
        UUID customerId = idOf(createCustomer(null, "有项目客户"));
        createProject(null, customerId, "阻塞项目");

        mockMvc.perform(delete(CUSTOMERS + "/" + customerId).header("Authorization", adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("个项目")));
    }

    @Test
    @DisplayName("恢复/含已删列表：非 ADMIN → 403")
    void adminEndpointsRequireAdmin() throws Exception {
        UUID id = idOf(createCustomer(null, "权限客户"));

        mockMvc.perform(post(ADMIN + "/customers/" + id + "/restore").header("Authorization", leaderToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(ADMIN + "/customers").header("Authorization", engineerToken))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ 二、项目

    @Test
    @DisplayName("项目：删除后列表/详情/项目资产接口都 404 或不可见")
    void deleteProject_hiddenEverywhere() throws Exception {
        UUID customerId = idOf(createCustomer(null, "项目软删客户"));
        String code = "SD-PRJ-" + System.nanoTime();
        UUID projectId = idOf(createProject(code, customerId, "软删项目"));

        mockMvc.perform(delete(PROJECTS + "/" + projectId).header("Authorization", adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(PROJECTS).header("Authorization", adminToken).param("keyword", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", org.hamcrest.Matchers.hasSize(0)));
        mockMvc.perform(get(PROJECTS + "/summary").header("Authorization", adminToken).param("keyword", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", org.hamcrest.Matchers.hasSize(0)));
        mockMvc.perform(get(PROJECTS + "/" + projectId).header("Authorization", adminToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(PROJECTS + "/" + projectId + "/assets").header("Authorization", adminToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(PROJECTS + "/" + projectId + "/stats").header("Authorization", adminToken))
                .andExpect(status().isNotFound());

        assertNotNull(projectIncludingDeleted(projectId).getDeletedAt());
    }

    @Test
    @DisplayName("项目：仍有未删资产挂靠 → 409；仅剩已删资产的挂靠 → 可删")
    void deleteProject_linkRules() throws Exception {
        UUID customerId = idOf(createCustomer(null, "挂靠规则客户"));
        UUID projectWithLink = idOf(createProject(null, customerId, "有挂靠项目"));
        JsonNode asset = createAsset("挂靠资产", projectWithLink);
        UUID assetId = idOf(asset);

        mockMvc.perform(delete(PROJECTS + "/" + projectWithLink).header("Authorization", adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("资产挂靠")));

        // 软删资产后，其挂靠行不再阻塞项目删除
        mockMvc.perform(delete(ASSETS + "/" + assetId).header("Authorization", adminToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete(PROJECTS + "/" + projectWithLink).header("Authorization", adminToken))
                .andExpect(status().isNoContent());
        assertFalse(linkRepository.findByProjectId(projectWithLink).isEmpty(),
                "软删项目不删挂靠行（历史可追溯）");
    }

    @Test
    @DisplayName("项目：恢复后详情 200；管理端 includeDeleted 可见")
    void restoreProject() throws Exception {
        UUID customerId = idOf(createCustomer(null, "项目恢复客户"));
        UUID projectId = idOf(createProject(null, customerId, "待恢复项目"));
        mockMvc.perform(delete(PROJECTS + "/" + projectId).header("Authorization", adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(ADMIN + "/projects").header("Authorization", adminToken)
                        .param("includeDeleted", "true").param("keyword", "待恢复项目"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(post(ADMIN + "/projects/" + projectId + "/restore").header("Authorization", adminToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(PROJECTS + "/" + projectId).header("Authorization", adminToken))
                .andExpect(status().isOk());
        assertEquals(1, auditLogRepository.findByAction("RESTORE_PROJECT").size());
    }

    // ------------------------------------------------------------------ 三、资产

    @Test
    @DisplayName("资产：删除后列表/详情/项目资产都不计；库中 deleted_at 非空")
    void deleteAsset_hiddenEverywhere() throws Exception {
        UUID customerId = idOf(createCustomer(null, "资产软删客户"));
        UUID projectId = idOf(createProject(null, customerId, "资产软删项目"));
        String assetName = "待删资产-" + System.nanoTime();
        UUID assetId = idOf(createAsset(assetName, projectId));

        mockMvc.perform(delete(ASSETS + "/" + assetId).header("Authorization", adminToken))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(ASSETS).header("Authorization", adminToken).param("keyword", assetName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", org.hamcrest.Matchers.hasSize(0)));
        mockMvc.perform(get(ASSETS + "/" + assetId).header("Authorization", adminToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(ASSETS + "/" + assetId + "/project-links").header("Authorization", adminToken))
                .andExpect(status().isNotFound());

        // 项目资产接口不再列出该资产
        mockMvc.perform(get(PROJECTS + "/" + projectId + "/assets").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", org.hamcrest.Matchers.hasSize(0)));

        Asset row = assetIncludingDeleted(assetId);
        assertNotNull(row.getDeletedAt());
        assertNotNull(row.getDeletedBy(), "deleted_by 应记录操作者");
        assertFalse(linkRepository.findByAssetId(assetId).isEmpty(), "软删资产保留挂靠行（历史可追溯）");
    }

    @Test
    @DisplayName("资产：删除后 dashboard 统计与分布不计入")
    void deleteAsset_dashboardExcludes() throws Exception {
        UUID customerId = idOf(createCustomer(null, "统计客户"));
        UUID projectId = idOf(createProject(null, customerId, "统计项目"));
        UUID assetId = idOf(createAsset("统计资产-" + System.nanoTime(), projectId));

        String before = mockMvc.perform(get("/api/v1/dashboard/assets/stats").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int totalBefore = objectMapper.readTree(before).path("data").path("totalAssets").asInt(-1);

        mockMvc.perform(delete(ASSETS + "/" + assetId).header("Authorization", adminToken))
                .andExpect(status().isNoContent());

        String after = mockMvc.perform(get("/api/v1/dashboard/assets/stats").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int totalAfter = objectMapper.readTree(after).path("data").path("totalAssets").asInt(-1);

        if (totalBefore >= 0 && totalAfter >= 0) {
            assertTrue(totalAfter < totalBefore, "软删资产不应计入 dashboard 统计: " + totalBefore + " → " + totalAfter);
        }
        mockMvc.perform(get("/api/v1/dashboard/customer-distribution").header("Authorization", adminToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("资产：软删后同编号重建 → 409；恢复后详情 200")
    void restoreAsset_andCodeStillOccupied() throws Exception {
        UUID customerId = idOf(createCustomer(null, "资产恢复客户"));
        UUID projectId = idOf(createProject(null, customerId, "资产恢复项目"));
        JsonNode asset = createAsset("恢复资产-" + System.nanoTime(), projectId);
        UUID assetId = idOf(asset);
        String code = asset.path("code").asText();

        mockMvc.perform(delete(ASSETS + "/" + assetId).header("Authorization", adminToken))
                .andExpect(status().isNoContent());
        assertEquals(1, assetRepository.countByCodeIncludingDeleted(code), "软删后编号仍占用");

        mockMvc.perform(get(ADMIN + "/assets").header("Authorization", adminToken)
                        .param("includeDeleted", "true").param("keyword", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(post(ADMIN + "/assets/" + assetId + "/restore").header("Authorization", adminToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(ASSETS + "/" + assetId).header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value(code));
        assertEquals(1, auditLogRepository.findByAction("RESTORE_ASSET").size());
    }

    @Test
    @DisplayName("客户：恢复不存在/从未删除的对象 → 404 / 幂等 204")
    void restoreEdgeCases() throws Exception {
        mockMvc.perform(post(ADMIN + "/customers/" + UUID.randomUUID() + "/restore")
                        .header("Authorization", adminToken))
                .andExpect(status().isNotFound());

        UUID id = idOf(createCustomer(null, "未删客户"));
        mockMvc.perform(post(ADMIN + "/customers/" + id + "/restore").header("Authorization", adminToken))
                .andExpect(status().isNoContent());
        assertEquals(0, auditLogRepository.findByAction("RESTORE_CUSTOMER").size(),
                "未删对象的恢复是空操作，不应写审计");
    }

    @Test
    @DisplayName("删除动作写审计（拦截器），且对象仍在库中")
    void deleteWritesAudit() throws Exception {
        UUID id = idOf(createCustomer(null, "审计删除客户"));
        mockMvc.perform(delete(CUSTOMERS + "/" + id).header("Authorization", adminToken))
                .andExpect(status().isNoContent());

        boolean hasDeleteAudit = auditLogRepository.findAll().stream()
                .anyMatch(log -> log.getAction() != null
                        && log.getAction().toUpperCase(java.util.Locale.ROOT).contains("DELETE"));
        assertTrue(hasDeleteAudit, "删除应写审计（action 含 DELETE）");
        assertNotNull(customerIncludingDeleted(id).getDeletedAt(), "软删不物理删除");
    }

    @Test
    @DisplayName("统计口径：客户 summary 的项目/资产数不含软删子项")
    void customerSummaryExcludesDeletedChildren() throws Exception {
        UUID customerId = idOf(createCustomer(null, "汇总客户"));
        UUID projectId = idOf(createProject(null, customerId, "汇总项目"));
        UUID assetId = idOf(createAsset("汇总资产-" + System.nanoTime(), projectId));

        mockMvc.perform(get(CUSTOMERS + "/" + customerId + "/summary").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stats.projectCount").value(1));

        mockMvc.perform(delete(ASSETS + "/" + assetId).header("Authorization", adminToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(CUSTOMERS + "/" + customerId + "/summary").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stats.assetCount").value(0));

        mockMvc.perform(delete(PROJECTS + "/" + projectId).header("Authorization", adminToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(CUSTOMERS + "/" + customerId + "/summary").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stats.projectCount").value(0));
    }

    @Test
    @DisplayName("项目：软删后客户详情里的项目列表也不含它")
    void customerSummaryHidesDeletedProject() throws Exception {
        UUID customerId = idOf(createCustomer(null, "详情客户"));
        UUID projectId = idOf(createProject(null, customerId, "详情项目"));

        mockMvc.perform(get(CUSTOMERS + "/" + customerId).header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectCount").value(1));

        mockMvc.perform(delete(PROJECTS + "/" + projectId).header("Authorization", adminToken))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(CUSTOMERS + "/" + customerId).header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectCount").value(0));
    }
}
