package com.vulnaudit.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.vulnaudit.entity.FormDefinition;
import com.vulnaudit.entity.FormField;
import com.vulnaudit.entity.FormSubmission;
import com.vulnaudit.entity.FormValue;
import com.vulnaudit.repository.FormDefinitionRepository;
import com.vulnaudit.repository.FormFieldRepository;
import com.vulnaudit.repository.FormSubmissionRepository;
import com.vulnaudit.repository.FormValueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
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
 * 自定义填表系统 · 模板与字段 CRUD（t178，第 1/5 块）
 *
 * <p>覆盖：CRUD、code 唯一/不可改、软删与引用保护、字段 key 唯一、类型/source 白名单、SELECT options、
 * 重排序连续、已有数据禁止改类型/删除、权限、审计条数、V35 脚本守卫（四表 + 索引 + collation）。
 */
@DisplayName("填表系统-模板管理测试")
public class FormDefinitionTest extends BaseTest {

    private static final String BASE = "/api/v1/admin/form-definitions";

    @Autowired
    private FormDefinitionRepository definitionRepository;
    @Autowired
    private FormFieldRepository fieldRepository;
    @Autowired
    private FormSubmissionRepository submissionRepository;
    @Autowired
    private FormValueRepository formValueRepository;
    @Autowired
    private com.vulnaudit.repository.AuditLogRepository auditLogRepository;

    private FormDefinition definition;

    @BeforeEach
    void setUpDefinition() {
        definition = definitionRepository.save(Objects.requireNonNull(FormDefinition.builder()
                .code("FORM-T178-" + System.nanoTime())
                .name("季度巡检表")
                .description("t178 夹具")
                .enabled(true)
                .createdBy(adminUserId)
                .build()));
    }

    // ------------------------------------------------------------------ 工具

    private JsonNode createDefinition(String code, String name) throws Exception {
        String body = mockMvc.perform(post(BASE).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"" + name + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    private String addField(UUID definitionId, String body) throws Exception {
        String created = mockMvc.perform(post(BASE + "/" + definitionId + "/fields")
                        .header("Authorization", adminToken).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(created).path("data").path("id").asText();
    }

    private FormField fieldFixture(UUID definitionId, String key, int sortOrder) {
        return fieldRepository.save(Objects.requireNonNull(FormField.builder()
                .definitionId(definitionId).fieldKey(key).label("字段" + key)
                .fieldType("TEXT").required(false).sortOrder(sortOrder).build()));
    }

    // ------------------------------------------------------------------ 一、模板 CRUD

    @Test
    @DisplayName("模板：创建 → 详情 → 改名 → 列表 → 软删后列表不含且详情 404")
    void definitionCrud() throws Exception {
        String code = "FORM-QUARTER-" + System.nanoTime();
        JsonNode created = createDefinition(code, "季度巡检表");
        UUID id = UUID.fromString(created.path("id").asText());
        assertEquals(code, created.path("code").asText());

        mockMvc.perform(get(BASE + "/" + id).header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("季度巡检表"))
                .andExpect(jsonPath("$.data.fields").isArray());

        mockMvc.perform(put(BASE + "/" + id).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"季度巡检表V2\",\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("季度巡检表V2"))
                .andExpect(jsonPath("$.data.enabled").value(false));

        mockMvc.perform(get(BASE).header("Authorization", adminToken).param("keyword", "季度巡检表V2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].fieldCount").exists());

        mockMvc.perform(delete(BASE + "/" + id).header("Authorization", adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get(BASE).header("Authorization", adminToken).param("keyword", code))
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get(BASE + "/" + id).header("Authorization", adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("模板：code 重复 → 409（含已软删的编码也不可复用）")
    void duplicateCode() throws Exception {
        String code = "FORM-DUP-" + System.nanoTime();
        JsonNode created = createDefinition(code, "重复码模板");
        mockMvc.perform(post(BASE).header("Authorization", adminToken).contentType(APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"另一个\"}"))
                .andExpect(status().isConflict());

        // 软删后仍不可复用（编码是历史表单的稳定标识）
        mockMvc.perform(delete(BASE + "/" + created.path("id").asText()).header("Authorization", adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(post(BASE).header("Authorization", adminToken).contentType(APPLICATION_JSON)
                        .content("{\"code\":\"" + code + "\",\"name\":\"复用尝试\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("模板：改 code → 403（code 创建后不可改）")
    void codeImmutable() throws Exception {
        mockMvc.perform(put(BASE + "/" + definition.getId()).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"code\":\"NEW-CODE\"}"))
                .andExpect(status().isForbidden());

        // 传原值不算改，正常 200
        mockMvc.perform(put(BASE + "/" + definition.getId()).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"code\":\"" + definition.getCode() + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("模板：被表单实例引用时删除 → 409 且消息带引用数")
    void deleteReferencedDefinition() throws Exception {
        submissionRepository.saveAndFlush(Objects.requireNonNull(FormSubmission.builder()
                .definitionId(definition.getId()).title("已生成的表单").status("DRAFT")
                .createdBy(adminUserId).build()));

        mockMvc.perform(delete(BASE + "/" + definition.getId()).header("Authorization", adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("1 个表单实例")));

        // 409 时不做软删（事务未提交变更）
        assertNull(definitionRepository.findById(definition.getId()).orElseThrow().getDeletedAt(),
                "被引用时不应被软删");
    }

    // ------------------------------------------------------------------ 二、字段 CRUD

    @Test
    @DisplayName("字段：新增 3 个（1 库字段 + 2 手填）→ 详情按 sort_order 返回，sourceLabel 正确")
    void addThreeFieldsInOrder() throws Exception {
        addField(definition.getId(), "{\"fieldKey\":\"customerName\",\"label\":\"客户名称\","
                + "\"fieldType\":\"TEXT\",\"sourceType\":\"CUSTOMER.NAME\",\"sortOrder\":0}");
        addField(definition.getId(), "{\"fieldKey\":\"remark\",\"label\":\"备注\",\"fieldType\":\"TEXTAREA\"}");
        addField(definition.getId(), "{\"fieldKey\":\"level\",\"label\":\"等级\",\"fieldType\":\"SELECT\","
                + "\"options\":\"[\\\"高\\\",\\\"中\\\",\\\"低\\\"]\"}");

        String body = mockMvc.perform(get(BASE + "/" + definition.getId()).header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode fields = objectMapper.readTree(body).path("data").path("fields");

        assertEquals(3, fields.size());
        assertEquals("customerName", fields.get(0).path("fieldKey").asText());
        assertEquals("CUSTOMER.NAME", fields.get(0).path("sourceType").asText());
        assertEquals("客户名称", fields.get(0).path("sourceLabel").asText());
        assertTrue(fields.get(1).path("sourceType").isNull(), "手填字段 sourceType 应为空");
        assertEquals("SELECT", fields.get(2).path("fieldType").asText());
        assertNotNull(fields.get(2).path("options").asText());
    }

    @Test
    @DisplayName("字段：field_key 同模板重复 → 409（不同模板可同名）")
    void duplicateFieldKey() throws Exception {
        addField(definition.getId(), "{\"fieldKey\":\"dupKey\",\"label\":\"A\",\"fieldType\":\"TEXT\"}");
        mockMvc.perform(post(BASE + "/" + definition.getId() + "/fields")
                        .header("Authorization", adminToken).contentType(APPLICATION_JSON)
                        .content("{\"fieldKey\":\"dupKey\",\"label\":\"B\",\"fieldType\":\"TEXT\"}"))
                .andExpect(status().isConflict());

        FormDefinition other = definitionRepository.save(Objects.requireNonNull(FormDefinition.builder()
                .code("FORM-OTHER-" + System.nanoTime()).name("另一个模板").enabled(true).build()));
        addField(other.getId(), "{\"fieldKey\":\"dupKey\",\"label\":\"C\",\"fieldType\":\"TEXT\"}");
    }

    @Test
    @DisplayName("字段：SELECT 缺 options → 400；非 SELECT 传 options 会被忽略")
    void selectRequiresOptions() throws Exception {
        mockMvc.perform(post(BASE + "/" + definition.getId() + "/fields")
                        .header("Authorization", adminToken).contentType(APPLICATION_JSON)
                        .content("{\"fieldKey\":\"noOpt\",\"label\":\"无选项\",\"fieldType\":\"SELECT\"}"))
                .andExpect(status().isBadRequest());

        String id = addField(definition.getId(), "{\"fieldKey\":\"withOpt\",\"label\":\"有选项\","
                + "\"fieldType\":\"TEXT\",\"options\":\"[\\\"a\\\"]\"}");
        assertTrue(fieldRepository.findById(UUID.fromString(id)).orElseThrow().getOptions() == null,
                "非 SELECT 的 options 应被忽略");
    }

    @Test
    @DisplayName("字段：非法 field_type / source_type → 400（消息带可选值）")
    void invalidTypeAndSource() throws Exception {
        mockMvc.perform(post(BASE + "/" + definition.getId() + "/fields")
                        .header("Authorization", adminToken).contentType(APPLICATION_JSON)
                        .content("{\"fieldKey\":\"badType\",\"label\":\"X\",\"fieldType\":\"RICH\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("field_type")));

        mockMvc.perform(post(BASE + "/" + definition.getId() + "/fields")
                        .header("Authorization", adminToken).contentType(APPLICATION_JSON)
                        .content("{\"fieldKey\":\"badSource\",\"label\":\"X\",\"fieldType\":\"TEXT\","
                                + "\"sourceType\":\"CUSTOMER.UNKNOWN\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("source_type")));
    }

    @Test
    @DisplayName("字段：reorder 生效且 sort_order 连续 0..n-1")
    void reorderFields() throws Exception {
        String a = addField(definition.getId(), "{\"fieldKey\":\"a\",\"label\":\"A\",\"fieldType\":\"TEXT\"}");
        String b = addField(definition.getId(), "{\"fieldKey\":\"b\",\"label\":\"B\",\"fieldType\":\"TEXT\"}");
        String c = addField(definition.getId(), "{\"fieldKey\":\"c\",\"label\":\"C\",\"fieldType\":\"TEXT\"}");

        mockMvc.perform(put(BASE + "/" + definition.getId() + "/fields/reorder")
                        .header("Authorization", adminToken).contentType(APPLICATION_JSON)
                        .content("{\"fieldIds\":[\"" + c + "\",\"" + a + "\",\"" + b + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fields[0].fieldKey").value("c"))
                .andExpect(jsonPath("$.data.fields[1].fieldKey").value("a"))
                .andExpect(jsonPath("$.data.fields[2].fieldKey").value("b"));

        var fields = fieldRepository.findByDefinitionIdAndSubmissionIdIsNullAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(
                definition.getId());
        assertEquals(3, fields.size());
        assertEquals(0, fields.get(0).getSortOrder());
        assertEquals(1, fields.get(1).getSortOrder());
        assertEquals(2, fields.get(2).getSortOrder());
    }

    @Test
    @DisplayName("字段：reorder 的 fieldIds 覆盖不全 → 400")
    void reorderValidation() throws Exception {
        String a = addField(definition.getId(), "{\"fieldKey\":\"a\",\"label\":\"A\",\"fieldType\":\"TEXT\"}");
        addField(definition.getId(), "{\"fieldKey\":\"b\",\"label\":\"B\",\"fieldType\":\"TEXT\"}");

        mockMvc.perform(put(BASE + "/" + definition.getId() + "/fields/reorder")
                        .header("Authorization", adminToken).contentType(APPLICATION_JSON)
                        .content("{\"fieldIds\":[\"" + a + "\"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("字段：已有表单数据 → 禁止改 field_type / 删除（409），但可改 label")
    void fieldWithDataIsProtected() throws Exception {
        String fieldId = addField(definition.getId(), "{\"fieldKey\":\"locked\",\"label\":\"锁定字段\","
                + "\"fieldType\":\"TEXT\"}");
        UUID fieldUuid = UUID.fromString(fieldId);

        // 造一条表单数据（模拟已生成实例）
        FormSubmission submission = submissionRepository.saveAndFlush(Objects.requireNonNull(
                FormSubmission.builder().definitionId(definition.getId()).title("已生成").status("DRAFT")
                        .createdBy(adminUserId).build()));
        formValueRepository.saveAndFlush(Objects.requireNonNull(FormValue.builder()
                .submissionId(submission.getId()).fieldId(fieldUuid)
                .sourceValue("库快照").filledValue("人工值").build()));

        mockMvc.perform(put(BASE + "/" + definition.getId() + "/fields/" + fieldId)
                        .header("Authorization", adminToken).contentType(APPLICATION_JSON)
                        .content("{\"fieldType\":\"NUMBER\"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(delete(BASE + "/" + definition.getId() + "/fields/" + fieldId)
                        .header("Authorization", adminToken))
                .andExpect(status().isConflict());

        mockMvc.perform(put(BASE + "/" + definition.getId() + "/fields/" + fieldId)
                        .header("Authorization", adminToken).contentType(APPLICATION_JSON)
                        .content("{\"label\":\"改后的标签\",\"required\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.label").value("改后的标签"))
                .andExpect(jsonPath("$.data.required").value(true));
    }

    @Test
    @DisplayName("字段：无数据时可改类型与删除，删后详情不含")
    void fieldWithoutDataEditable() throws Exception {
        String fieldId = addField(definition.getId(), "{\"fieldKey\":\"free\",\"label\":\"自由字段\","
                + "\"fieldType\":\"TEXT\"}");

        mockMvc.perform(put(BASE + "/" + definition.getId() + "/fields/" + fieldId)
                        .header("Authorization", adminToken).contentType(APPLICATION_JSON)
                        .content("{\"fieldType\":\"NUMBER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fieldType").value("NUMBER"));

        mockMvc.perform(delete(BASE + "/" + definition.getId() + "/fields/" + fieldId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get(BASE + "/" + definition.getId()).header("Authorization", adminToken))
                .andExpect(jsonPath("$.data.fields.length()").value(0));
    }

    // ------------------------------------------------------------------ 三、权限与审计

    @Test
    @DisplayName("权限：非 ADMIN → 403；未登录 → 401")
    void permissions() throws Exception {
        mockMvc.perform(get(BASE).header("Authorization", leaderToken)).andExpect(status().isForbidden());
        mockMvc.perform(post(BASE).header("Authorization", engineerToken).contentType(APPLICATION_JSON)
                        .content("{\"code\":\"X\",\"name\":\"X\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(BASE + "/" + definition.getId()).header("Authorization", leaderToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE).contentType(APPLICATION_JSON).content("{\"code\":\"X\",\"name\":\"X\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("审计：每次写操作恰好 1 条（模板与字段分别归类）")
    void auditEachWriteOnce() throws Exception {
        JsonNode created = createDefinition("FORM-AUDIT-" + System.nanoTime(), "审计模板");
        String defId = created.path("id").asText();
        String fieldId = addField(UUID.fromString(defId),
                "{\"fieldKey\":\"a1\",\"label\":\"A1\",\"fieldType\":\"TEXT\"}");
        mockMvc.perform(put(BASE + "/" + defId + "/fields/" + fieldId).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"label\":\"A1-改\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(delete(BASE + "/" + defId + "/fields/" + fieldId).header("Authorization", adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(delete(BASE + "/" + defId).header("Authorization", adminToken))
                .andExpect(status().isOk());

        // 创建 1 + 更新 0（本用例没更模板） + 删除 1 = 2 条模板审计；字段 3 条（增/改/删）
        assertEquals(2, auditLogRepository.findByAction("UPDATE_FORM_DEFINITION").size());
        assertEquals(3, auditLogRepository.findByAction("UPDATE_FORM_FIELD").size());
    }

    @Test
    @DisplayName("V35 脚本守卫：四表 + 唯一索引 + 显式 collation + manual-ddl 等价")
    void migrationScriptGuards() throws Exception {
        String sql = Files.readString(Paths.get("src/main/resources/db/migration/V35__form_system_tables.sql"),
                StandardCharsets.UTF_8);
        for (String table : java.util.List.of("form_definition", "form_field", "form_submission", "form_value")) {
            assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS " + table), "缺表 " + table);
        }
        assertTrue(sql.contains("uk_form_definition_code"));
        assertTrue(sql.contains("uk_form_field_key"));
        assertTrue(sql.contains("uk_form_value_submission_field"));
        // 只数建表语句结尾（注释里也提到 collation，数关键字会多算）
        assertEquals(4, countOccurrences(sql, ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"),
                "四张表都要显式 DEFAULT CHARSET + collation");
        assertTrue(sql.contains("source_value") && sql.contains("filled_value") && sql.contains("override_value"),
                "三层值列必须都在");

        String manual = Files.readString(Paths.get("../deploy/manual-ddl.sql"), StandardCharsets.UTF_8);
        assertTrue(manual.contains("V35：自定义填表系统"), "manual-ddl 必须有等价段");
    }

    private int countOccurrences(String text, String token) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(token, index)) >= 0) {
            count++;
            index += token.length();
        }
        return count;
    }

    @Test
    @DisplayName("字段：跨模板隔离（用别的模板 id 访问字段 → 404）")
    void fieldIsolationAcrossDefinitions() throws Exception {
        String fieldId = addField(definition.getId(), "{\"fieldKey\":\"iso\",\"label\":\"隔离\",\"fieldType\":\"TEXT\"}");
        FormDefinition other = definitionRepository.save(Objects.requireNonNull(FormDefinition.builder()
                .code("FORM-ISO-" + System.nanoTime()).name("隔离模板").enabled(true).build()));

        mockMvc.perform(put(BASE + "/" + other.getId() + "/fields/" + fieldId)
                        .header("Authorization", adminToken).contentType(APPLICATION_JSON)
                        .content("{\"label\":\"越权改\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(BASE + "/" + other.getId() + "/fields/" + fieldId)
                        .header("Authorization", adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("模板列表：enabled 过滤生效")
    void listFiltersByEnabled() throws Exception {
        definitionRepository.save(Objects.requireNonNull(FormDefinition.builder()
                .code("FORM-DISABLED-" + System.nanoTime()).name("停用模板").enabled(false).build()));

        mockMvc.perform(get(BASE).header("Authorization", adminToken).param("enabled", "false").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.enabled==true)]",
                        org.hamcrest.Matchers.hasSize(0)));
        mockMvc.perform(get(BASE).header("Authorization", adminToken).param("enabled", "true").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[?(@.enabled==false)]",
                        org.hamcrest.Matchers.hasSize(0)));
    }
}
