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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * 行业 / 项目类型「管理员可管理」验收（t132）
 *
 * <p>重点：**内置保护**（code/name 不可改、不可删）、**引用检查**（被客户 / 项目引用时 409 且消息给出 N）、
 * **软删**（deleted_at 写入后列表与检索不再返回）、**审计**（每个写操作恰好 1 条）、
 * **公开下拉**（登录即可、仅 enabled 且未删）、**typeCode 落库与非法/停用 400**，
 * 以及 **V28 迁移**的幂等守卫与回填口径（含 t67 的 {@code OTHER} → NULL）。
 *
 * <p>口径说明（对应需求「同步 type 为对应 name 或 code，择一并说明」）：
 * **取 code** —— `projects.type` 存的就是编码（历史值与过滤器、状态机、导入、前端筛选全按 code 比较），
 * 若改存 name 会让 `<code>type=PENTEST</code>` 之类的既有查询与 20+ 处调用全部失效。
 */
@DisplayName("行业/项目类型管理员管理测试")
public class MetaTypeAdminTest extends BaseTest {

    private static final String ADMIN_INDUSTRIES = "/api/v1/admin/industry-types";
    private static final String INDUSTRIES = "/api/v1/industry-types";
    private static final String ADMIN_PROJECT_TYPES = "/api/v1/admin/project-types";
    private static final String PROJECT_TYPES = "/api/v1/project-types";
    private static final String PROJECTS = "/api/v1/projects";
    private static final String CUSTOMERS = "/api/v1/customers";

    private static final Path MIGRATION_V28 =
            Path.of("src/main/resources/db/migration/V28__project_types_and_project_code.sql");
    private static final Path MANUAL_DDL = Path.of("../deploy/manual-ddl.sql");

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
        // 启动器已插入；此处兜底（同时验证 ensureBuiltin 幂等：表非空时不重复插入）
        industryTypeService.ensureBuiltin();
        projectTypeService.ensureBuiltin();
    }

    // ------------------------------------------------------------------ 工具

    private String industryBody(String code, String name, String parentCode, Integer sortOrder, Boolean enabled) {
        StringBuilder sb = new StringBuilder("{");
        if (code != null) {
            sb.append("\"code\":\"").append(code).append("\",");
        }
        if (name != null) {
            sb.append("\"name\":\"").append(name).append("\",");
        }
        if (parentCode != null) {
            sb.append("\"parentCode\":\"").append(parentCode).append("\",");
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
                        .contentType(APPLICATION_JSON).content(industryBody(code, name, null, 200, null)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    private IndustryType builtinIndustry() {
        return industryTypeRepository.findAll().stream()
                .filter(t -> t.isBuiltin() && !t.isDeleted())
                .findFirst()
                .orElseThrow();
    }

    private ProjectType builtinProjectType() {
        return projectTypeRepository.findAll().stream()
                .filter(t -> t.isBuiltin() && !t.isDeleted())
                .findFirst()
                .orElseThrow();
    }

    /** 建 JSON（只给 name + 可选 industryCode） */
    private void createCustomer(String name, String industryCode) throws Exception {
        String body = industryCode == null
                ? "{\"name\":\"" + name + "\"}"
                : "{\"name\":\"" + name + "\",\"industryCode\":\"" + industryCode + "\"}";
        mockMvc.perform(post(CUSTOMERS).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    private Customer customerByName(String name) {
        return customerRepository.findAll().stream()
                .filter(c -> name.equals(c.getName()))
                .max(java.util.Comparator.comparing(Customer::getCreatedAt))
                .orElseThrow();
    }

    private JsonNode createProject(UUID customerId, String typeCode, String type) throws Exception {
        StringBuilder sb = new StringBuilder("{\"customerId\":\"").append(customerId).append("\",")
                .append("\"name\":\"类型验收项目\",")
                .append("\"managerId\":\"").append(adminUserId).append("\"");
        if (typeCode != null) {
            sb.append(",\"typeCode\":\"").append(typeCode).append("\"");
        }
        if (type != null) {
            sb.append(",\"type\":\"").append(type).append("\"");
        }
        String body = mockMvc.perform(post(PROJECTS).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content(sb.append("}").toString()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    private String createProjectType(String code, String name) throws Exception {
        String body = mockMvc.perform(post(ADMIN_PROJECT_TYPES).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asText();
    }

    // ------------------------------------------------------------------ 一、行业 ADMIN CRUD

    @Test
    @DisplayName("行业：新建自定义项 201 且 builtin=false；code 重复 409")
    void createCustomIndustry() throws Exception {
        JsonNode created = createIndustry("T132-IND", "自定义行业");
        assertEquals("T132-IND", created.path("code").asText());
        assertFalse(created.path("builtin").asBoolean(true), "新建项必须 builtin=false");

        mockMvc.perform(post(ADMIN_INDUSTRIES).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content(industryBody("T132-IND", "重复编码", null, null, null)))
                .andExpect(status().isConflict());

        // 管理列表可查（含 builtin/enabled/sortOrder 字段）
        mockMvc.perform(get(ADMIN_INDUSTRIES).header("Authorization", adminToken)
                        .param("keyword", "T132-IND"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].builtin").value(false))
                .andExpect(jsonPath("$.data.content[0].sortOrder").value(200));
    }

    @Test
    @DisplayName("行业：内置项改 name → 403「内置行业不可修改」")
    void renameBuiltinIndustryForbidden() throws Exception {
        IndustryType builtin = builtinIndustry();
        mockMvc.perform(put(ADMIN_INDUSTRIES + "/" + builtin.getId()).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(industryBody(null, "被篡改的名字", null, null, null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("内置行业不可修改")));

        assertEquals(builtin.getName(),
                industryTypeRepository.findById(builtin.getId()).orElseThrow().getName(),
                "内置项名称不得变化");
    }

    @Test
    @DisplayName("行业：内置项改 sortOrder / enabled → 200")
    void updateBuiltinIndustryOrderAllowed() throws Exception {
        IndustryType builtin = builtinIndustry();
        mockMvc.perform(put(ADMIN_INDUSTRIES + "/" + builtin.getId()).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content(industryBody(null, null, null, 321, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sortOrder").value(321));

        mockMvc.perform(put(ADMIN_INDUSTRIES + "/" + builtin.getId()).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content(industryBody(null, null, null, null, false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));
    }

    @Test
    @DisplayName("行业：删除内置项 → 403")
    void deleteBuiltinIndustryForbidden() throws Exception {
        IndustryType builtin = builtinIndustry();
        mockMvc.perform(delete(ADMIN_INDUSTRIES + "/" + builtin.getId()).header("Authorization", adminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("内置行业不可修改")));

        assertNull(industryTypeRepository.findById(builtin.getId()).orElseThrow().getDeletedAt(),
                "内置项不得被软删");
    }

    @Test
    @DisplayName("行业：被 2 个客户引用时删除 → 409「已有 2 个客户使用该行业」")
    void deleteReferencedIndustryConflict() throws Exception {
        createIndustry("T132-REF", "被引用行业");
        createCustomer("引用客户1", "T132-REF");
        createCustomer("引用客户2", "T132-REF");

        IndustryType type = industryTypeRepository.findByCode("T132-REF").orElseThrow();
        mockMvc.perform(delete(ADMIN_INDUSTRIES + "/" + type.getId()).header("Authorization", adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("已有 2 个客户使用该行业")));

        assertNull(industryTypeRepository.findByCode("T132-REF").orElseThrow().getDeletedAt(),
                "被引用时不得软删");
    }

    @Test
    @DisplayName("行业：删除自定义项 → 软删生效（检索不再返回、deleted_at 非空、二次删除 404）")
    void deleteCustomIndustrySoftDelete() throws Exception {
        createIndustry("T132-DEL", "待删行业");
        IndustryType type = industryTypeRepository.findByCode("T132-DEL").orElseThrow();

        mockMvc.perform(delete(ADMIN_INDUSTRIES + "/" + type.getId()).header("Authorization", adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get(INDUSTRIES).header("Authorization", adminToken).param("keyword", "T132-DEL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", org.hamcrest.Matchers.hasSize(0)));

        IndustryType after = industryTypeRepository.findByCode("T132-DEL").orElseThrow();
        assertNotNull(after.getDeletedAt(), "应为软删（行仍在库中）");
        assertFalse(after.isEnabled());

        mockMvc.perform(delete(ADMIN_INDUSTRIES + "/" + type.getId()).header("Authorization", adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("行业：非 ADMIN 全部写操作与列表 403/401")
    void industryWritesRequireAdmin() throws Exception {
        String payload = industryBody("T132-PL", "组长试图建", null, null, null);
        mockMvc.perform(post(ADMIN_INDUSTRIES).header("Authorization", leaderToken)
                        .contentType(APPLICATION_JSON).content(payload))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(ADMIN_INDUSTRIES).header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON).content(payload))
                .andExpect(status().isForbidden());

        IndustryType builtin = builtinIndustry();
        mockMvc.perform(put(ADMIN_INDUSTRIES + "/" + builtin.getId()).header("Authorization", leaderToken)
                        .contentType(APPLICATION_JSON).content(industryBody(null, null, null, 7, null)))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete(ADMIN_INDUSTRIES + "/" + builtin.getId()).header("Authorization", engineerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(ADMIN_INDUSTRIES).header("Authorization", leaderToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(ADMIN_INDUSTRIES))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("行业：一次新建恰好 1 条 UPDATE_INDUSTRY_TYPE 审计（details 含 code）")
    void industryWriteAuditedOnce() throws Exception {
        createIndustry("T132-AUD", "审计行业");

        List<AuditLog> logs = auditLogRepository.findByAction("UPDATE_INDUSTRY_TYPE");
        assertEquals(1, logs.size(), "一次写操作应恰好 1 条审计（拦截器已跳过该端点）");
        assertTrue(logs.get(0).getDetails().contains("T132-AUD"), logs.get(0).getDetails());
    }

    // ------------------------------------------------------------------ 二、项目类型 ADMIN CRUD

    @Test
    @DisplayName("项目类型：内置 5 条齐全，且 ensureBuiltin 不重复插入")
    void builtinProjectTypes() {
        List<String> codes = projectTypeRepository
                .findByEnabledTrueAndDeletedAtIsNullOrderByBuiltinDescSortOrderAscCodeAsc()
                .stream().map(ProjectType::getCode).toList();
        assertTrue(codes.containsAll(List.of("PENTEST", "CODE_AUDIT", "EMERGENCY", "COMPLIANCE", "TRAINING")),
                codes.toString());
        assertEquals(5, projectTypeRepository.countByBuiltinTrueAndDeletedAtIsNull(), "内置应恰好 5 条");
        assertFalse(projectTypeService.ensureBuiltin(), "表非空时应跳过");
        assertEquals(5, projectTypeRepository.countByBuiltinTrueAndDeletedAtIsNull(), "二次调用不得重复插入");
    }

    @Test
    @DisplayName("项目类型：自定义项可建/改/删；内置项改 name 403、删 403")
    void projectTypeCrud() throws Exception {
        String id = createProjectType("T132_CUSTOM", "自定义类型");

        mockMvc.perform(put(ADMIN_PROJECT_TYPES + "/" + id).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"改名后的类型\",\"sortOrder\":77}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("改名后的类型"))
                .andExpect(jsonPath("$.data.sortOrder").value(77));

        ProjectType builtin = builtinProjectType();
        mockMvc.perform(put(ADMIN_PROJECT_TYPES + "/" + builtin.getId()).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"name\":\"改内置\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("内置项目类型不可修改")));
        mockMvc.perform(delete(ADMIN_PROJECT_TYPES + "/" + builtin.getId()).header("Authorization", adminToken))
                .andExpect(status().isForbidden());
        assertNull(projectTypeRepository.findById(builtin.getId()).orElseThrow().getDeletedAt(),
                "内置项目类型不得被软删");

        mockMvc.perform(delete(ADMIN_PROJECT_TYPES + "/" + id).header("Authorization", adminToken))
                .andExpect(status().isOk());
        assertNotNull(projectTypeRepository.findById(UUID.fromString(id)).orElseThrow().getDeletedAt(),
                "删除应为软删");
    }

    @Test
    @DisplayName("项目类型：被 2 个项目引用时删除 → 409「已有 2 个项目使用该类型」")
    void deleteReferencedProjectTypeConflict() throws Exception {
        String id = createProjectType("T132_USED", "被引用类型");
        createCustomer("类型引用客户", null);
        UUID customerId = customerByName("类型引用客户").getId();
        createProject(customerId, "T132_USED", null);
        createProject(customerId, "T132_USED", null);

        mockMvc.perform(delete(ADMIN_PROJECT_TYPES + "/" + id).header("Authorization", adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("已有 2 个项目使用该类型")));
    }

    @Test
    @DisplayName("公开下拉：登录即可，仅返回 enabled 且未软删的类型")
    void publicOptionsOnlyEnabled() throws Exception {
        String id = createProjectType("T132_FROZEN", "冻结类型");

        mockMvc.perform(get(PROJECT_TYPES).header("Authorization", engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code=='T132_FROZEN')]", org.hamcrest.Matchers.hasSize(1)));

        mockMvc.perform(put(ADMIN_PROJECT_TYPES + "/" + id).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk());
        mockMvc.perform(get(PROJECT_TYPES).header("Authorization", engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code=='T132_FROZEN')]", org.hamcrest.Matchers.hasSize(0)));

        // 软删后同样不返回
        mockMvc.perform(put(ADMIN_PROJECT_TYPES + "/" + id).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"enabled\":true}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete(ADMIN_PROJECT_TYPES + "/" + id).header("Authorization", adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get(PROJECT_TYPES).header("Authorization", engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.code=='T132_FROZEN')]", org.hamcrest.Matchers.hasSize(0)));
    }

    @Test
    @DisplayName("项目类型：一次新建恰好 1 条 UPDATE_PROJECT_TYPE 审计（details 含 code）")
    void projectTypeWriteAuditedOnce() throws Exception {
        createProjectType("T132_AUDT", "审计类型");

        List<AuditLog> logs = auditLogRepository.findByAction("UPDATE_PROJECT_TYPE");
        assertEquals(1, logs.size(), "一次写操作应恰好 1 条审计");
        assertTrue(logs.get(0).getDetails().contains("T132_AUDT"), logs.get(0).getDetails());
    }

    // ------------------------------------------------------------------ 三、typeCode 落库

    @Test
    @DisplayName("建项目：typeCode 落库一致，type 同步为 code（本任务口径）")
    void createProjectWithTypeCode() throws Exception {
        createCustomer("typeCode 客户", null);
        UUID customerId = customerByName("typeCode 客户").getId();

        JsonNode created = createProject(customerId, "CODE_AUDIT", null);
        assertEquals("CODE_AUDIT", created.path("typeCode").asText());
        assertEquals("CODE_AUDIT", created.path("type").asText(), "type 与 typeCode 同步为 code");

        Project reloaded = projectRepository.findById(UUID.fromString(created.path("id").asText())).orElseThrow();
        assertEquals("CODE_AUDIT", reloaded.getTypeCode());
        assertEquals("CODE_AUDIT", reloaded.getType());
    }

    @Test
    @DisplayName("建项目：typeCode 不存在 / 已停用 → 400")
    void invalidOrDisabledTypeCodeRejected() throws Exception {
        createCustomer("非法类型客户", null);
        UUID customerId = customerByName("非法类型客户").getId();

        mockMvc.perform(post(PROJECTS).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"customerId\":\"" + customerId + "\",\"name\":\"非法类型项目\","
                                + "\"managerId\":\"" + adminUserId + "\",\"typeCode\":\"NO_SUCH_TYPE\"}"))
                .andExpect(status().isBadRequest());

        String id = createProjectType("T132_OFF", "停用类型");
        mockMvc.perform(put(ADMIN_PROJECT_TYPES + "/" + id).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"enabled\":false}"))
                .andExpect(status().isOk());
        mockMvc.perform(post(PROJECTS).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"customerId\":\"" + customerId + "\",\"name\":\"停用类型项目\","
                                + "\"managerId\":\"" + adminUserId + "\",\"typeCode\":\"T132_OFF\"}"))
                .andExpect(status().isBadRequest());

        assertEquals(0, projectRepository.count(), "非法/停用 typeCode 不得落库");
    }

    @Test
    @DisplayName("回填口径：只传 type（无 typeCode）→ 命中表则回填 type_code，OTHER/REAUDIT 保持 NULL")
    void typeBackfillSemantics() throws Exception {
        createCustomer("回填客户", null);
        UUID customerId = customerByName("回填客户").getId();

        JsonNode pentest = createProject(customerId, null, "PENTEST");
        assertEquals("PENTEST", pentest.path("typeCode").asText(), "PENTEST 在 project_types 中，应回填");
        assertEquals("PENTEST", pentest.path("type").asText());

        // 历史值 REAUDIT：静态白名单允许写入，但 project_types 里没有 → type_code 保持 NULL
        JsonNode reaudit = createProject(customerId, null, "REAUDIT");
        assertEquals("REAUDIT", reaudit.path("type").asText());
        assertTrue(reaudit.path("typeCode").isNull(), "REAUDIT 未在 project_types 中，typeCode 应为空");

        // t67 的 OTHER：不在内置清单（内置恰好 5 条），只可能是历史行 —— 直接落库模拟，
        // 回填 SQL 的 JOIN 匹配不到 → type_code 保持 NULL（「匹配不到保持 NULL」）
        Project legacyOther = projectRepository.save(Objects.requireNonNull(Project.builder()
                .code("T132-LEGACY-OTHER")
                .customerId(customerId)
                .name("历史 OTHER 项目")
                .type("OTHER")
                .status("DRAFT")
                .managerId(adminUserId)
                .createdBy(adminUserId)
                .build()));
        assertNull(legacyOther.getTypeCode(), "OTHER 匹配不到 project_types.code，type_code 应为 NULL");

        // 历史行仍可正常读取（列表不因 type_code 为空而报错）
        mockMvc.perform(get(PROJECTS).header("Authorization", adminToken)
                        .param("keyword", "T132-LEGACY-OTHER"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].type").value("OTHER"))
                .andExpect(jsonPath("$.data.content[0].code").value("T132-LEGACY-OTHER"));
    }

    // ------------------------------------------------------------------ 四、V28 迁移

    @Test
    @DisplayName("V28：两套 DDL 与幂等守卫齐全（与 manual-ddl 等价）")
    void migrationScriptGuards() throws Exception {
        String sql = Files.readString(MIGRATION_V28, StandardCharsets.UTF_8);
        String manual = Files.readString(MANUAL_DDL, StandardCharsets.UTF_8);

        // ① 行业软删列 / ② 项目类型表 / ③ projects.type_code 三块 DDL
        assertTrue(sql.contains("ALTER TABLE industry_types ADD COLUMN IF NOT EXISTS deleted_at"),
                "缺少 industry_types.deleted_at 的幂等 ALTER");
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS project_types"), "缺少 project_types 幂等建表");
        assertTrue(sql.contains("CREATE UNIQUE INDEX IF NOT EXISTS uk_project_types_code"), "缺少 code 唯一索引");
        assertTrue(sql.contains("ALTER TABLE projects ADD COLUMN IF NOT EXISTS type_code"), "缺少 projects.type_code");

        // 内置 5 项：每条都用 WHERE NOT EXISTS 守卫（重跑只补缺）
        for (String code : List.of("PENTEST", "CODE_AUDIT", "EMERGENCY", "COMPLIANCE", "TRAINING")) {
            assertTrue(sql.contains("'" + code + "'"), "缺少内置项目类型 " + code);
        }
        assertEquals(5, countOccurrences(sql, "WHERE NOT EXISTS (SELECT 1 FROM project_types WHERE code ="),
                "内置项插入必须逐条 WHERE NOT EXISTS 守卫");
        // 回填只填空值，重跑不覆盖
        assertTrue(sql.contains("WHERE p.type_code IS NULL"), "回填必须只处理 type_code IS NULL 的行");

        // manual-ddl 等价段：同一段里出现同样的两套 DDL 与 5 个内置 code
        int v28 = manual.indexOf("V28：项目类型管理");
        assertTrue(v28 > 0, "manual-ddl 缺少 V28 段");
        String manualV28 = manual.substring(v28);
        assertTrue(manualV28.contains("ALTER TABLE industry_types ADD COLUMN IF NOT EXISTS deleted_at"));
        assertTrue(manualV28.contains("CREATE TABLE IF NOT EXISTS project_types"));
        assertTrue(manualV28.contains("ALTER TABLE projects ADD COLUMN IF NOT EXISTS type_code"));
        assertEquals(5, countOccurrences(manualV28, "WHERE NOT EXISTS (SELECT 1 FROM project_types WHERE code ="),
                "manual-ddl V28 段内置项同样逐条守卫");
    }

    @Test
    @DisplayName("V28：内置项落库与实体/常量一致（code、名称、builtin、sort_order）")
    void builtinProjectTypesMatchSeed() {
        var builtin = projectTypeRepository
                .findByEnabledTrueAndDeletedAtIsNullOrderByBuiltinDescSortOrderAscCodeAsc().stream()
                .filter(ProjectType::isBuiltin)
                .toList();
        assertEquals(5, builtin.size());
        assertEquals(List.of("PENTEST", "CODE_AUDIT", "EMERGENCY", "COMPLIANCE", "TRAINING"),
                builtin.stream().map(ProjectType::getCode).toList(), "内置顺序按 sort_order");
        assertEquals("渗透测试", builtin.get(0).getName());
        assertEquals(10, builtin.get(0).getSortOrder());
        assertEquals(50, builtin.get(4).getSortOrder());
    }

    private static int countOccurrences(String haystack, String needle) {
        int count = 0;
        int from = 0;
        while (true) {
            int at = haystack.indexOf(needle, from);
            if (at < 0) {
                return count;
            }
            count++;
            from = at + needle.length();
        }
    }

    @Test
    @DisplayName("V28：内置 code 不可被重复创建（应用层 existsByCode → 409；DDL 侧 uk_*_code 兜底）")
    void duplicateBuiltinCodeRejected() throws Exception {
        mockMvc.perform(post(ADMIN_PROJECT_TYPES).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"code\":\"PENTEST\",\"name\":\"重复的内置类型\"}"))
                .andExpect(status().isConflict());
        assertEquals(1, projectTypeRepository.findAll().stream()
                .filter(t -> "PENTEST".equals(t.getCode())).count(), "库中 PENTEST 仍只有 1 条");

        // 行业内置同理（门类 A 农林牧渔）
        mockMvc.perform(post(ADMIN_INDUSTRIES).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(industryBody("A", "重复的内置行业", null, null, null)))
                .andExpect(status().isConflict());
        assertEquals(1, industryTypeRepository.findAll().stream()
                .filter(t -> "A".equals(t.getCode())).count(), "库中 A 仍只有 1 条");
    }

    @Test
    @DisplayName("软删口径：软删后的 code 不可复用（唯一性含软删行）→ 409")
    void softDeletedCodeNotReusable() throws Exception {
        // 行业
        createIndustry("T132-SOFT", "待软删行业");
        IndustryType industry = industryTypeRepository.findByCode("T132-SOFT").orElseThrow();
        mockMvc.perform(delete(ADMIN_INDUSTRIES + "/" + industry.getId()).header("Authorization", adminToken))
                .andExpect(status().isOk());
        assertNotNull(industryTypeRepository.findByCode("T132-SOFT").orElseThrow().getDeletedAt());
        mockMvc.perform(post(ADMIN_INDUSTRIES).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(industryBody("T132-SOFT", "重建同名编码", null, null, null)))
                .andExpect(status().isConflict());

        // 项目类型
        String id = createProjectType("T132_SOFT", "待软删类型");
        mockMvc.perform(delete(ADMIN_PROJECT_TYPES + "/" + id).header("Authorization", adminToken))
                .andExpect(status().isOk());
        assertNotNull(projectTypeRepository.findById(UUID.fromString(id)).orElseThrow().getDeletedAt());
        mockMvc.perform(post(ADMIN_PROJECT_TYPES).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"code\":\"T132_SOFT\",\"name\":\"重建同名类型\"}"))
                .andExpect(status().isConflict());
    }
}
