package com.vulnaudit.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.vulnaudit.entity.Customer;
import com.vulnaudit.entity.Project;
import com.vulnaudit.repository.CustomerRepository;
import com.vulnaudit.repository.ProjectRepository;
import com.vulnaudit.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 编码预览与「创建时按预览编码落库」测试（t192）。
 *
 * <p>背景：客户/项目编码此前允许在表单里手输（客户甚至是必填手输），资产编码则根本不展示。
 * 产品要求统一为：**打开新建页就生成并只读展示，用户不可手动设置**。
 * 因此新增三个 {@code /next-code} 预览端点，并在创建时接受回传编码（未传则仍自动生成，兼容导入/旧客户端）。
 */
@DisplayName("编码预览 + 创建回填")
class CodePreviewTest extends BaseTest {

    private static final String CUSTOMER_CODE_PATTERN = "^CUST-[A-Z]{7}$";
    private static final String PROJECT_CODE_PATTERN = "^PRJ-\\d{4}-\\d{2}-\\d{2}-[A-Z]{6}$";
    private static final String ASSET_CODE_PATTERN = "^AST-\\d{8}-[A-Z0-9]{6}$";

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TeamRepository teamRepository;

    private Customer customer;
    private Project project;
    private UUID teamId;

    @BeforeEach
    void setUpFixtures() {
        String suffix = String.valueOf(System.nanoTime());
        customer = customerRepository.save(Objects.requireNonNull(Customer.builder()
                .code("CUST-T192-" + suffix)
                .name("t192 客户")
                .level("A")
                .status("active")
                .createdBy(adminUserId)
                .build()));
        project = projectRepository.save(Objects.requireNonNull(Project.builder()
                .code("PRJ-T192-" + suffix)
                .customerId(customer.getId())
                .name("t192 项目")
                .type("PENTEST")
                .status("ACTIVE")
                .managerId(adminUserId)
                .isPhased(false)
                .createdBy(adminUserId)
                .build()));
        teamId = teamRepository.save(Objects.requireNonNull(com.vulnaudit.entity.Team.builder()
                .name("t192 团队-" + UUID.randomUUID())
                .leaderId(adminUserId)
                .createdBy(adminUserId)
                .build())).getId();
    }

    private String previewCode(String path) throws Exception {
        String body = mockMvc.perform(get(path).header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        assertTrue(json.path("success").asBoolean(), path + " 应成功");
        return json.path("data").path("code").asText();
    }

    // ------------------------------------------------------------------ 预览格式

    @Test
    @DisplayName("客户/项目/资产 预览编码符合各自规则，且两次预览互不相同")
    void previewCodes_matchRules() throws Exception {
        String customerCode = previewCode("/api/v1/customers/next-code");
        String projectCode = previewCode("/api/v1/projects/next-code");
        String assetCode = previewCode("/api/v1/assets/next-code");

        assertTrue(customerCode.matches(CUSTOMER_CODE_PATTERN), "客户编码格式不符: " + customerCode);
        assertTrue(projectCode.matches(PROJECT_CODE_PATTERN), "项目编码格式不符: " + projectCode);
        assertTrue(assetCode.matches(ASSET_CODE_PATTERN), "资产编码格式不符: " + assetCode);

        assertNotEquals(customerCode, previewCode("/api/v1/customers/next-code"), "两次预览不应相同");
        assertNotEquals(projectCode, previewCode("/api/v1/projects/next-code"), "两次预览不应相同");
        assertNotEquals(assetCode, previewCode("/api/v1/assets/next-code"), "两次预览不应相同");
    }

    @Test
    @DisplayName("预览端点需相应角色：工程师访问客户/资产预览 → 403")
    void previewEndpoints_requireRole() throws Exception {
        mockMvc.perform(get("/api/v1/customers/next-code").header("Authorization", engineerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/projects/next-code").header("Authorization", engineerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/assets/next-code").header("Authorization", engineerToken))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ 创建时回填

    @Test
    @DisplayName("客户创建带预览编码 → 落库编码与预览一致（不再需要手输）")
    void createCustomer_withPreviewedCode_keepsCode() throws Exception {
        String code = previewCode("/api/v1/customers/next-code");
        String payload = objectMapper.writeValueAsString(java.util.Map.of(
                "code", code,
                "name", "t192 预览编码客户",
                "contactName", "张三",
                "contactPhone", "13900000000"));
        String body = mockMvc.perform(post("/api/v1/customers")
                        .header("Authorization", adminToken)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertEquals(code, objectMapper.readTree(body).path("data").path("code").asText());
    }

    @Test
    @DisplayName("资产创建带预览编码 → 返回编码一致；同一编码再次创建 → 409")
    void createAsset_withPreviewedCode_conflictsOnReuse() throws Exception {
        String code = previewCode("/api/v1/assets/next-code");
        String payload = objectMapper.writeValueAsString(java.util.Map.of(
                "code", code,
                "name", "t192 预览编码资产",
                "targetType", "web",
                "targetSource", "internal",
                "priority", "P2",
                "status", "active",
                "teamId", teamId.toString(),
                "projectId", project.getId().toString()));

        String created = mockMvc.perform(post("/api/v1/assets")
                        .header("Authorization", adminToken)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertEquals(code, objectMapper.readTree(created).path("data").path("code").asText(),
                "表单里显示的编码必须与落库一致");

        mockMvc.perform(post("/api/v1/assets")
                        .header("Authorization", adminToken)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(payload))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("不带 code 创建（CSV 导入/旧客户端）仍按规则自动生成")
    void createAsset_withoutCode_stillGenerates() throws Exception {
        String payload = objectMapper.writeValueAsString(java.util.Map.of(
                "name", "t192 自动生成资产",
                "targetType", "web",
                "targetSource", "internal",
                "priority", "P2",
                "status", "active",
                "teamId", teamId.toString(),
                "projectId", project.getId().toString()));
        String body = mockMvc.perform(post("/api/v1/assets")
                        .header("Authorization", adminToken)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String generated = objectMapper.readTree(body).path("data").path("code").asText();
        assertTrue(generated.matches(ASSET_CODE_PATTERN), "自动生成的资产编码格式不符: " + generated);
    }
}