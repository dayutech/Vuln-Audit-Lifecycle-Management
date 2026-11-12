package com.vulnaudit.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.Customer;
import com.vulnaudit.entity.FormDefinition;
import com.vulnaudit.entity.FormField;
import com.vulnaudit.entity.FormSubmission;
import com.vulnaudit.entity.FormValue;
import com.vulnaudit.entity.Project;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.CustomerRepository;
import com.vulnaudit.repository.FormDefinitionRepository;
import com.vulnaudit.repository.FormFieldRepository;
import com.vulnaudit.repository.FormSubmissionRepository;
import com.vulnaudit.repository.FormValueRepository;
import com.vulnaudit.repository.ProjectRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 表单实例生成（库快照）+ 指派 + 实例级字段 + 查询（t179，第 2/5 块）
 *
 * <p>最关键的一条：**快照与业务库脱钩** —— 改客户名后重新读详情，`sourceValue` 仍是旧值。
 */
@DisplayName("填表系统-表单实例测试")
public class FormSubmissionTest extends BaseTest {

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
    private ProjectRepository projectRepository;
    @Autowired
    private AssetRepository assetRepository;
    @Autowired
    private com.vulnaudit.repository.AuditLogRepository auditLogRepository;

    private FormDefinition definition;
    private FormField customerNameField;
    private FormField customerCodeField;
    private FormField remarkField;
    private Customer customer;

    @BeforeEach
    void setUpFixtures() {
        definition = definitionRepository.save(Objects.requireNonNull(FormDefinition.builder()
                .code("FORM-T179-" + System.nanoTime()).name("客户巡检表").enabled(true)
                .createdBy(adminUserId).build()));
        customerNameField = fieldFixture("customerName", "客户名称", "TEXT", "CUSTOMER.NAME", 0);
        customerCodeField = fieldFixture("customerCode", "客户编号", "TEXT", "CUSTOMER.CODE", 1);
        remarkField = fieldFixture("remark", "备注", "TEXTAREA", null, 2);

        customer = customerRepository.save(Objects.requireNonNull(Customer.builder()
                .code("CUS-T179-" + System.nanoTime()).name("快照测试客户").industry("金融")
                .status("active").createdBy(adminUserId).build()));
    }

    // ------------------------------------------------------------------ 工具

    private FormField fieldFixture(String key, String label, String type, String sourceType, int sortOrder) {
        return fieldRepository.save(Objects.requireNonNull(FormField.builder()
                .definitionId(definition.getId()).fieldKey(key).label(label).fieldType(type)
                .required(false).sortOrder(sortOrder).sourceType(sourceType).build()));
    }

    private JsonNode createSubmission(String token, String subjectType, UUID subjectId, String assignedTo)
            throws Exception {
        String body = "{\"definitionId\":\"" + definition.getId() + "\",\"subjectType\":\"" + subjectType
                + "\",\"subjectId\":\"" + subjectId + "\""
                + (assignedTo == null ? "" : ",\"assignedTo\":\"" + assignedTo + "\"") + "}";
        String response = mockMvc.perform(post(BASE).header("Authorization", token)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data");
    }

    private JsonNode detail(String token, String id) throws Exception {
        String body = mockMvc.perform(get(BASE + "/" + id).header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    private String sourceValueOf(JsonNode detail, String fieldKey) {
        for (JsonNode field : detail.path("fields")) {
            if (fieldKey.equals(field.path("fieldKey").asText())) {
                return field.path("sourceValue").isNull() ? null : field.path("sourceValue").asText();
            }
        }
        throw new AssertionError("字段不存在: " + fieldKey);
    }

    // ------------------------------------------------------------------ 一、快照

    @Test
    @DisplayName("生成：CUSTOMER.NAME / CUSTOMER.CODE 正确入快照，手填字段 sourceValue 为 null")
    void snapshotFromCustomer() throws Exception {
        JsonNode created = createSubmission(adminToken, "CUSTOMER", customer.getId(), null);
        assertEquals("DRAFT", created.path("status").asText());
        assertEquals(3, created.path("fieldCount").asInt());
        assertEquals(2, created.path("snapshotFilled").asInt(), "两个库字段应取到值");
        assertEquals(0, created.path("snapshotNull").asInt());

        JsonNode detail = detail(adminToken, created.path("id").asText());
        assertEquals("快照测试客户", sourceValueOf(detail, "customerName"));
        assertEquals(customer.getCode(), sourceValueOf(detail, "customerCode"));
        assertNull(sourceValueOf(detail, "remark"), "手填字段 sourceValue 必须是 null");
        assertEquals("快照测试客户", detail.path("subjectName").asText());
        assertEquals("CUSTOMER", detail.path("subjectType").asText());
    }

    @Test
    @DisplayName("关键：快照后修改业务数据，sourceValue 不变（修改不回写、也不回读）")
    void snapshotIsImmutableAfterBusinessChange() throws Exception {
        JsonNode created = createSubmission(adminToken, "CUSTOMER", customer.getId(), null);
        String id = created.path("id").asText();
        assertEquals("快照测试客户", sourceValueOf(detail(adminToken, id), "customerName"));

        // 业务侧改名：不该影响已生成表单
        customer.setName("改名后的客户");
        customerRepository.saveAndFlush(customer);

        JsonNode after = detail(adminToken, id);
        assertEquals("快照测试客户", sourceValueOf(after, "customerName"),
                "sourceValue 必须保持生成时的快照");

        FormValue value = formValueRepository.findBySubmissionId(UUID.fromString(id)).stream()
                .filter(item -> item.getFieldId().equals(customerNameField.getId())).findFirst().orElseThrow();
        assertEquals("快照测试客户", value.getSourceValue());
        assertNull(value.getOverrideValue(), "生成时不应有覆盖值");
    }

    @Test
    @DisplayName("生成：PROJECT.CODE 与 ASSET.CODE 快照各自正确（模板字段换成对应来源）")
    void snapshotFromProjectAndAsset() throws Exception {
        // 项目来源的模板
        FormDefinition projectDefinition = definitionRepository.save(Objects.requireNonNull(
                FormDefinition.builder().code("FORM-PRJ-" + System.nanoTime()).name("项目表")
                        .enabled(true).createdBy(adminUserId).build()));
        fieldRepository.save(Objects.requireNonNull(FormField.builder()
                .definitionId(projectDefinition.getId()).fieldKey("projectCode").label("项目编号")
                .fieldType("TEXT").sortOrder(0).sourceType("PROJECT.CODE").build()));

        Project project = projectRepository.saveAndFlush(Objects.requireNonNull(Project.builder()
                .code("PRJ-T179-" + System.nanoTime()).name("快照项目").customerId(customer.getId())
                .type("PENTEST").status("ACTIVE").managerId(adminUserId).createdBy(adminUserId).build()));

        String body = "{\"definitionId\":\"" + projectDefinition.getId()
                + "\",\"subjectType\":\"PROJECT\",\"subjectId\":\"" + project.getId() + "\"}";
        String response = mockMvc.perform(post(BASE).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(response).path("data").path("id").asText();

        JsonNode detail = detail(adminToken, id);
        assertEquals(project.getCode(), sourceValueOf(detail, "projectCode"));
        assertEquals("快照项目", detail.path("subjectName").asText());

        // 资产来源
        Asset asset = assetRepository.saveAndFlush(Objects.requireNonNull(Asset.builder()
                .name("快照资产").code("AST-T179-" + System.nanoTime()).targetType("WEB").targetSource("manual")
                .priority("P1").status("active").ownerId(adminUserId).build()));
        FormDefinition assetDefinition = definitionRepository.save(Objects.requireNonNull(
                FormDefinition.builder().code("FORM-AST-" + System.nanoTime()).name("资产表")
                        .enabled(true).createdBy(adminUserId).build()));
        fieldRepository.save(Objects.requireNonNull(FormField.builder()
                .definitionId(assetDefinition.getId()).fieldKey("assetCode").label("资产编号")
                .fieldType("TEXT").sortOrder(0).sourceType("ASSET.CODE").build()));
        String assetBody = "{\"definitionId\":\"" + assetDefinition.getId()
                + "\",\"subjectType\":\"ASSET\",\"subjectId\":\"" + asset.getId() + "\"}";
        String assetResponse = mockMvc.perform(post(BASE).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content(assetBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode assetDetail = detail(adminToken, objectMapper.readTree(assetResponse).path("data").path("id").asText());
        assertEquals(asset.getCode(), sourceValueOf(assetDetail, "assetCode"));
    }

    @Test
    @DisplayName("生成：库字段为空值 → sourceValue=null，snapshotNull 计数，不阻塞生成")
    void snapshotNullWhenBusinessFieldEmpty() throws Exception {
        FormDefinition emptyDefinition = definitionRepository.save(Objects.requireNonNull(
                FormDefinition.builder().code("FORM-EMPTY-" + System.nanoTime()).name("空值表")
                        .enabled(true).createdBy(adminUserId).build()));
        fieldRepository.save(Objects.requireNonNull(FormField.builder()
                .definitionId(emptyDefinition.getId()).fieldKey("industry").label("行业")
                .fieldType("TEXT").sortOrder(0).sourceType("CUSTOMER.INDUSTRY").build()));
        Customer noIndustry = customerRepository.saveAndFlush(Objects.requireNonNull(Customer.builder()
                .code("CUS-NOIND-" + System.nanoTime()).name("无行业客户").status("active")
                .createdBy(adminUserId).build()));

        String body = "{\"definitionId\":\"" + emptyDefinition.getId()
                + "\",\"subjectType\":\"CUSTOMER\",\"subjectId\":\"" + noIndustry.getId() + "\"}";
        String response = mockMvc.perform(post(BASE).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode created = objectMapper.readTree(response).path("data");
        assertEquals(0, created.path("snapshotFilled").asInt());
        assertEquals(1, created.path("snapshotNull").asInt());
    }

    @Test
    @DisplayName("生成：模板停用 → 400；模板不存在/主体不存在 → 404；subjectType 非法 → 400")
    void createValidation() throws Exception {
        FormDefinition disabled = definitionRepository.save(Objects.requireNonNull(FormDefinition.builder()
                .code("FORM-OFF-" + System.nanoTime()).name("停用表").enabled(false).build()));
        fieldRepository.save(Objects.requireNonNull(FormField.builder()
                .definitionId(disabled.getId()).fieldKey("f").label("F").fieldType("TEXT").build()));
        mockMvc.perform(post(BASE).header("Authorization", adminToken).contentType(APPLICATION_JSON)
                        .content("{\"definitionId\":\"" + disabled.getId()
                                + "\",\"subjectType\":\"CUSTOMER\",\"subjectId\":\"" + customer.getId() + "\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(BASE).header("Authorization", adminToken).contentType(APPLICATION_JSON)
                        .content("{\"definitionId\":\"" + UUID.randomUUID()
                                + "\",\"subjectType\":\"CUSTOMER\",\"subjectId\":\"" + customer.getId() + "\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(BASE).header("Authorization", adminToken).contentType(APPLICATION_JSON)
                        .content("{\"definitionId\":\"" + definition.getId()
                                + "\",\"subjectType\":\"CUSTOMER\",\"subjectId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(BASE).header("Authorization", adminToken).contentType(APPLICATION_JSON)
                        .content("{\"definitionId\":\"" + definition.getId()
                                + "\",\"subjectType\":\"INVOICE\",\"subjectId\":\"" + customer.getId() + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("生成：对不可见资产 → 403（visibility 不绕过）")
    void createOnInvisibleAssetForbidden() throws Exception {
        Asset othersAsset = assetRepository.saveAndFlush(Objects.requireNonNull(Asset.builder()
                .name("别人的资产").code("AST-INVIS-" + System.nanoTime()).targetType("WEB").targetSource("manual")
                .priority("P2").status("active").ownerId(leaderUserId).build()));
        FormDefinition assetDefinition = definitionRepository.save(Objects.requireNonNull(
                FormDefinition.builder().code("FORM-INVIS-" + System.nanoTime()).name("资产表")
                        .enabled(true).createdBy(adminUserId).build()));
        fieldRepository.save(Objects.requireNonNull(FormField.builder()
                .definitionId(assetDefinition.getId()).fieldKey("assetName").label("资产名")
                .fieldType("TEXT").sortOrder(0).sourceType("ASSET.NAME").build()));

        // engineer 看不到 leader 独占团队的资产
        mockMvc.perform(post(BASE).header("Authorization", engineerToken).contentType(APPLICATION_JSON)
                        .content("{\"definitionId\":\"" + assetDefinition.getId()
                                + "\",\"subjectType\":\"ASSET\",\"subjectId\":\"" + othersAsset.getId() + "\"}"))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------ 二、指派

    @Test
    @DisplayName("指派：发起人指派 → ASSIGNED + assignedAt 非空 + 审计 1 条")
    void assignByCreator() throws Exception {
        JsonNode created = createSubmission(adminToken, "CUSTOMER", customer.getId(), null);
        String id = created.path("id").asText();
        assertEquals("DRAFT", created.path("status").asText());

        mockMvc.perform(post(BASE + "/" + id + "/assign").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"assigneeId\":\"" + engineerUserId + "\",\"note\":\"请本周填完\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.data.assignedTo").value(engineerUserId.toString()))
                .andExpect(jsonPath("$.data.note").value("请本周填完"));

        FormSubmission submission = submissionRepository.findById(UUID.fromString(id)).orElseThrow();
        assertNotNull(submission.getAssignedAt());
        assertEquals(engineerUserId, submission.getAssignedTo());
        assertEquals(1, auditLogRepository.findByAction("ASSIGN_FORM_SUBMISSION").size());
    }

    @Test
    @DisplayName("指派：生成时直接带 assignedTo → 初始就是 ASSIGNED")
    void createWithAssignee() throws Exception {
        JsonNode created = createSubmission(adminToken, "CUSTOMER", customer.getId(), engineerUserId.toString());
        assertEquals("ASSIGNED", created.path("status").asText());
        JsonNode detail = detail(adminToken, created.path("id").asText());
        assertNotNull(detail.path("assignedAt"));
    }

    @Test
    @DisplayName("指派：非发起人 → 403；已提交 → 409；被指派人不存在 → 404")
    void assignRules() throws Exception {
        JsonNode created = createSubmission(adminToken, "CUSTOMER", customer.getId(), null);
        String id = created.path("id").asText();

        mockMvc.perform(post(BASE + "/" + id + "/assign").header("Authorization", leaderToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"assigneeId\":\"" + engineerUserId + "\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(BASE + "/" + id + "/assign").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"assigneeId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isNotFound());

        FormSubmission submission = submissionRepository.findById(UUID.fromString(id)).orElseThrow();
        submission.setStatus("SUBMITTED");
        submissionRepository.saveAndFlush(submission);
        mockMvc.perform(post(BASE + "/" + id + "/assign").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"assigneeId\":\"" + engineerUserId + "\"}"))
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------ 三、实例级字段

    @Test
    @DisplayName("加字段：成功且 sort_order 接在模板字段之后，field_key 为 instance_N，sourceType 为空")
    void addInstanceField() throws Exception {
        JsonNode created = createSubmission(adminToken, "CUSTOMER", customer.getId(), null);
        String id = created.path("id").asText();

        mockMvc.perform(post(BASE + "/" + id + "/fields").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"label\":\"现场照片说明\",\"fieldType\":\"TEXTAREA\",\"required\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.fieldKey").value("instance_1"))
                .andExpect(jsonPath("$.data.sortOrder").value(3))
                .andExpect(jsonPath("$.data.instanceField").value(true))
                .andExpect(jsonPath("$.data.sourceType").value(org.hamcrest.Matchers.nullValue()));

        mockMvc.perform(post(BASE + "/" + id + "/fields").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"label\":\"等级\",\"fieldType\":\"SELECT\",\"options\":\"[\\\"高\\\",\\\"低\\\"]\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.sortOrder").value(4));

        // 模板侧不受污染
        mockMvc.perform(get("/api/v1/admin/form-definitions/" + definition.getId()).header("Authorization", adminToken))
                .andExpect(jsonPath("$.data.fields.length()").value(3));
    }

    @Test
    @DisplayName("加字段：库字段类型无法通过（接口不接受 sourceType）+ SELECT 缺 options → 400 + 非发起人 403")
    void addInstanceFieldRules() throws Exception {
        JsonNode created = createSubmission(adminToken, "CUSTOMER", customer.getId(), null);
        String id = created.path("id").asText();

        // 接口层面没有 sourceType 字段，尝试加库字段只能靠非法类型表达意图 → 400
        mockMvc.perform(post(BASE + "/" + id + "/fields").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"label\":\"客户名\",\"fieldType\":\"CUSTOMER.NAME\",\"sourceType\":\"CUSTOMER.NAME\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(BASE + "/" + id + "/fields").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"label\":\"等级\",\"fieldType\":\"SELECT\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(BASE + "/" + id + "/fields").header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"label\":\"X\",\"fieldType\":\"TEXT\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("删字段：删他人（非发起人）字段 → 403；已填写 → 409；模板字段 → 404；正常删除成功")
    void deleteInstanceFieldRules() throws Exception {
        JsonNode created = createSubmission(adminToken, "CUSTOMER", customer.getId(), null);
        String id = created.path("id").asText();
        String fieldId = objectMapper.readTree(mockMvc.perform(post(BASE + "/" + id + "/fields")
                        .header("Authorization", adminToken).contentType(APPLICATION_JSON)
                        .content("{\"label\":\"临时字段\",\"fieldType\":\"TEXT\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).path("data").path("fieldId").asText();

        mockMvc.perform(delete(BASE + "/" + id + "/fields/" + fieldId).header("Authorization", engineerToken))
                .andExpect(status().isForbidden());

        // 模板字段不能经实例接口删
        mockMvc.perform(delete(BASE + "/" + id + "/fields/" + remarkField.getId())
                        .header("Authorization", adminToken))
                .andExpect(status().isNotFound());

        // 已填写 → 409
        FormValue value = formValueRepository.findBySubmissionId(UUID.fromString(id)).stream()
                .filter(item -> item.getFieldId().equals(UUID.fromString(fieldId))).findFirst().orElseThrow();
        value.setFilledValue("已填内容");
        formValueRepository.saveAndFlush(value);
        mockMvc.perform(delete(BASE + "/" + id + "/fields/" + fieldId).header("Authorization", adminToken))
                .andExpect(status().isConflict());

        // 清空后可删
        value.setFilledValue(null);
        formValueRepository.saveAndFlush(value);
        mockMvc.perform(delete(BASE + "/" + id + "/fields/" + fieldId).header("Authorization", adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get(BASE + "/" + id).header("Authorization", adminToken))
                .andExpect(jsonPath("$.data.fieldCount").value(3));
    }

    @Test
    @DisplayName("加字段：已提交 → 409")
    void addFieldAfterSubmitted() throws Exception {
        JsonNode created = createSubmission(adminToken, "CUSTOMER", customer.getId(), null);
        String id = created.path("id").asText();
        FormSubmission submission = submissionRepository.findById(UUID.fromString(id)).orElseThrow();
        submission.setStatus("SUBMITTED");
        submissionRepository.saveAndFlush(submission);

        mockMvc.perform(post(BASE + "/" + id + "/fields").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"label\":\"X\",\"fieldType\":\"TEXT\"}"))
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------ 四、查询与权限

    @Test
    @DisplayName("mine：只含我发起的，含进度 N/M 与指派对象；status 过滤生效")
    void mineOnlyOwn() throws Exception {
        createSubmission(adminToken, "CUSTOMER", customer.getId(), engineerUserId.toString());
        // 别人的表单
        mockMvc.perform(post(BASE).header("Authorization", leaderToken).contentType(APPLICATION_JSON)
                        .content("{\"definitionId\":\"" + definition.getId()
                                + "\",\"subjectType\":\"CUSTOMER\",\"subjectId\":\"" + customer.getId() + "\"}"))
                .andExpect(status().isCreated());

        String body = mockMvc.perform(get(BASE + "/mine").header("Authorization", adminToken)
                        .param("status", "all").param("size", "50"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode content = objectMapper.readTree(body).path("data").path("content");
        assertEquals(1, content.size(), "只应看到自己发起的 1 条");
        assertEquals("ASSIGNED", content.get(0).path("status").asText());
        assertEquals(3, content.get(0).path("fieldCount").asInt());
        assertEquals(0, content.get(0).path("filledCount").asInt());
        assertNotNull(content.get(0).path("assignedToName").asText());

        // status 过滤（ASSIGNED 有、SUBMITTED 无）
        assertEquals(1, objectMapper.readTree(mockMvc.perform(get(BASE + "/mine")
                        .header("Authorization", adminToken).param("status", "ASSIGNED"))
                .andReturn().getResponse().getContentAsString()).path("data").path("totalElements").asInt());
        assertEquals(0, objectMapper.readTree(mockMvc.perform(get(BASE + "/mine")
                        .header("Authorization", adminToken).param("status", "SUBMITTED"))
                .andReturn().getResponse().getContentAsString()).path("data").path("totalElements").asInt());
        mockMvc.perform(get(BASE + "/mine").header("Authorization", adminToken).param("status", "BOGUS"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("详情：他人 → 404（不泄露存在性）；被指派人/ADMIN 可读")
    void detailPermission() throws Exception {
        JsonNode created = createSubmission(adminToken, "CUSTOMER", customer.getId(), engineerUserId.toString());
        String id = created.path("id").asText();

        mockMvc.perform(get(BASE + "/" + id).header("Authorization", leaderToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(BASE + "/" + id).header("Authorization", engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.assignedTo").value(engineerUserId.toString()));
        mockMvc.perform(get(BASE + "/" + id).header("Authorization", adminToken))
                .andExpect(status().isOk());
        // 未登录 401
        mockMvc.perform(get(BASE + "/" + id)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(BASE).contentType(APPLICATION_JSON)
                        .content("{\"definitionId\":\"" + definition.getId()
                                + "\",\"subjectType\":\"CUSTOMER\",\"subjectId\":\"" + customer.getId() + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("详情：字段按 sort_order 返回且带三层值与来源标签")
    void detailFieldsShape() throws Exception {
        JsonNode created = createSubmission(adminToken, "CUSTOMER", customer.getId(), null);
        JsonNode detail = detail(adminToken, created.path("id").asText());
        JsonNode fields = detail.path("fields");
        assertEquals(3, fields.size());
        assertEquals("customerName", fields.get(0).path("fieldKey").asText());
        assertEquals("客户名称", fields.get(0).path("sourceLabel").asText());
        assertFalse(fields.get(0).path("instanceField").asBoolean());
        assertTrue(fields.get(2).path("sourceValue").isNull());
        assertEquals(0, detail.path("filledCount").asInt());
        assertEquals(3, detail.path("fieldCount").asInt());
    }

    @Test
    @DisplayName("V36 脚本守卫：form_field.submission_id + form_submission.subject_* + collation + manual-ddl")
    void migrationV36Guards() throws Exception {
        String sql = Files.readString(
                Paths.get("src/main/resources/db/migration/V36__form_field_submission_id.sql"),
                StandardCharsets.UTF_8);
        assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS submission_id"));
        assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS subject_type"));
        assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS subject_id"));
        assertTrue(sql.contains("idx_form_field_submission"));
        assertTrue(sql.contains("COLLATE utf8mb4_unicode_ci"));
        assertTrue(Files.readString(Paths.get("../deploy/manual-ddl.sql"), StandardCharsets.UTF_8)
                .contains("V36：实例级字段"), "manual-ddl 必须有等价段");
    }
}
