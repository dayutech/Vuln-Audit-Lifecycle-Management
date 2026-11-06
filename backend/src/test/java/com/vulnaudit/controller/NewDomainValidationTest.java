package com.vulnaudit.controller;

import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.AssetProjectLink;
import com.vulnaudit.entity.Customer;
import com.vulnaudit.entity.Project;
import com.vulnaudit.repository.AssetProjectLinkRepository;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.CustomerRepository;
import com.vulnaudit.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 新域（客户 / 项目 / 期次 / 资产挂靠）NOT NULL 兜底校验测试（t80）。
 *
 * <p>背景：本项目反复出现「依赖数据库 NOT NULL 约束抛错 → 500」的模式
 * （t34 漏洞创建、t40 公告 publishedAt、t22 passwordHash，本轮 t75 发现 {@code POST /projects}
 * 缺 {@code managerId} 报 500）。本用例把「每个 NOT NULL 列必须由 DTO 校验或服务层兜底」固化成断言：
 * <b>所有缺字段场景必须是 400（带字段名）或 404（引用不存在），不得出现 5xx</b>。
 */
@DisplayName("新域 NOT NULL 兜底校验测试")
public class NewDomainValidationTest extends BaseTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private AssetProjectLinkRepository linkRepository;

    private Customer customer;
    private Project project;
    private Asset asset;

    @BeforeEach
    void setUpDomain() {
        String suffix = String.valueOf(System.currentTimeMillis());
        customer = customerRepository.save(Objects.requireNonNull(Customer.builder()
                .code("VAL-CUST-" + suffix)
                .name("校验客户")
                .status("active")
                .createdBy(adminUserId)
                .build()));
        project = projectRepository.save(Objects.requireNonNull(Project.builder()
                .code("VAL-PRJ-" + suffix)
                .customerId(customer.getId())
                .name("校验项目")
                .type("PENTEST")
                .status("DRAFT")
                .managerId(adminUserId)
                .isPhased(false)
                .createdBy(adminUserId)
                .build()));
        asset = assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name("校验资产_" + suffix)
                .code("VAL-A-" + suffix)
                .targetType("Web应用")
                .targetSource("internal")
                .priority("P1")
                .status("active")
                .ownerId(adminUserId)
                .build()));
    }

    /** 统一断言：状态码不得是 5xx（依赖 DB 约束抛错的典型表现） */
    private void assertNotServerError(MvcResult result, String scene) {
        int statusCode = result.getResponse().getStatus();
        assertTrue(statusCode < 500, scene + " 不应返回 5xx（实际 " + statusCode + "）");
    }

    private String projectBody(String code, UUID customerId, UUID managerId) {
        StringBuilder sb = new StringBuilder("{");
        if (code != null) {
            sb.append("\"code\":\"").append(code).append("\",");
        }
        if (customerId != null) {
            sb.append("\"customerId\":\"").append(customerId).append("\",");
        }
        sb.append("\"name\":\"校验项目名\",\"type\":\"PENTEST\"");
        if (managerId != null) {
            sb.append(",\"managerId\":\"").append(managerId).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    // ------------------------------------------------------------------ 项目必填字段

    @Test
    @DisplayName("POST /projects 缺 managerId → 400 且 data.managerId（不再 500）")
    void createProject_missingManager_returns400() throws Exception {
        String code = "VAL-MISS-MGR-" + System.nanoTime();
        String body = projectBody(code, customer.getId(), null);

        MvcResult result = mockMvc.perform(post("/api/v1/projects").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.managerId").exists())
                .andReturn();

        assertNotServerError(result, "缺 managerId 建项目");
        assertTrue(projectRepository.findByCode(code).isEmpty(), "缺字段不得落库");
    }

    @Test
    @DisplayName("POST /projects 缺 customerId → 400 且 data.customerId")
    void createProject_missingCustomer_returns400() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/projects").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(projectBody("VAL-MISS-CUST-" + System.nanoTime(), null, adminUserId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.customerId").exists())
                .andReturn();
        assertNotServerError(result, "缺 customerId 建项目");
    }

    @Test
    @DisplayName("POST /projects code 为空 → 自动生成（201，t131）；name 为空 → 400 且带字段名")
    void createProject_blankCodeAutoGenerated_blankName400() throws Exception {
        // t131：项目编码改为可选，空值由服务端生成 PRJ-yyyy-MM-dd-XXXXXX
        MvcResult blankCode = mockMvc.perform(post("/api/v1/projects").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"code\":\"\",\"customerId\":\"" + customer.getId()
                                + "\",\"name\":\"x\",\"managerId\":\"" + adminUserId + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code", org.hamcrest.Matchers.matchesPattern(
                        "^PRJ-\\d{4}-\\d{2}-\\d{2}-[A-Z]{6}$")))
                .andReturn();
        assertNotServerError(blankCode, "空 code 建项目（应自动生成）");

        MvcResult blankName = mockMvc.perform(post("/api/v1/projects").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"customerId\":\"" + customer.getId()
                                + "\",\"name\":\" \",\"managerId\":\"" + adminUserId + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.name").exists())
                .andReturn();
        assertNotServerError(blankName, "空 name 建项目");
    }

    @Test
    @DisplayName("POST /projects 引用不存在：customer → 404、manager → 404")
    void createProject_unknownReferences_returns404() throws Exception {
        MvcResult missingCustomer = mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(projectBody("VAL-NOCUST-" + System.nanoTime(), UUID.randomUUID(), adminUserId)))
                .andExpect(status().isNotFound())
                .andReturn();
        assertNotServerError(missingCustomer, "客户不存在建项目");

        MvcResult missingManager = mockMvc.perform(post("/api/v1/projects")
                        .header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(projectBody("VAL-NOMGR-" + System.nanoTime(), customer.getId(), UUID.randomUUID())))
                .andExpect(status().isNotFound())
                .andReturn();
        assertNotServerError(missingManager, "经理不存在建项目");
    }

    @Test
    @DisplayName("PUT /projects/{id} 缺 managerId → 400（更新路径同样兜底）")
    void updateProject_missingManager_returns400() throws Exception {
        MvcResult result = mockMvc.perform(put("/api/v1/projects/" + project.getId())
                        .header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(projectBody(project.getCode(), customer.getId(), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.managerId").exists())
                .andReturn();
        assertNotServerError(result, "更新项目缺 managerId");
    }

    // ------------------------------------------------------------------ 期次 / 挂靠 / 客户

    @Test
    @DisplayName("POST /phases 缺 name → 400 且 data.name；phaseNo 留空由服务层自增（不依赖 DB 约束）")
    void createPhase_missingName_returns400() throws Exception {
        Project phased = projectRepository.save(Objects.requireNonNull(Project.builder()
                .code("VAL-PHASED-" + System.nanoTime())
                .customerId(customer.getId())
                .name("分期项目")
                .type("PENTEST")
                .status("DRAFT")
                .managerId(adminUserId)
                .isPhased(true)
                .createdBy(adminUserId)
                .build()));

        MvcResult missingName = mockMvc.perform(post("/api/v1/projects/" + phased.getId() + "/phases")
                        .header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"status\":\"PLANNED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.name").exists())
                .andReturn();
        assertNotServerError(missingName, "建期次缺 name");

        // phaseNo 留空 → 服务层自增，不应因 NOT NULL 报错
        MvcResult autoNo = mockMvc.perform(post("/api/v1/projects/" + phased.getId() + "/phases")
                        .header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"自动号期次\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.phaseNo").value(1))
                .andReturn();
        assertNotServerError(autoNo, "建期次 phaseNo 留空");
    }

    @Test
    @DisplayName("POST /assets/{id}/project-links 缺 projectId → 400 且 data.projectId；linkType 留空落库默认 TARGET")
    void createLink_validationAndDefaults() throws Exception {
        MvcResult missingProject = mockMvc.perform(post("/api/v1/assets/" + asset.getId() + "/project-links")
                        .header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"remark\":\"缺项目\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.projectId").exists())
                .andReturn();
        assertNotServerError(missingProject, "挂靠缺 projectId");

        MvcResult created = mockMvc.perform(post("/api/v1/assets/" + asset.getId() + "/project-links")
                        .header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"projectId\":\"" + project.getId() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.linkType").value("TARGET"))
                .andReturn();
        assertNotServerError(created, "挂靠缺 linkType（应默认 TARGET）");

        AssetProjectLink link = linkRepository.findByAssetId(asset.getId()).stream().findFirst().orElseThrow();
        assertEquals("TARGET", link.getLinkType(), "linkType 应落库默认值 TARGET");
        assertNotEquals(null, link.getPhaseKey(), "phaseKey 必须非空（未分期用哨兵值，避免 NOT NULL 报错）");
    }

    @Test
    @DisplayName("POST /customers code 为空 → 自动生成（201，t129）；name 为空 → 400 且带字段名")
    void createCustomer_blankFields() throws Exception {
        // t129：客户编码改为可选，空值由服务端生成 CUST-XXXXXXX
        MvcResult blankCode = mockMvc.perform(post("/api/v1/customers").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"code\":\"\",\"name\":\"客户\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code", org.hamcrest.Matchers.matchesPattern("^CUST-[A-Z]{7}$")))
                .andReturn();
        assertNotServerError(blankCode, "空 code 建客户（应自动生成）");

        MvcResult blankName = mockMvc.perform(post("/api/v1/customers").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"code\":\"VAL-C-" + System.nanoTime() + "\",\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.name").exists())
                .andReturn();
        assertNotServerError(blankName, "空 name 建客户");
    }

    @Test
    @DisplayName("全套必填缺失场景统一断言：状态码只能是 400/404，绝无 5xx")
    void allMissingFieldCases_neverReturn5xx() throws Exception {
        String[] bodies = {
                "{}",
                "{\"code\":null,\"customerId\":null,\"name\":null,\"managerId\":null}",
                "{\"code\":\"VAL-ALL-" + System.nanoTime() + "\"}",
                "{\"customerId\":\"" + customer.getId() + "\"}",
                "{\"name\":\"只有名字\"}"
        };
        for (String body : bodies) {
            MvcResult result = mockMvc.perform(post("/api/v1/projects")
                            .header("Authorization", adminToken)
                            .contentType(APPLICATION_JSON).content(body))
                    .andReturn();
            int statusCode = result.getResponse().getStatus();
            assertTrue(statusCode == 400 || statusCode == 404,
                    "缺字段应返回 400/404，实际 " + statusCode + "，body=" + body);
        }
    }
}
