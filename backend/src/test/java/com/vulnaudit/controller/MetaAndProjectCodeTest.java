package com.vulnaudit.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.vulnaudit.entity.AuditLog;
import com.vulnaudit.entity.Customer;
import com.vulnaudit.entity.IndustryType;
import com.vulnaudit.entity.Project;
import com.vulnaudit.entity.ProjectType;
import com.vulnaudit.repository.AuditLogRepository;
import com.vulnaudit.repository.CustomerRepository;
import com.vulnaudit.repository.IndustryTypeRepository;
import com.vulnaudit.repository.ProjectRepository;
import com.vulnaudit.repository.ProjectTypeRepository;
import com.vulnaudit.service.IndustryTypeService;
import com.vulnaudit.service.ProjectTypeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 行业类型 / 项目类型管理 + 项目编码自动生成（t130）
 *
 * <p>覆盖：内置不可改删（403）、自定义可改删、被引用删除 409、软删生效、
 * 非 ADMIN 写 403、下拉只返回启用、typeCode 落库与非法 400、
 * 项目编码格式/唯一/显式保留/更新保留，以及内置项初始化幂等。
 */
@DisplayName("行业/项目类型管理与项目编码测试")
public class MetaAndProjectCodeTest extends BaseTest {

    private static final String ADMIN_INDUSTRIES = "/api/v1/admin/industry-types";
    private static final String INDUSTRIES = "/api/v1/industry-types";
    private static final String ADMIN_PROJECT_TYPES = "/api/v1/admin/project-types";
    private static final String PROJECT_TYPES = "/api/v1/project-types";
    private static final String PROJECTS = "/api/v1/projects";
    private static final String CUSTOMERS = "/api/v1/customers";

    @Autowired
    private IndustryTypeRepository industryTypeRepository;
    @Autowired
    private ProjectTypeRepository projectTypeRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private AuditLogRepository auditLogRepository;
    @Autowired
    private IndustryTypeService industryTypeService;
    @Autowired
    private ProjectTypeService projectTypeService;

    @BeforeEach
    void ensureMeta() {
        // 启动器已插入；此处兜底，保证用例独立可跑（同时也是幂等性验证）
        industryTypeService.ensureBuiltin();
        projectTypeService.ensureBuiltin();
    }

    // ------------------------------------------------------------------ 工具

    private String industryJson(String code, String name, Integer sortOrder, Boolean enabled) {
        StringBuilder sb = new StringBuilder("{");
        if (code != null) {
            sb.append("\"code\":\"").append(code).append("\",");
        }
        if (name != null) {
            sb.append("\"name\":\"").append(name).append("\",");
        }
        if (sortOrder != null) {
            sb.append("\"sortOrder\":").append(sortOrder).append(',');
        }
        if (enabled != null) {
            sb.append("\"enabled\":").append(enabled).append(',');
        }
        if (sb.charAt(sb.length() - 1) == ',') {
            sb.setLength(sb.length() - 1);
        }
        return sb.append("}").toString();
    }

    private JsonNode createIndustry(String code, String name) throws Exception {
        String body = mockMvc.perform(post(ADMIN_INDUSTRIES).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content(industryJson(code, name, 100, null)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    private IndustryType builtinIndustry() {
        return industryTypeRepository.findAll().stream()
                .filter(type -> type.isBuiltin() && !type.isDeleted())
                .findFirst()
                .orElseThrow();
    }

    private ProjectType builtinProjectType() {
        return projectTypeRepository.findAll().stream()
                .filter(type -> type.isBuiltin() && !type.isDeleted())
                .findFirst()
                .orElseThrow();
    }

    private Customer createCustomer(String name) throws Exception {
        String body = "{\"name\":\"" + name + "\"}";
        mockMvc.perform(post(CUSTOMERS).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        return customerRepository.findAll().stream()
                .filter(c -> name.equals(c.getName()))
                .max(java.util.Comparator.comparing(Customer::getCreatedAt))
                .orElseThrow();
    }

    private String projectJson(String code, String customerId, String typeCode, String projectCode) {
        StringBuilder sb = new StringBuilder("{\"customerId\":\"").append(customerId).append("\",");
        sb.append("\"name\":\"编码测试项目\",");
        sb.append("\"managerId\":\"").append(adminUserId).append("\",");
        if (code != null) {
            sb.append("\"code\":\"").append(code).append("\",");
        }
        if (projectCode != null) {
            sb.append("\"projectCode\":\"").append(projectCode).append("\",");
        }
        if (typeCode != null) {
            sb.append("\"typeCode\":\"").append(typeCode).append("\",");
        }
        if (sb.charAt(sb.length() - 1) == ',') {
            sb.setLength(sb.length() - 1);
        }
        return sb.append("}").toString();
    }

    private JsonNode createProject(String code, UUID customerId, String typeCode, String projectCode) throws Exception {
        String body = mockMvc.perform(post(PROJECTS).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(projectJson(code, customerId.toString(), typeCode, projectCode)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    // ------------------------------------------------------------------ 行业：管理员 CRUD

    @Test
    @DisplayName("行业：新建自定义项 201，builtin=false，列表可见")
    void createCustomIndustry() throws Exception {
        JsonNode created = createIndustry("CUS-T1", "自定义行业一");
        assertFalse(created.path("builtin").asBoolean(true), "新建项必须 builtin=false");
        assertEquals("CUS-T1", created.path("code").asText());

        mockMvc.perform(get(ADMIN_INDUSTRIES).header("Authorization", adminToken)
                        .param("keyword", "CUS-T1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].builtin").value(false));
    }

    @Test
    @DisplayName("行业：新建重复 code → 409")
    void createDuplicateIndustryCode() throws Exception {
        createIndustry("CUS-DUP", "重复编码");
        mockMvc.perform(post(ADMIN_INDUSTRIES).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content(industryJson("CUS-DUP", "重复编码2", null, null)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("行业：内置项改名 → 403「内置行业不可修改」")
    void renameBuiltinIndustryForbidden() throws Exception {
        IndustryType builtin = builtinIndustry();
        mockMvc.perform(put(ADMIN_INDUSTRIES + "/" + builtin.getId()).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(industryJson(null, "改过的名字", null, null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("内置行业不可修改")));
    }

    @Test
    @DisplayName("行业：内置项允许调整 sortOrder / enabled")
    void updateBuiltinIndustrySortOrderAllowed() throws Exception {
        IndustryType builtin = builtinIndustry();
        mockMvc.perform(put(ADMIN_INDUSTRIES + "/" + builtin.getId()).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(industryJson(null, null, 99, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sortOrder").value(99));

        mockMvc.perform(put(ADMIN_INDUSTRIES + "/" + builtin.getId()).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(industryJson(null, null, null, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));
        // 恢复启用，避免影响同事务内的后续断言
        mockMvc.perform(put(ADMIN_INDUSTRIES + "/" + builtin.getId()).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content(industryJson(null, null, null, true)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("行业：删除内置项 → 403")
    void deleteBuiltinIndustryForbidden() throws Exception {
        IndustryType builtin = builtinIndustry();
        mockMvc.perform(delete(ADMIN_INDUSTRIES + "/" + builtin.getId()).header("Authorization", adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("行业：被客户引用时删除 → 409，提示引用数量")
    void deleteReferencedIndustryConflict() throws Exception {
        createIndustry("CUS-REF", "被引用行业");
        createCustomerWithIndustry("引用客户", "CUS-REF");

        IndustryType type = industryTypeRepository.findByCode("CUS-REF").orElseThrow();
        mockMvc.perform(delete(ADMIN_INDUSTRIES + "/" + type.getId()).header("Authorization", adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("已有 1 个客户使用该行业")));
    }

    @Test
    @DisplayName("行业：删除自定义项后软删生效（检索不再返回，行仍在库中）")
    void deleteCustomIndustrySoftDelete() throws Exception {
        createIndustry("CUS-DEL", "待删行业");
        IndustryType type = industryTypeRepository.findByCode("CUS-DEL").orElseThrow();

        mockMvc.perform(delete(ADMIN_INDUSTRIES + "/" + type.getId()).header("Authorization", adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get(INDUSTRIES).header("Authorization", adminToken).param("keyword", "CUS-DEL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", org.hamcrest.Matchers.hasSize(0)));

        IndustryType after = industryTypeRepository.findByCode("CUS-DEL").orElseThrow();
        assertNotNull(after.getDeletedAt(), "应为软删（deleted_at 非空）");
        assertFalse(after.isEnabled());
        mockMvc.perform(delete(ADMIN_INDUSTRIES + "/" + type.getId()).header("Authorization", adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("行业：非 ADMIN 全部写操作 403")
    void industryWriteRequiresAdmin() throws Exception {
        String payload = industryJson("CUS-PL", "组长试图建", null, null);
        mockMvc.perform(post(ADMIN_INDUSTRIES).header("Authorization", leaderToken)
                        .contentType(APPLICATION_JSON).content(payload))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(ADMIN_INDUSTRIES).header("Authorization", engineerToken))
                .andExpect(status().isForbidden());

        IndustryType builtin = builtinIndustry();
        mockMvc.perform(put(ADMIN_INDUSTRIES + "/" + builtin.getId()).header("Authorization", operatorToken())
                        .contentType(APPLICATION_JSON).content(industryJson(null, null, 5, null)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(ADMIN_INDUSTRIES + "/" + builtin.getId()).header("Authorization", leaderToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("行业：写操作各审计 1 条 UPDATE_INDUSTRY_TYPE，details 含动作与 code")
    void industryWritesAreAuditedOnce() throws Exception {
        createIndustry("CUS-AUD", "审计行业");
        List<AuditLog> logs = auditLogRepository.findByAction("UPDATE_INDUSTRY_TYPE");
        assertEquals(1, logs.size(), "一次新建应恰好 1 条审计（拦截器已跳过）");
        assertTrue(logs.get(0).getDetails().contains("CUS-AUD"), logs.get(0).getDetails());
    }

    private String operatorToken() {
        return engineerToken;
    }

    private void createCustomerWithIndustry(String name, String industryCode) throws Exception {
        String body = "{\"name\":\"" + name + "\",\"industryCode\":\"" + industryCode + "\"}";
        mockMvc.perform(post(CUSTOMERS).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------ 项目类型

    @Test
    @DisplayName("项目类型：内置 ≥5 且初始化幂等")
    void builtinProjectTypes() {
        long builtin = projectTypeRepository.countByBuiltinTrueAndDeletedAtIsNull();
        assertTrue(builtin >= 5, "内置项目类型应 ≥5，实际 " + builtin);

        // 二次 ensureBuiltin 不应重复插入
        assertFalse(projectTypeService.ensureBuiltin(), "表非空时应跳过");
        assertEquals(builtin, projectTypeRepository.countByBuiltinTrueAndDeletedAtIsNull());

        List<String> codes = projectTypeRepository.findByEnabledTrueAndDeletedAtIsNullOrderByBuiltinDescSortOrderAscCodeAsc()
                .stream().map(ProjectType::getCode).toList();
        assertTrue(codes.containsAll(List.of("PENTEST", "CODE_AUDIT", "EMERGENCY", "COMPLIANCE", "TRAINING")), codes.toString());
    }

    @Test
    @DisplayName("项目类型：自定义项可建/改/删；内置项改 name 403、删 403")
    void projectTypeCrud() throws Exception {
        String created = mockMvc.perform(post(ADMIN_PROJECT_TYPES).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"code\":\"custom_red\",\"name\":\"红队评估\",\"sortOrder\":60}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.builtin").value(false))
                .andReturn().getResponse().getContentAsString();
        UUID customId = UUID.fromString(objectMapper.readTree(created).path("data").path("id").asText());

        mockMvc.perform(put(ADMIN_PROJECT_TYPES + "/" + customId).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"红队实战\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("红队实战"));

        ProjectType builtin = builtinProjectType();
        mockMvc.perform(put(ADMIN_PROJECT_TYPES + "/" + builtin.getId()).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"改内置\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(ADMIN_PROJECT_TYPES + "/" + builtin.getId()).header("Authorization", adminToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete(ADMIN_PROJECT_TYPES + "/" + customId).header("Authorization", adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get(PROJECT_TYPES).header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code=='custom_red')]", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    @DisplayName("项目类型：下拉只返回 enabled；禁用后不再出现")
    void projectTypeOptionsOnlyEnabled() throws Exception {
        String created = mockMvc.perform(post(ADMIN_PROJECT_TYPES).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"code\":\"FROZEN\",\"name\":\"冻结类型\",\"enabled\":true}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(created).path("data").path("id").asText();

        mockMvc.perform(get(PROJECT_TYPES).header("Authorization", engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code=='FROZEN')]", org.hamcrest.Matchers.hasSize(1)));

        mockMvc.perform(put(ADMIN_PROJECT_TYPES + "/" + id).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(get(PROJECT_TYPES).header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code=='FROZEN')]", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    @DisplayName("项目类型：被项目引用时删除 409")
    void deleteReferencedProjectTypeConflict() throws Exception {
        String created = mockMvc.perform(post(ADMIN_PROJECT_TYPES).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"code\":\"USED_TYPE\",\"name\":\"被引用类型\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(created).path("data").path("id").asText();

        Customer customer = createCustomer("类型引用客户");
        createProject(null, customer.getId(), "USED_TYPE", null);

        mockMvc.perform(delete(ADMIN_PROJECT_TYPES + "/" + id).header("Authorization", adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("已有 1 个项目使用该类型")));
    }

    @Test
    @DisplayName("项目创建：带 typeCode 落库一致（type 与 typeCode 同步）；非法 typeCode 400")
    void createProjectWithTypeCode() throws Exception {
        Customer customer = createCustomer("类型落库客户");
        JsonNode project = createProject(null, customer.getId(), "CODE_AUDIT", null);
        assertEquals("CODE_AUDIT", project.path("typeCode").asText());
        assertEquals("CODE_AUDIT", project.path("type").asText());

        mockMvc.perform(post(PROJECTS).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(projectJson(null, customer.getId().toString(), "NO_SUCH_TYPE", null)))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ 项目编码

    @Test
    @DisplayName("项目编码：未传 → 自动生成 PRJ-yyyy-MM-dd-XXXXXX，两次不同")
    void autoGenerateProjectCode() throws Exception {
        Customer customer = createCustomer("编码客户A");
        JsonNode first = createProject(null, customer.getId(), null, null);
        JsonNode second = createProject(null, customer.getId(), null, null);

        String code1 = first.path("code").asText();
        String code2 = second.path("code").asText();
        assertTrue(code1.matches("^PRJ-\\d{4}-\\d{2}-\\d{2}-[A-Z]{6}$"), "格式不符: " + code1);
        assertEquals(21, code1.length(), "PRJ- + 10 位日期 + - + 6 位 = 21");
        assertNotEquals(code1, code2, "两次创建编码必须不同");
        assertTrue(projectRepository.existsByCode(code1));
    }

    @Test
    @DisplayName("项目编码：projectCode 别名同样生效")
    void generateCodeFromProjectCodeAlias() throws Exception {
        Customer customer = createCustomer("编码客户B");
        JsonNode created = createProject(null, customer.getId(), null, "PRJ-2026-11-06-KXQZAB");
        assertEquals("PRJ-2026-11-06-KXQZAB", created.path("code").asText());
    }

    @Test
    @DisplayName("项目编码：显式传入保留；重复 409")
    void explicitProjectCodePreservedAndUnique() throws Exception {
        Customer customer = createCustomer("编码客户C");
        JsonNode created = createProject("PRJ-2026-01-02-ABCDEF", customer.getId(), null, null);
        assertEquals("PRJ-2026-01-02-ABCDEF", created.path("code").asText());

        mockMvc.perform(post(PROJECTS).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(projectJson("PRJ-2026-01-02-ABCDEF", customer.getId().toString(), null, null)))
                .andExpect(status().isConflict());

        // 历史非日期形编码保持兼容（仅校验唯一）
        JsonNode legacy = createProject("PRJ-HISTORY", customer.getId(), null, null);
        assertEquals("PRJ-HISTORY", legacy.path("code").asText());
    }

    @Test
    @DisplayName("项目编码：日期形但尾巴不合法 → 400")
    void invalidDateShapedCodeRejected() throws Exception {
        Customer customer = createCustomer("编码客户D");
        mockMvc.perform(post(PROJECTS).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(projectJson("PRJ-2026-01-02-abc", customer.getId().toString(), null, null)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("项目更新：未传编码 → 保留原编码；编码对外不变")
    void updateKeepsProjectCode() throws Exception {
        Customer customer = createCustomer("编码客户E");
        JsonNode created = createProject(null, customer.getId(), null, null);
        String code = created.path("code").asText();
        UUID id = UUID.fromString(created.path("id").asText());

        mockMvc.perform(put(PROJECTS + "/" + id).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"customerId\":\"" + customer.getId() + "\",\"name\":\"改名项目\","
                                + "\"managerId\":\"" + adminUserId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.code").value(code))
                .andExpect(jsonPath("$.data.name").value("改名项目"));

        Project reloaded = projectRepository.findById(id).orElseThrow();
        assertEquals(code, reloaded.getCode());
        // 未传 type → 保持默认 PENTEST，且能匹配内置类型，故 typeCode 回填为 PENTEST
        assertEquals("PENTEST", reloaded.getType());
        assertEquals("PENTEST", reloaded.getTypeCode());
    }

    @Test
    @DisplayName("迁移回填口径：历史 type=REAUDIT 匹配不到 code → typeCode 为空且不报错")
    void legacyTypeWithoutCodeLeavesTypeCodeNull() throws Exception {
        Customer customer = createCustomer("历史类型客户");
        JsonNode created = createProject(null, customer.getId(), null, null);
        UUID id = UUID.fromString(created.path("id").asText());

        mockMvc.perform(put(PROJECTS + "/" + id).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"customerId\":\"" + customer.getId() + "\",\"name\":\"审计项目\","
                                + "\"type\":\"REAUDIT\",\"managerId\":\"" + adminUserId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("REAUDIT"));

        Project reloaded = projectRepository.findById(id).orElseThrow();
        assertEquals("REAUDIT", reloaded.getType());
        assertNull(reloaded.getTypeCode(), "REAUDIT 不在 project_types 中，typeCode 应为空");
    }

    @Test
    @DisplayName("审计：项目类型写操作各 1 条 UPDATE_PROJECT_TYPE")
    void projectTypeWritesAreAuditedOnce() throws Exception {
        mockMvc.perform(post(ADMIN_PROJECT_TYPES).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"code\":\"AUD_TYPE\",\"name\":\"审计类型\"}"))
                .andExpect(status().isCreated());

        List<AuditLog> logs = auditLogRepository.findByAction("UPDATE_PROJECT_TYPE");
        assertEquals(1, logs.size(), "一次新建应恰好 1 条审计");
        assertTrue(logs.get(0).getDetails().contains("AUD_TYPE"), logs.get(0).getDetails());
    }

    @Test
    @DisplayName("行业：内置数量 ≥20（GB/T 4754 门类+大类）")
    void builtinIndustryCountAndIdempotent() {
        long builtin = industryTypeRepository.countByBuiltinTrueAndDeletedAtIsNull();
        assertTrue(builtin >= 20, "内置行业应 ≥20，实际 " + builtin);
        assertFalse(industryTypeService.ensureBuiltin(), "表非空时应跳过");
        assertEquals(builtin, industryTypeRepository.countByBuiltinTrueAndDeletedAtIsNull());
    }

    @Test
    @DisplayName("审计：行业/项目类型管理页对非 ADMIN 不可见（列表 403）")
    void adminListForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(get(ADMIN_PROJECT_TYPES).header("Authorization", leaderToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(ADMIN_INDUSTRIES))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ 软删口径（含软删行的唯一性）

    @Test
    @DisplayName("行业：软删后的 code 不可复用（唯一性含软删行）→ 409")
    void softDeletedIndustryCodeNotReusable() throws Exception {
        createIndustry("CUS-SOFT", "待软删行业");
        IndustryType type = industryTypeRepository.findByCode("CUS-SOFT").orElseThrow();

        mockMvc.perform(delete(ADMIN_INDUSTRIES + "/" + type.getId()).header("Authorization", adminToken))
                .andExpect(status().isOk());
        assertNotNull(industryTypeRepository.findByCode("CUS-SOFT").orElseThrow().getDeletedAt(),
                "软删行仍在库中且 deleted_at 非空");

        mockMvc.perform(post(ADMIN_INDUSTRIES).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content(industryJson("CUS-SOFT", "重建同名编码", null, null)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("项目类型：软删后的 code 不可复用（唯一性含软删行）→ 409")
    void softDeletedProjectTypeCodeNotReusable() throws Exception {
        String created = mockMvc.perform(post(ADMIN_PROJECT_TYPES).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"code\":\"SOFT_TYPE\",\"name\":\"待软删类型\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(created).path("data").path("id").asText();

        mockMvc.perform(delete(ADMIN_PROJECT_TYPES + "/" + id).header("Authorization", adminToken))
                .andExpect(status().isOk());
        assertNotNull(projectTypeRepository.findByCode("SOFT_TYPE").orElseThrow().getDeletedAt(),
                "软删行仍在库中且 deleted_at 非空");

        mockMvc.perform(post(ADMIN_PROJECT_TYPES).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"code\":\"SOFT_TYPE\",\"name\":\"重建同名类型\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get(PROJECT_TYPES).header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code=='SOFT_TYPE')]", org.hamcrest.Matchers.hasSize(0)));
    }

    // ------------------------------------------------------------------ 管理员列表与编码归一

    @Test
    @DisplayName("管理员列表：includeDisabled=false 隐藏停用项，true 显示")
    void adminListIncludeDisabledFilter() throws Exception {
        String created = mockMvc.perform(post(ADMIN_PROJECT_TYPES).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"code\":\"HIDDEN_TYPE\",\"name\":\"停用类型\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(created).path("data").path("id").asText();

        mockMvc.perform(put(ADMIN_PROJECT_TYPES + "/" + id).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk());

        mockMvc.perform(get(ADMIN_PROJECT_TYPES).header("Authorization", adminToken)
                        .param("keyword", "HIDDEN_TYPE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mockMvc.perform(get(ADMIN_PROJECT_TYPES).header("Authorization", adminToken)
                        .param("keyword", "HIDDEN_TYPE").param("includeDisabled", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].enabled").value(false));
    }

    @Test
    @DisplayName("项目类型：自定义 code 统一大写落库，下拉返回大写 code")
    void projectTypeCodeNormalizedToUpper() throws Exception {
        mockMvc.perform(post(ADMIN_PROJECT_TYPES).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"code\":\"custom_blue\",\"name\":\"蓝队评估\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("CUSTOM_BLUE"))
                .andExpect(jsonPath("$.data.builtin").value(false));

        mockMvc.perform(get(PROJECT_TYPES).header("Authorization", engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code=='CUSTOM_BLUE')]", org.hamcrest.Matchers.hasSize(1)));
    }
}
