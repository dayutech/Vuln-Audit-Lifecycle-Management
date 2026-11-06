package com.vulnaudit.controller;

import com.vulnaudit.entity.Customer;
import com.vulnaudit.entity.IndustryType;
import com.vulnaudit.repository.CustomerRepository;
import com.vulnaudit.repository.IndustryTypeRepository;
import com.vulnaudit.service.IndustryTypeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 客户编码自动生成 + 国标行业枚举检索（t129）
 */
@DisplayName("客户编码与行业枚举测试")
public class CustomerCodeIndustryTest extends BaseTest {

    private static final String CUSTOMERS = "/api/v1/customers";
    private static final String INDUSTRIES = "/api/v1/industry-types";

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private IndustryTypeRepository industryTypeRepository;

    @Autowired
    private IndustryTypeService industryTypeService;

    @BeforeEach
    void ensureIndustries() {
        // 启动器已插入；此处兜底（保证用例独立可跑）
        industryTypeService.ensureBuiltin();
    }

    // ------------------------------------------------------------------ 工具

    private String createCustomerJson(String code, String name, String industry, String industryCode) {
        StringBuilder sb = new StringBuilder("{");
        if (code != null) {
            sb.append("\"code\":\"").append(code).append("\",");
        }
        sb.append("\"name\":\"").append(name).append("\"");
        if (industry != null) {
            sb.append(",\"industry\":\"").append(industry).append("\"");
        }
        if (industryCode != null) {
            sb.append(",\"industryCode\":\"").append(industryCode).append("\"");
        }
        return sb.append("}").toString();
    }

    private Customer createCustomer(String code, String name, String industry, String industryCode) throws Exception {
        mockMvc.perform(post(CUSTOMERS).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(createCustomerJson(code, name, industry, industryCode)))
                .andExpect(status().isCreated());
        return customerRepository.findAll().stream()
                .filter(c -> name.equals(c.getName()))
                .max(java.util.Comparator.comparing(Customer::getCreatedAt))
                .orElseThrow();
    }

    // ------------------------------------------------------------------ 编码自动生成

    @Test
    @DisplayName("未传 code → 自动生成 CUST-+7 位大写字母（总长 12），两次创建不同")
    void autoGenerateCode() throws Exception {
        Customer first = createCustomer(null, "自动编码客户A", null, null);
        Customer second = createCustomer(null, "自动编码客户B", null, null);

        assertTrue(first.getCode().matches("^CUST-[A-Z]{7}$"), "格式不符: " + first.getCode());
        assertEquals(12, first.getCode().length());
        assertNotEquals(first.getCode(), second.getCode(), "两次创建编码必须不同");
    }

    @Test
    @DisplayName("显式传 code → 原样保留（便于导入历史数据）")
    void explicitCodeKept() throws Exception {
        Customer customer = createCustomer("LEGACY-0001", "历史客户", null, null);
        assertEquals("LEGACY-0001", customer.getCode());
    }

    @Test
    @DisplayName("显式传重复 code → 409")
    void duplicateCodeRejected() throws Exception {
        createCustomer("DUP-0001", "重复编码客户1", null, null);
        mockMvc.perform(post(CUSTOMERS).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(createCustomerJson("DUP-0001", "重复编码客户2", null, null)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("生成的编码写入数据库且与旧记录不冲突（含历史行）")
    void generatedCodeUniqueAgainstExisting() throws Exception {
        createCustomer("CUST-AAAAAAA", "占位客户", null, null);
        Customer customer = createCustomer(null, "新客户", null, null);
        assertFalse("CUST-AAAAAAA".equals(customer.getCode()));
        assertTrue(customerRepository.existsByCode(customer.getCode()));
    }

    // ------------------------------------------------------------------ 行业检索

    @Test
    @DisplayName("keyword=软件 → 命中「软件和信息技术服务业」")
    void searchByChineseKeyword() throws Exception {
        mockMvc.perform(get(INDUSTRIES).header("Authorization", adminToken).param("keyword", "软件"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("I65"))
                .andExpect(jsonPath("$.data[0].name").value("软件和信息技术服务业"))
                .andExpect(jsonPath("$.data[0].builtin").value(true));
    }

    @Test
    @DisplayName("关键字大小写不敏感（code 检索 i65 / I65 等价）")
    void searchCaseInsensitive() throws Exception {
        mockMvc.perform(get(INDUSTRIES).header("Authorization", adminToken).param("keyword", "i65"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].code").value("I65"));
    }

    @Test
    @DisplayName("limit 生效且上限 100")
    void searchLimit() throws Exception {
        mockMvc.perform(get(INDUSTRIES).header("Authorization", adminToken).param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)));
        mockMvc.perform(get(INDUSTRIES).header("Authorization", adminToken).param("limit", "1000"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("禁用项不出现在检索结果")
    void disabledIndustryHidden() throws Exception {
        IndustryType type = industryTypeRepository.findByCode("T").orElseThrow();
        type.setEnabled(false);
        industryTypeRepository.save(type);

        mockMvc.perform(get(INDUSTRIES).header("Authorization", adminToken).param("keyword", "国际组织"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("内置行业至少 20 条且 builtin=true")
    void builtinSeeded() {
        // t130 给 industry_types 加软删后，仓储方法改为 countByBuiltinTrueAndDeletedAtIsNull
        assertTrue(industryTypeRepository.countByBuiltinTrueAndDeletedAtIsNull() >= 20,
                "内置行业数量不足: " + industryTypeRepository.countByBuiltinTrueAndDeletedAtIsNull());
        assertTrue(industryTypeRepository.findByCode("I65").isPresent());
    }

    // ------------------------------------------------------------------ 客户与行业联动

    @Test
    @DisplayName("创建客户带 industryCode → 落库 code + 显示名一致")
    void createWithIndustryCode() throws Exception {
        Customer customer = createCustomer(null, "软件客户", null, "I65");
        assertEquals("I65", customer.getIndustryCode());
        assertEquals("软件和信息技术服务业", customer.getIndustry());
    }

    @Test
    @DisplayName("非法 / 停用的 industryCode → 400")
    void invalidIndustryCodeRejected() throws Exception {
        mockMvc.perform(post(CUSTOMERS).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(createCustomerJson(null, "非法行业客户", null, "ZZ99")))
                .andExpect(status().isBadRequest());

        IndustryType type = industryTypeRepository.findByCode("T").orElseThrow();
        type.setEnabled(false);
        industryTypeRepository.save(type);
        mockMvc.perform(post(CUSTOMERS).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(createCustomerJson(null, "停用行业客户", null, "T")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("旧前端只传 industry 名称 → 能匹配则回填 code")
    void legacyIndustryNameBackfillsCode() throws Exception {
        Customer customer = createCustomer(null, "旧前端客户", "软件和信息技术服务业", null);
        assertEquals("I65", customer.getIndustryCode());
        assertEquals("软件和信息技术服务业", customer.getIndustry());
    }

    @Test
    @DisplayName("旧前端传匹配不到的名称 → 不报错，只存名称且 code 为空")
    void unmatchedIndustryNameAccepted() throws Exception {
        Customer customer = createCustomer(null, "自定义行业客户", "某不存在的行业", null);
        assertNull(customer.getIndustryCode());
        assertEquals("某不存在的行业", customer.getIndustry());
    }

    @Test
    @DisplayName("更新客户时不传 code → 保留原编码；传 industryCode → 同步显示名")
    void updateKeepsCodeAndSyncsIndustry() throws Exception {
        Customer customer = createCustomer(null, "待更新客户", null, null);
        String originalCode = customer.getCode();

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put(CUSTOMERS + "/" + customer.getId()).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"name\":\"待更新客户\",\"industryCode\":\"J\"}"))
                .andExpect(status().isOk());

        Customer updated = customerRepository.findById(customer.getId()).orElseThrow();
        assertEquals(originalCode, updated.getCode(), "未传 code 时应保留原编码");
        assertEquals("J", updated.getIndustryCode());
        assertEquals("金融业", updated.getIndustry());
        assertNotNull(updated.getIndustryCode());
    }
}
