package com.vulnaudit.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.Customer;
import com.vulnaudit.entity.FormDefinition;
import com.vulnaudit.entity.FormField;
import com.vulnaudit.entity.FormSubmission;
import com.vulnaudit.entity.FormValue;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.CustomerRepository;
import com.vulnaudit.repository.FormDefinitionRepository;
import com.vulnaudit.repository.FormFieldRepository;
import com.vulnaudit.repository.FormSubmissionRepository;
import com.vulnaudit.repository.FormValueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 填表流程（t180，第 3/5 块）：填写 / 提交 / 修改覆盖 / 确认 / 退回 / 待我处理
 *
 * <p>最关键的两条：填了值以后 `sourceValue` 不变；设了覆盖值以后**业务库原值不变**（覆盖只影响导出）。
 */
@DisplayName("填表系统-填表流程测试")
public class FormFillFlowTest extends BaseTest {

    private static final String BASE = "/api/v1/forms/submissions";

    @Autowired
    private FormDefinitionRepository definitionRepository;
    @Autowired
    private FormFieldRepository fieldRepository;
    @Autowired
    private FormSubmissionRepository submissionRepository;
    @Autowired
    private FormValueRepository formValueRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private AssetRepository assetRepository;
    @Autowired
    private com.vulnaudit.repository.AuditLogRepository auditLogRepository;

    private FormDefinition definition;
    private FormField assetNameField;   // 库字段（ASSET.NAME）—— 覆盖测试用
    private FormField numberField;
    private FormField dateField;
    private FormField selectField;
    private FormField checkboxField;
    private FormField requiredTextField;
    private Asset asset;
    private Customer customer;

    @BeforeEach
    void setUpFixtures() {
        customer = customerRepository.save(Objects.requireNonNull(Customer.builder()
                .code("CUS-T180-" + System.nanoTime()).name("填表客户").status("active")
                .createdBy(adminUserId).build()));
        asset = assetRepository.saveAndFlush(Objects.requireNonNull(Asset.builder()
                .name("原始资产名").code("AST-T180-" + System.nanoTime()).targetType("WEB")
                .targetSource("manual").priority("P2").status("active").ownerId(adminUserId).build()));

        definition = definitionRepository.save(Objects.requireNonNull(FormDefinition.builder()
                .code("FORM-T180-" + System.nanoTime()).name("填表模板").enabled(true)
                .createdBy(adminUserId).build()));
        assetNameField = field("assetName", "资产名称", "TEXT", "ASSET.NAME", false, 0, null);
        numberField = field("score", "得分", "NUMBER", null, false, 1, null);
        dateField = field("checkDate", "检查日期", "DATE", null, false, 2, null);
        selectField = field("level", "等级", "SELECT", null, false, 3, "[\"高\",\"中\",\"低\"]");
        checkboxField = field("passed", "是否通过", "CHECKBOX", null, false, 4, null);
        requiredTextField = field("conclusion", "结论", "TEXT", null, true, 5, null);
    }

    private FormField field(String key, String label, String type, String sourceType, boolean required,
                            int sortOrder, String options) {
        return fieldRepository.save(Objects.requireNonNull(FormField.builder()
                .definitionId(definition.getId()).fieldKey(key).label(label).fieldType(type)
                .required(required).sortOrder(sortOrder).sourceType(sourceType).options(options).build()));
    }

    private JsonNode createSubmission(String token, String assignedTo) throws Exception {
        String body = "{\"definitionId\":\"" + definition.getId()
                + "\",\"subjectType\":\"ASSET\",\"subjectId\":\"" + asset.getId() + "\""
                + (assignedTo == null ? "" : ",\"assignedTo\":\"" + assignedTo + "\"") + "}";
        String response = mockMvc.perform(post(BASE).header("Authorization", token)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    private void fill(String token, String id, String json) throws Exception {
        mockMvc.perform(put(BASE + "/" + id + "/values").header("Authorization", token)
                        .contentType(APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
    }

    private void submit(String token, String id) throws Exception {
        mockMvc.perform(post(BASE + "/" + id + "/submit").header("Authorization", token))
                .andExpect(status().isOk());
    }

    private JsonNode detail(String token, String id) throws Exception {
        String body = mockMvc.perform(get(BASE + "/" + id).header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    private JsonNode fieldNode(JsonNode detail, String fieldKey) {
        for (JsonNode node : detail.path("fields")) {
            if (fieldKey.equals(node.path("fieldKey").asText())) {
                return node;
            }
        }
        throw new AssertionError("字段不存在: " + fieldKey);
    }

    private String fillAll(String id) {
        return "{\"values\":[{\"fieldId\":\"" + numberField.getId() + "\",\"filledValue\":\"88\"},"
                + "{\"fieldId\":\"" + dateField.getId() + "\",\"filledValue\":\"2026-11-12\"},"
                + "{\"fieldId\":\"" + selectField.getId() + "\",\"filledValue\":\"高\"},"
                + "{\"fieldId\":\"" + checkboxField.getId() + "\",\"filledValue\":\"true\"},"
                + "{\"fieldId\":\"" + requiredTextField.getId() + "\",\"filledValue\":\"符合要求\"}]}";
    }

    // ------------------------------------------------------------------ 一、填写

    @Test
    @DisplayName("填写：非被指派人（含 ADMIN）→ 403；被指派人填写成功且 filledValue 落库")
    void fillPermissionAndPersist() throws Exception {
        JsonNode created = createSubmission(adminToken, engineerUserId.toString());
        String id = created.path("id").asText();

        mockMvc.perform(put(BASE + "/" + id + "/values").header("Authorization", leaderToken)
                        .contentType(APPLICATION_JSON).content(fillAll(id)))
                .andExpect(status().isForbidden());
        // ADMIN 也不代填
        mockMvc.perform(put(BASE + "/" + id + "/values").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content(fillAll(id)))
                .andExpect(status().isForbidden());

        fill(engineerToken, id, fillAll(id));
        FormValue numberValue = valueOf(id, numberField.getId());
        assertEquals("88", numberValue.getFilledValue());
        JsonNode detail = detail(engineerToken, id);
        assertEquals("88", fieldNode(detail, "score").path("filledValue").asText());
        assertEquals("88", fieldNode(detail, "score").path("effectiveValue").asText());
    }

    @Test
    @DisplayName("关键：填写后 sourceValue 不变，请求里带 sourceValue 被忽略")
    void fillDoesNotTouchSnapshot() throws Exception {
        JsonNode created = createSubmission(adminToken, engineerUserId.toString());
        String id = created.path("id").asText();
        assertEquals("原始资产名", fieldNode(detail(engineerToken, id), "assetName").path("sourceValue").asText());

        // 请求里偷偷带 sourceValue（未知字段被 Jackson 忽略）→ 快照必须保持原样
        fill(engineerToken, id, "{\"values\":[{\"fieldId\":\"" + assetNameField.getId()
                + "\",\"filledValue\":\"人工填的资产名\",\"sourceValue\":\"篡改快照\"}]}");

        JsonNode detail = detail(engineerToken, id);
        assertEquals("原始资产名", fieldNode(detail, "assetName").path("sourceValue").asText(),
                "sourceValue 是快照，任何填写都不能改它");
        assertEquals("人工填的资产名", fieldNode(detail, "assetName").path("filledValue").asText());
        assertEquals("原始资产名", assetRepository.findById(asset.getId()).orElseThrow().getName(),
                "业务库不应被填写影响");
    }

    @Test
    @DisplayName("填写：字段不属于该实例 → 400")
    void fillRejectsForeignField() throws Exception {
        JsonNode created = createSubmission(adminToken, engineerUserId.toString());
        UUID foreignField = fieldRepository.save(Objects.requireNonNull(FormField.builder()
                .definitionId(definitionRepository.saveAndFlush(Objects.requireNonNull(
                        FormDefinition.builder().code("FORM-OTHER-" + System.nanoTime()).name("别的模板")
                                .enabled(true).build())).getId())
                .fieldKey("foreign").label("外来字段").fieldType("TEXT").build())).getId();

        mockMvc.perform(put(BASE + "/" + created.path("id").asText() + "/values")
                        .header("Authorization", engineerToken).contentType(APPLICATION_JSON)
                        .content("{\"values\":[{\"fieldId\":\"" + foreignField + "\",\"filledValue\":\"x\"}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("类型校验：NUMBER / DATE / SELECT / CHECKBOX 各一例 → 400")
    void typeValidation() throws Exception {
        JsonNode created = createSubmission(adminToken, engineerUserId.toString());
        String id = created.path("id").asText();

        mockMvc.perform(put(BASE + "/" + id + "/values").header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON).content("{\"values\":[{\"fieldId\":\"" + numberField.getId()
                                + "\",\"filledValue\":\"八十八\"}]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put(BASE + "/" + id + "/values").header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON).content("{\"values\":[{\"fieldId\":\"" + dateField.getId()
                                + "\",\"filledValue\":\"2026/11/12\"}]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put(BASE + "/" + id + "/values").header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON).content("{\"values\":[{\"fieldId\":\"" + selectField.getId()
                                + "\",\"filledValue\":\"超高\"}]}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put(BASE + "/" + id + "/values").header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON).content("{\"values\":[{\"fieldId\":\"" + checkboxField.getId()
                                + "\",\"filledValue\":\"yes\"}]}"))
                .andExpect(status().isBadRequest());

        // 合法值可通过
        fill(engineerToken, id, "{\"values\":[{\"fieldId\":\"" + numberField.getId() + "\",\"filledValue\":\"88\"},"
                + "{\"fieldId\":\"" + dateField.getId() + "\",\"filledValue\":\"2026-11-12\"},"
                + "{\"fieldId\":\"" + selectField.getId() + "\",\"filledValue\":\"中\"},"
                + "{\"fieldId\":\"" + checkboxField.getId() + "\",\"filledValue\":\"false\"}]}");
    }

    @Test
    @DisplayName("必填：保存草稿不校验；提交时缺必填 → 400 并列出字段 label")
    void requiredCheckedOnlyOnSubmit() throws Exception {
        JsonNode created = createSubmission(adminToken, engineerUserId.toString());
        String id = created.path("id").asText();

        // 草稿保存：只填非必填字段，不报错
        fill(engineerToken, id, "{\"values\":[{\"fieldId\":\"" + numberField.getId()
                + "\",\"filledValue\":\"70\"}]}");

        mockMvc.perform(post(BASE + "/" + id + "/submit").header("Authorization", engineerToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("结论")));

        // 补上必填后可提交
        fill(engineerToken, id, "{\"values\":[{\"fieldId\":\"" + requiredTextField.getId()
                + "\",\"filledValue\":\"符合要求\"}]}");
        submit(engineerToken, id);
        assertEquals("SUBMITTED", detail(engineerToken, id).path("status").asText());
    }

    @Test
    @DisplayName("提交：重复提交 → 409；未指派时发起人可填可提交")
    void submitRulesAndSelfFill() throws Exception {
        JsonNode created = createSubmission(adminToken, engineerUserId.toString());
        String id = created.path("id").asText();
        fill(engineerToken, id, fillAll(id));
        submit(engineerToken, id);
        mockMvc.perform(post(BASE + "/" + id + "/submit").header("Authorization", engineerToken))
                .andExpect(status().isConflict());

        // 未指派（DRAFT）：发起人自填自交
        JsonNode draft = createSubmission(adminToken, null);
        String draftId = draft.path("id").asText();
        fill(adminToken, draftId, fillAll(draftId));
        submit(adminToken, draftId);
        assertEquals("SUBMITTED", detail(adminToken, draftId).path("status").asText());
    }

    @Test
    @DisplayName("填写：已提交后再填 → 409（需确认人退回）")
    void fillAfterSubmitConflict() throws Exception {
        JsonNode created = createSubmission(adminToken, engineerUserId.toString());
        String id = created.path("id").asText();
        fill(engineerToken, id, fillAll(id));
        submit(engineerToken, id);
        mockMvc.perform(put(BASE + "/" + id + "/values").header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON).content("{\"values\":[{\"fieldId\":\""
                                + numberField.getId() + "\",\"filledValue\":\"99\"}]}"))
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------ 二、修改覆盖

    @Test
    @DisplayName("覆盖：SUBMITTED 前设置 → 409；SUBMITTED 后发起人设置成功且 filledValue 不变")
    void overrideAfterSubmitOnly() throws Exception {
        JsonNode created = createSubmission(adminToken, engineerUserId.toString());
        String id = created.path("id").asText();
        fill(engineerToken, id, fillAll(id));

        mockMvc.perform(put(BASE + "/" + id + "/overrides").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"values\":[{\"fieldId\":\"" + numberField.getId() + "\",\"overrideValue\":\"99\"}]}"))
                .andExpect(status().isConflict());

        submit(engineerToken, id);
        mockMvc.perform(put(BASE + "/" + id + "/overrides").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"values\":[{\"fieldId\":\"" + numberField.getId() + "\",\"overrideValue\":\"99\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.effectiveValues.score").value("99"));

        FormValue value = valueOf(id, numberField.getId());
        assertEquals("99", value.getOverrideValue());
        assertEquals("88", value.getFilledValue(), "覆盖不得修改填写值");
        JsonNode detail = detail(adminToken, id);
        assertEquals("88", fieldNode(detail, "score").path("filledValue").asText());
        assertEquals("99", fieldNode(detail, "score").path("effectiveValue").asText(),
                "生效值应取覆盖值");
    }

    @Test
    @DisplayName("覆盖：overrideValue=null 清除后 effectiveValue 回落到 filledValue")
    void overrideClearedFallsBack() throws Exception {
        JsonNode created = createSubmission(adminToken, engineerUserId.toString());
        String id = created.path("id").asText();
        fill(engineerToken, id, fillAll(id));
        submit(engineerToken, id);
        mockMvc.perform(put(BASE + "/" + id + "/overrides").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"values\":[{\"fieldId\":\"" + numberField.getId() + "\",\"overrideValue\":\"99\"}]}"))
                .andExpect(status().isOk());
        mockMvc.perform(put(BASE + "/" + id + "/overrides").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"values\":[{\"fieldId\":\"" + numberField.getId() + "\",\"overrideValue\":null}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.effectiveValues.score").value("88"));

        assertNull(valueOf(id, numberField.getId()).getOverrideValue());
        assertEquals("88", fieldNode(detail(adminToken, id), "score").path("effectiveValue").asText());
    }

    @Test
    @DisplayName("关键：覆盖库字段（ASSET.NAME）后，业务库资产名不变")
    void overrideDoesNotWriteBusinessTable() throws Exception {
        JsonNode created = createSubmission(adminToken, engineerUserId.toString());
        String id = created.path("id").asText();
        fill(engineerToken, id, fillAll(id));
        submit(engineerToken, id);

        mockMvc.perform(put(BASE + "/" + id + "/overrides").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"values\":[{\"fieldId\":\"" + assetNameField.getId()
                                + "\",\"overrideValue\":\"覆盖后的资产名\"}]}"))
                .andExpect(status().isOk());

        JsonNode detail = detail(adminToken, id);
        assertEquals("原始资产名", fieldNode(detail, "assetName").path("sourceValue").asText());
        assertEquals("覆盖后的资产名", fieldNode(detail, "assetName").path("effectiveValue").asText());
        assertEquals("原始资产名", assetRepository.findById(asset.getId()).orElseThrow().getName(),
                "覆盖只影响导出，绝不回写资产表");
    }

    @Test
    @DisplayName("覆盖：被指派人（非发起人/确认人/ADMIN）→ 403")
    void overrideForbiddenForAssignee() throws Exception {
        JsonNode created = createSubmission(adminToken, engineerUserId.toString());
        String id = created.path("id").asText();
        fill(engineerToken, id, fillAll(id));
        submit(engineerToken, id);

        mockMvc.perform(put(BASE + "/" + id + "/overrides").header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"values\":[{\"fieldId\":\"" + numberField.getId() + "\",\"overrideValue\":\"1\"}]}"))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ 三、确认 / 退回

    @Test
    @DisplayName("确认：非确认人 403；状态不符 409；设置确认人后确认成功 → CONFIRMED")
    void confirmFlow() throws Exception {
        JsonNode created = createSubmission(adminToken, engineerUserId.toString());
        String id = created.path("id").asText();

        // 设置确认人
        mockMvc.perform(post(BASE + "/" + id + "/confirmers").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"confirmerId\":\"" + leaderUserId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.confirmedBy").value(leaderUserId.toString()));

        // 未提交时确认 → 409
        mockMvc.perform(post(BASE + "/" + id + "/confirm").header("Authorization", leaderToken))
                .andExpect(status().isConflict());

        fill(engineerToken, id, fillAll(id));
        submit(engineerToken, id);
        // 非确认人（工程师）→ 403
        mockMvc.perform(post(BASE + "/" + id + "/confirm").header("Authorization", engineerToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/" + id + "/confirm").header("Authorization", leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));

        FormSubmission submission = submissionRepository.findById(UUID.fromString(id)).orElseThrow();
        assertEquals("CONFIRMED", submission.getStatus());
        assertNotNull(submission.getConfirmedAt());
    }

    @Test
    @DisplayName("退回：note 必填 → 400；退回后 RETURNED，被指派人可再次编辑并重新提交")
    void returnFlow() throws Exception {
        JsonNode created = createSubmission(adminToken, engineerUserId.toString());
        String id = created.path("id").asText();
        mockMvc.perform(post(BASE + "/" + id + "/confirmers").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"confirmerId\":\"" + leaderUserId + "\"}"))
                .andExpect(status().isOk());
        fill(engineerToken, id, fillAll(id));
        submit(engineerToken, id);

        mockMvc.perform(post(BASE + "/" + id + "/return").header("Authorization", leaderToken)
                        .contentType(APPLICATION_JSON).content("{\"note\":\"  \"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(BASE + "/" + id + "/return").header("Authorization", leaderToken)
                        .contentType(APPLICATION_JSON).content("{\"note\":\"结论太笼统，请补充证据\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RETURNED"))
                .andExpect(jsonPath("$.data.note").value("结论太笼统，请补充证据"));

        // 退回后可再编辑并重新提交
        fill(engineerToken, id, "{\"values\":[{\"fieldId\":\"" + requiredTextField.getId()
                + "\",\"filledValue\":\"补充了证据\"}]}");
        submit(engineerToken, id);
        assertEquals("SUBMITTED", detail(engineerToken, id).path("status").asText());
    }

    @Test
    @DisplayName("确认人：非发起人设置 → 403；已确认后不可改 → 409")
    void confirmerRules() throws Exception {
        JsonNode created = createSubmission(adminToken, engineerUserId.toString());
        String id = created.path("id").asText();
        mockMvc.perform(post(BASE + "/" + id + "/confirmers").header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON).content("{\"confirmerId\":\"" + leaderUserId + "\"}"))
                .andExpect(status().isForbidden());

        FormSubmission submission = submissionRepository.findById(UUID.fromString(id)).orElseThrow();
        submission.setStatus("CONFIRMED");
        submissionRepository.saveAndFlush(submission);
        mockMvc.perform(post(BASE + "/" + id + "/confirmers").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"confirmerId\":\"" + leaderUserId + "\"}"))
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------ 四、待我处理

    @Test
    @DisplayName("assigned：只含指派给自己的（含 RETURNED），含退回原因 note")
    void assignedListOnlyMine() throws Exception {
        JsonNode mine = createSubmission(adminToken, engineerUserId.toString());
        createSubmission(adminToken, leaderUserId.toString());

        fill(engineerToken, mine.path("id").asText(), fillAll(mine.path("id").asText()));
        submit(engineerToken, mine.path("id").asText());
        mockMvc.perform(post(BASE + "/" + mine.path("id").asText() + "/return").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"note\":\"请补充\"}"))
                .andExpect(status().isOk());

        String body = mockMvc.perform(get(BASE + "/assigned").header("Authorization", engineerToken)
                        .param("status", "all").param("size", "50"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode content = objectMapper.readTree(body).path("data").path("content");
        assertEquals(1, content.size(), "只应看到指派给自己的 1 条");
        assertEquals("RETURNED", content.get(0).path("status").asText());
        assertEquals("请补充", content.get(0).path("note").asText());
        assertTrue(content.get(0).path("fieldCount").asInt() >= 6);

        // 另一个人的 assigned 列表不含这条
        String other = mockMvc.perform(get(BASE + "/assigned").header("Authorization", leaderToken)
                        .param("status", "all"))
                .andReturn().getResponse().getContentAsString();
        assertEquals(1, objectMapper.readTree(other).path("data").path("totalElements").asInt());
    }

    @Test
    @DisplayName("审计：提交/覆盖/确认/退回各 1 条")
    void auditFourActions() throws Exception {
        JsonNode created = createSubmission(adminToken, engineerUserId.toString());
        String id = created.path("id").asText();
        mockMvc.perform(post(BASE + "/" + id + "/confirmers").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{\"confirmerId\":\"" + leaderUserId + "\"}"))
                .andExpect(status().isOk());
        fill(engineerToken, id, fillAll(id));
        submit(engineerToken, id);
        mockMvc.perform(put(BASE + "/" + id + "/overrides").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"values\":[{\"fieldId\":\"" + numberField.getId() + "\",\"overrideValue\":\"90\"}]}"))
                .andExpect(status().isOk());
        mockMvc.perform(post(BASE + "/" + id + "/return").header("Authorization", leaderToken)
                        .contentType(APPLICATION_JSON).content("{\"note\":\"退回一次\"}"))
                .andExpect(status().isOk());
        fill(engineerToken, id, "{\"values\":[{\"fieldId\":\"" + requiredTextField.getId()
                + "\",\"filledValue\":\"已补充\"}]}");
        submit(engineerToken, id);
        mockMvc.perform(post(BASE + "/" + id + "/confirm").header("Authorization", leaderToken))
                .andExpect(status().isOk());

        assertEquals(2, auditLogRepository.findByAction("SUBMIT_FORM_SUBMISSION").size(), "提交 2 次（退回后重交）");
        assertEquals(1, auditLogRepository.findByAction("OVERRIDE_FORM_VALUES").size());
        assertEquals(1, auditLogRepository.findByAction("RETURN_FORM_SUBMISSION").size());
        assertEquals(1, auditLogRepository.findByAction("CONFIRM_FORM_SUBMISSION").size());
    }

    @Test
    @DisplayName("未登录：填写/提交/覆盖/确认/退回/assigned → 401")
    void requiresLogin() throws Exception {
        mockMvc.perform(put(BASE + "/" + UUID.randomUUID() + "/values").contentType(APPLICATION_JSON)
                .content("{\"values\":[]}")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE + "/" + UUID.randomUUID() + "/submit")).andExpect(status().isUnauthorized());
        mockMvc.perform(put(BASE + "/" + UUID.randomUUID() + "/overrides").contentType(APPLICATION_JSON)
                .content("{\"values\":[]}")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE + "/" + UUID.randomUUID() + "/confirm")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE + "/" + UUID.randomUUID() + "/return").contentType(APPLICATION_JSON)
                .content("{\"note\":\"x\"}")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/assigned")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("effectiveValue 优先级：override ?? filled ?? source（无填无覆盖时回落到快照）")
    void effectiveValuePrecedence() throws Exception {
        JsonNode created = createSubmission(adminToken, engineerUserId.toString());
        String id = created.path("id").asText();

        // 只有快照：生效值 = 快照
        assertEquals("原始资产名", fieldNode(detail(engineerToken, id), "assetName").path("effectiveValue").asText());
        // 既没填也没快照的字段：生效值为 null（不是空串，避免前端显示混淆）
        assertTrue(fieldNode(detail(engineerToken, id), "score").path("effectiveValue").isNull(),
                "无填无覆盖无快照时 effectiveValue 应为 null");

        // 填写后：生效值 = 填写值（覆盖未设）
        fill(engineerToken, id, "{\"values\":[{\"fieldId\":\"" + assetNameField.getId()
                + "\",\"filledValue\":\"人工值\"}]}");
        assertEquals("人工值", fieldNode(detail(engineerToken, id), "assetName").path("effectiveValue").asText());

        // 设覆盖后：生效值 = 覆盖值（提交前先补齐必填字段）
        fill(engineerToken, id, "{\"values\":[{\"fieldId\":\"" + requiredTextField.getId()
                + "\",\"filledValue\":\"符合要求\"}]}");
        submit(engineerToken, id);
        mockMvc.perform(put(BASE + "/" + id + "/overrides").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"values\":[{\"fieldId\":\"" + assetNameField.getId()
                                + "\",\"overrideValue\":\"覆盖值\"}]}"))
                .andExpect(status().isOk());
        assertEquals("覆盖值", fieldNode(detail(adminToken, id), "assetName").path("effectiveValue").asText());
    }

    private FormValue valueOf(String submissionId, UUID fieldId) {
        return formValueRepository.findBySubmissionId(UUID.fromString(submissionId)).stream()
                .filter(value -> value.getFieldId().equals(fieldId))
                .findFirst().orElseThrow(() -> new AssertionError("缺少表单值记录"));
    }
}
