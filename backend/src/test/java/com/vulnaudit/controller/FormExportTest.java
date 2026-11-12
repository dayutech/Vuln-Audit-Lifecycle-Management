package com.vulnaudit.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.Customer;
import com.vulnaudit.entity.FormDefinition;
import com.vulnaudit.entity.FormField;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.CustomerRepository;
import com.vulnaudit.repository.FormDefinitionRepository;
import com.vulnaudit.repository.FormFieldRepository;
import com.vulnaudit.repository.FormValueRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 填表系统导出（t181）：CSV + XLSX
 *
 * <p>上限用 `app.forms.export.max-rows=3` 覆盖，避免为了测 400 造 5001 条数据。
 */
@DisplayName("填表系统-导出测试")
@TestPropertySource(properties = "app.forms.export.max-rows=3")
public class FormExportTest extends BaseTest {

    private static final String BASE = "/api/v1/forms/submissions";

    @Autowired
    private FormDefinitionRepository definitionRepository;
    @Autowired
    private FormFieldRepository fieldRepository;
    @Autowired
    private FormValueRepository formValueRepository;
    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private AssetRepository assetRepository;
    @Autowired
    private com.vulnaudit.repository.AuditLogRepository auditLogRepository;

    private FormDefinition definition;
    private FormField customerNameField;
    private FormField numberField;
    private FormField remarkField;
    private Customer customer;
    private Asset asset;

    @BeforeEach
    void setUpFixtures() {
        customer = customerRepository.save(Objects.requireNonNull(Customer.builder()
                .code("CUS-T181-" + System.nanoTime()).name("导出客户").status("active")
                .createdBy(adminUserId).build()));
        asset = assetRepository.saveAndFlush(Objects.requireNonNull(Asset.builder()
                .name("导出资产").code("AST-T181-" + System.nanoTime()).targetType("WEB")
                .targetSource("manual").priority("P2").status("active").ownerId(adminUserId).build()));

        definition = definitionRepository.save(Objects.requireNonNull(FormDefinition.builder()
                .code("FORM-T181-" + System.nanoTime()).name("导出模板").enabled(true)
                .createdBy(adminUserId).build()));
        customerNameField = field("customerName", "客户名称", "TEXT", "CUSTOMER.NAME", 0, null);
        numberField = field("score", "得分", "NUMBER", null, 1, null);
        remarkField = field("remark", "备注", "TEXTAREA", null, 2, null);
    }

    private FormField field(String key, String label, String type, String sourceType, int sortOrder, String options) {
        return fieldRepository.save(Objects.requireNonNull(FormField.builder()
                .definitionId(definition.getId()).fieldKey(key).label(label).fieldType(type)
                .required(false).sortOrder(sortOrder).sourceType(sourceType).options(options).build()));
    }

    private String createSubmission(UUID definitionId, String token, String assignedTo) throws Exception {
        String body = "{\"definitionId\":\"" + definitionId + "\",\"subjectType\":\"CUSTOMER\",\"subjectId\":\""
                + customer.getId() + "\"" + (assignedTo == null ? "" : ",\"assignedTo\":\"" + assignedTo + "\"") + "}";
        String response = mockMvc.perform(post(BASE).header("Authorization", token)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("id").asText();
    }

    private byte[] exportCsv(String token, String query) throws Exception {
        return mockMvc.perform(get(BASE + "/export?format=csv" + query).header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }

    private List<List<String>> parseCsv(byte[] content) {
        String text = new String(content, StandardCharsets.UTF_8);
        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
        }
        List<List<String>> rows = new ArrayList<>();
        for (String line : text.split("\r\n")) {
            if (line.isEmpty()) {
                continue;
            }
            rows.add(splitCsvLine(line));
        }
        return rows;
    }

    private List<String> splitCsvLine(String line) {
        List<String> cells = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (quoted) {
                if (ch == '"') {
                    if (i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        current.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    current.append(ch);
                }
            } else if (ch == '"') {
                quoted = true;
            } else if (ch == ',') {
                cells.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        cells.add(current.toString());
        return cells;
    }

    // ------------------------------------------------------------------ 一、CSV

    @Test
    @DisplayName("CSV：首字节是 UTF-8 BOM，表头 = 11 固定列 + 动态列（按 sort_order）")
    void csvHasBomAndColumnOrder() throws Exception {
        createSubmission(definition.getId(), adminToken, engineerUserId.toString());
        byte[] content = exportCsv(adminToken, "");

        assertEquals((byte) 0xEF, content[0]);
        assertEquals((byte) 0xBB, content[1]);
        assertEquals((byte) 0xBF, content[2]);

        List<List<String>> rows = parseCsv(content);
        List<String> header = rows.get(0);
        assertEquals("表单ID", header.get(0));
        assertEquals("模板名称", header.get(1));
        assertEquals("主体类型", header.get(2));
        assertEquals("主体名称", header.get(3));
        assertEquals("状态", header.get(4));
        assertEquals("发起人", header.get(5));
        assertEquals("被指派人", header.get(6));
        assertEquals("确认人", header.get(7));
        assertEquals("创建时间", header.get(8));
        assertEquals("提交时间", header.get(9));
        assertEquals("确认时间", header.get(10));
        assertEquals("客户名称", header.get(11));
        assertEquals("得分", header.get(12));
        assertEquals("备注", header.get(13));
        assertEquals(1, rows.size() - 1, "1 条数据行");
    }

    @Test
    @DisplayName("CSV：effectiveValue 优先级（快照 → 填写 → 覆盖）")
    void csvUsesEffectiveValue() throws Exception {
        String id = createSubmission(definition.getId(), adminToken, engineerUserId.toString());
        // 快照：客户名称有值（source）；得分/备注无快照无填写
        List<List<String>> before = parseCsv(exportCsv(adminToken, ""));
        List<String> first = before.get(1);
        assertEquals("导出客户", first.get(11), "未填未覆盖 → 取快照");

        // 填写：得分 88
        mockMvc.perform(put(BASE + "/" + id + "/values").header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"values\":[{\"fieldId\":\"" + numberField.getId() + "\",\"filledValue\":\"88\"}]}"))
                .andExpect(status().isOk());
        List<String> afterFill = parseCsv(exportCsv(adminToken, "")).get(1);
        assertEquals("88", afterFill.get(12), "有填写 → 取填写值");

        // 提交后覆盖：得分 99
        mockMvc.perform(post(BASE + "/" + id + "/submit").header("Authorization", engineerToken))
                .andExpect(status().isOk());
        mockMvc.perform(put(BASE + "/" + id + "/overrides").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"values\":[{\"fieldId\":\"" + numberField.getId() + "\",\"overrideValue\":\"99\"}]}"))
                .andExpect(status().isOk());
        List<String> afterOverride = parseCsv(exportCsv(adminToken, "")).get(1);
        assertEquals("99", afterOverride.get(12), "有覆盖 → 取覆盖值");
    }

    @Test
    @DisplayName("CSV：覆盖值导出生效但业务库资产名不变")
    void csvOverrideDoesNotTouchBusinessTable() throws Exception {
        FormDefinition assetDefinition = definitionRepository.save(Objects.requireNonNull(
                FormDefinition.builder().code("FORM-EXP-AST-" + System.nanoTime()).name("资产导出表")
                        .enabled(true).createdBy(adminUserId).build()));
        FormField assetNameField = fieldRepository.save(Objects.requireNonNull(FormField.builder()
                .definitionId(assetDefinition.getId()).fieldKey("assetName").label("资产名称")
                .fieldType("TEXT").sortOrder(0).sourceType("ASSET.NAME").build()));
        String body = "{\"definitionId\":\"" + assetDefinition.getId()
                + "\",\"subjectType\":\"ASSET\",\"subjectId\":\"" + asset.getId() + "\",\"assignedTo\":\""
                + engineerUserId + "\"}";
        String created = mockMvc.perform(post(BASE).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(created).path("data").path("id").asText();
        mockMvc.perform(post(BASE + "/" + id + "/submit").header("Authorization", engineerToken))
                .andExpect(status().isOk());
        mockMvc.perform(put(BASE + "/" + id + "/overrides").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"values\":[{\"fieldId\":\"" + assetNameField.getId()
                                + "\",\"overrideValue\":\"导出里的资产名\"}]}"))
                .andExpect(status().isOk());

        List<List<String>> rows = parseCsv(exportCsv(adminToken, "&definitionId=" + assetDefinition.getId()));
        assertEquals("导出里的资产名", rows.get(1).get(11), "导出取覆盖值");
        assertEquals("导出资产", assetRepository.findById(asset.getId()).orElseThrow().getName(),
                "导出不得回写业务库");
    }

    @Test
    @DisplayName("CSV：实例级字段并入动态列；不同实例同名 label 按 fieldId 归并、列名取首次出现")
    void csvMergesInstanceFields() throws Exception {
        String first = createSubmission(definition.getId(), adminToken, engineerUserId.toString());
        String second = createSubmission(definition.getId(), engineerToken, null);

        mockMvc.perform(post(BASE + "/" + first + "/fields").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"label\":\"现场说明\",\"fieldType\":\"TEXT\"}"))
                .andExpect(status().isCreated());
        mockMvc.perform(post(BASE + "/" + second + "/fields").header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"label\":\"现场说明\",\"fieldType\":\"TEXT\"}"))
                .andExpect(status().isCreated());

        List<List<String>> rows = parseCsv(exportCsv(adminToken, ""));
        List<String> header = rows.get(0);
        // 动态列按 fieldId 归并：两个实例的实例级字段 fieldId 不同 → 各自一列；
        // 列名都是「现场说明」，重名时补序号（否则 Excel 里两个同名表头没法定位）
        assertEquals(16, header.size(), "3 模板字段 + 2 个实例字段（按 fieldId 归并后仍是 2 列）: " + header);
        assertEquals("现场说明", header.get(14));
        assertEquals("现场说明 (2)", header.get(15));
    }

    @Test
    @DisplayName("CSV：空值输出空字符串（不是 null 文本）")
    void csvEmptyCellsAreEmptyStrings() throws Exception {
        createSubmission(definition.getId(), adminToken, engineerUserId.toString());
        List<String> row = parseCsv(exportCsv(adminToken, "")).get(1);
        assertEquals("", row.get(13), "未填写的备注应为空串");
        assertEquals("", row.get(9), "未提交时提交时间为空串");
        assertEquals("", row.get(7), "未设确认人时为空串");
    }

    // ------------------------------------------------------------------ 二、XLSX

    @Test
    @DisplayName("XLSX：可被 POI 读回，行数/中文表头/数字列类型正确")
    void xlsxReadableByPoi() throws Exception {
        String id = createSubmission(definition.getId(), adminToken, engineerUserId.toString());
        mockMvc.perform(put(BASE + "/" + id + "/values").header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"values\":[{\"fieldId\":\"" + numberField.getId() + "\",\"filledValue\":\"88\"}]}"))
                .andExpect(status().isOk());

        byte[] content = mockMvc.perform(get(BASE + "/export?format=xlsx").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row header = sheet.getRow(0);
            assertEquals("表单ID", header.getCell(0).getStringCellValue());
            assertEquals("客户名称", header.getCell(11).getStringCellValue());
            assertEquals("得分", header.getCell(12).getStringCellValue());
            assertEquals(1, sheet.getLastRowNum(), "1 条数据行");

            Row dataRow = sheet.getRow(1);
            assertEquals("导出客户", dataRow.getCell(11).getStringCellValue(), "中文不乱码");
            assertEquals(CellType.NUMERIC, dataRow.getCell(12).getCellType(), "NUMBER 字段应为数值单元格");
            assertEquals(88.0, dataRow.getCell(12).getNumericCellValue());
            assertEquals(CellType.BLANK, dataRow.getCell(13).getCellType(), "空值为空单元格");
        }
    }

    // ------------------------------------------------------------------ 三、权限

    @Test
    @DisplayName("权限：普通用户只导出与自己相关的；ADMIN/PROJECT_LEADER 导出全部")
    void exportScope() throws Exception {
        createSubmission(definition.getId(), adminToken, engineerUserId.toString());          // 工程师被指派
        createSubmission(definition.getId(), leaderToken, null);                             // leader 发起
        createSubmission(definition.getId(), adminToken, null);                              // admin 发起

        // 工程师：只有被指派给自己的那 1 条
        List<List<String>> engineerRows = parseCsv(exportCsv(engineerToken, ""));
        assertEquals(2, engineerRows.size(), "工程师应只看到 1 条数据 + 表头");

        // 组长（TEAM_LEADER）不是导出管理层，仍按「与我相关」收窄
        List<List<String>> leaderRows = parseCsv(exportCsv(leaderToken, ""));
        assertEquals(2, leaderRows.size(), "TEAM_LEADER 只应看到自己发起的那 1 条");

        // PROJECT_LEADER 属于管理层：导出全部。BaseTest 只种了 team_leader，这里补一个 project_leader 用户 + 角色
        String projectLeaderToken = projectLeaderToken();
        List<List<String>> projectLeaderRows = parseCsv(exportCsv(projectLeaderToken, ""));
        assertEquals(4, projectLeaderRows.size(), "PROJECT_LEADER 应看到全部 3 条");

        List<List<String>> adminRows = parseCsv(exportCsv(adminToken, ""));
        assertEquals(4, adminRows.size(), "ADMIN 应看到全部 3 条");
    }

    @Test
    @DisplayName("单条导出：他人 → 404；自己相关 → 200（中文文件名 RFC 5987）")
    void exportOnePermissionAndFileName() throws Exception {
        String id = createSubmission(definition.getId(), leaderToken, null);

        mockMvc.perform(get(BASE + "/" + id + "/export?format=csv").header("Authorization", engineerToken))
                .andExpect(status().isNotFound());

        var response = mockMvc.perform(get(BASE + "/" + id + "/export?format=csv")
                        .header("Authorization", leaderToken))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        String disposition = response.getHeader("Content-Disposition");
        assertNotNull(disposition);
        assertTrue(disposition.contains("filename*=UTF-8''"), "应有 RFC 5987 编码: " + disposition);
        assertTrue(disposition.contains("%E5%AF%BC%E5%87%BA"), "中文文件名应被 URL 编码: " + disposition);
        assertTrue(disposition.contains(".csv"));
    }

    // ------------------------------------------------------------------ 四、过滤与上限

    @Test
    @DisplayName("上限：超过 max-rows → 400 且提示缩小范围")
    void exportExceedsLimit() throws Exception {
        for (int i = 0; i < 4; i++) {
            createSubmission(definition.getId(), adminToken, null);
        }
        mockMvc.perform(get(BASE + "/export?format=csv").header("Authorization", adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("过滤：status 白名单（非法 400）+ definitionId + from/to")
    void exportFilters() throws Exception {
        String id = createSubmission(definition.getId(), adminToken, engineerUserId.toString());
        mockMvc.perform(post(BASE + "/" + id + "/submit").header("Authorization", engineerToken))
                .andExpect(status().isOk());

        assertEquals(2, parseCsv(exportCsv(adminToken, "&status=SUBMITTED")).size());
        assertEquals(1, parseCsv(exportCsv(adminToken, "&status=DRAFT")).size(), "只有表头");

        mockMvc.perform(get(BASE + "/export?format=csv&status=BOGUS").header("Authorization", adminToken))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(BASE + "/export?format=pdf").header("Authorization", adminToken))
                .andExpect(status().isBadRequest());

        // definitionId 过滤：别的模板没有数据
        FormDefinition other = definitionRepository.save(Objects.requireNonNull(FormDefinition.builder()
                .code("FORM-EXP-OTHER-" + System.nanoTime()).name("空模板").enabled(true).build()));
        assertEquals(1, parseCsv(exportCsv(adminToken, "&definitionId=" + other.getId())).size());

        // from/to：未来日期 → 空
        assertEquals(1, parseCsv(exportCsv(adminToken, "&from=2099-01-01")).size());
        assertEquals(2, parseCsv(exportCsv(adminToken, "&from=2000-01-01&to=2099-12-31")).size());
        mockMvc.perform(get(BASE + "/export?format=csv&from=2099-01-01&to=2000-01-01")
                        .header("Authorization", adminToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("审计：每次导出 1 条 EXPORT_FORM_SUBMISSIONS（详情含 format/rows）")
    void exportAudit() throws Exception {
        createSubmission(definition.getId(), adminToken, null);
        exportCsv(adminToken, "");
        mockMvc.perform(get(BASE + "/export?format=xlsx").header("Authorization", adminToken))
                .andExpect(status().isOk());

        var logs = auditLogRepository.findByAction("EXPORT_FORM_SUBMISSIONS");
        assertEquals(2, logs.size(), "两次导出应各写 1 条");
        assertTrue(logs.get(0).getDetails().contains("format="));
        assertTrue(logs.get(0).getDetails().contains("rows="));
    }

    @Test
    @DisplayName("未登录：两个导出端点 → 401")
    void exportRequiresLogin() throws Exception {
        mockMvc.perform(get(BASE + "/export?format=csv")).andExpect(status().isUnauthorized());
        mockMvc.perform(get(BASE + "/" + UUID.randomUUID() + "/export?format=csv"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("导出空结果：仍返回表头（前端不用特判）")
    void exportEmptyStillHasHeader() throws Exception {
        List<List<String>> rows = parseCsv(exportCsv(adminToken, ""));
        assertEquals(1, rows.size());
        assertFalse(rows.get(0).isEmpty());
        assertEquals("表单ID", rows.get(0).get(0));
    }
    /** 造一个 PROJECT_LEADER 用户并签发 token（BaseTest 未内置该角色） */
    private String projectLeaderToken() {
        String username = "pleader_" + System.nanoTime();
        com.vulnaudit.entity.User user = userRepository.save(Objects.requireNonNull(
                com.vulnaudit.entity.User.builder()
                        .username(username)
                        .passwordHash("x".repeat(60))
                        .email(username + "@test.com")
                        .fullName("项目负责人")
                        .status("active")
                        .build()));
        com.vulnaudit.entity.Role role = roleRepository.findByCode("project_leader").orElseGet(() ->
                roleRepository.save(Objects.requireNonNull(com.vulnaudit.entity.Role.builder()
                        .name("项目负责人").code("project_leader").description("项目负责人").isSystem(false).build())));
        userRoleRepository.save(Objects.requireNonNull(com.vulnaudit.entity.UserRole.builder()
                .userId(user.getId()).roleId(role.getId()).assignedBy(adminUserId)
                .assignedAt(java.time.LocalDateTime.now()).build()));
        return Objects.requireNonNull(generateTestToken(username, "ROLE_PROJECT_LEADER"));
    }
}