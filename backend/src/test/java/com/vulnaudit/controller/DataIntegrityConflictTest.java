package com.vulnaudit.controller;

import com.vulnaudit.dto.ApiResponse;
import com.vulnaudit.exception.DataConflictMessages;
import com.vulnaudit.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 唯一键冲突统一映射 409（t133）
 *
 * <p><b>缺陷背景</b>：{@code GlobalExceptionHandler} 原来没有 {@code DataIntegrityViolationException} 分支，
 * 客户编码唯一索引冲突会落进兜底 {@code Exception} 分支变成 <b>500</b>（t129 声称的「唯一索引兜底 409」并未落地）。
 *
 * <p><b>本类验证</b>：
 * <ol>
 *   <li>唯一键冲突（MariaDB {@code Duplicate entry … for key 'uk_customers_code'}、H2
 *       {@code Unique index … ON PUBLIC.CUSTOMERS(CODE …)}、Hibernate 生成的 {@code UK_xxx} /
 *       {@code users.username} 形态）→ <b>409</b>，文案按约束识别为「客户编码已存在」这类可读消息；</li>
 *   <li>NOT NULL / 外键等非唯一键完整性失败 → <b>400</b>，绝不被误判成 409；</li>
 *   <li>无法识别的唯一约束 → 409 + 通用文案「数据冲突，请检查唯一字段」；</li>
 *   <li>响应体不含 SQL 原文 / 表名 / 列名 / 约束名 / 异常类名；</li>
 *   <li>中英文各覆盖一次（{@code Accept-Language: en}）。</li>
 * </ol>
 *
 * <p>端到端用 {@code standaloneSetup} + 真实 {@link GlobalExceptionHandler}：走真实 DispatcherServlet、
 * 异常解析器与语言包，而不是只调方法。
 */
@DisplayName("唯一键冲突映射 409 测试")
public class DataIntegrityConflictTest extends BaseTest {

    private static final String CUSTOMERS = "/api/v1/customers";
    private static final String PROJECTS = "/api/v1/projects";

    @Autowired
    private GlobalExceptionHandler globalExceptionHandler;

    // ------------------------------------------------------------------ 工具

    /** MariaDB / MySQL 形态：SQLState 23000 + Duplicate entry … for key */
    private static DataIntegrityViolationException mariaDbDuplicate(String key, String value) {
        return new DataIntegrityViolationException(
                "could not execute statement",
                new SQLIntegrityConstraintViolationException(
                        "Duplicate entry '" + value + "' for key '" + key + "'", "23000"));
    }

    /** H2 形态：SQLState 23505 + Unique index … ON PUBLIC.TABLE(COLUMN …) */
    private static DataIntegrityViolationException h2Unique(String message) {
        return new DataIntegrityViolationException("could not execute statement",
                new SQLException(message, "23505"));
    }

    /** NOT NULL 形态：SQLState 23502 */
    private static DataIntegrityViolationException notNull(String column) {
        return new DataIntegrityViolationException("could not execute statement",
                new SQLException("NULL not allowed for column \"" + column + "\"", "23502"));
    }

    /** 外键形态：SQLState 23506（H2）/ 23000（MariaDB） */
    private static DataIntegrityViolationException foreignKey(String constraint) {
        return new DataIntegrityViolationException(
                "could not execute statement",
                new SQLException("Referential integrity constraint violation: \"" + constraint + "\"", "23506"));
    }

    /** 端到端 MVC：真实 advice + 真实语言包 */
    private MockMvc adviceMvc() {
        return MockMvcBuilders.standaloneSetup(new ConflictProbeController())
                .setControllerAdvice(globalExceptionHandler)
                .build();
    }

    private void assertNoSqlLeak(String body) {
        for (String forbidden : new String[]{
                "SQLIntegrityConstraintViolationException", "DataIntegrityViolationException",
                "Duplicate entry", "unique index", "Unique index", "uk_", "CONSTRAINT",
                "PUBLIC.", "NON_NULL", "not allowed for column",
                "customers(", "users(", "projects("}) {
            assertFalse(body.contains(forbidden),
                    "响应体不得泄露底层 SQL 细节，发现: " + forbidden + " → " + body);
        }
    }

    // ------------------------------------------------------------------ 一、端到端（真实 MVC + advice）

    @Test
    @DisplayName("客户 code 唯一索引冲突 → 409「客户编码已存在」（中文默认）且不泄露 SQL")
    void customerCodeConflict409() throws Exception {
        MvcResult result = adviceMvc()
                .perform(post("/test-conflict/db").param("kind", "customer-code"))
                .andExpect(status().isConflict())
                .andReturn();
        String body = result.getResponse().getContentAsString();

        assertEquals("客户编码已存在，请更换编码", objectMapper.readTree(body).path("message").asText(), body);
        assertFalse(objectMapper.readTree(body).path("success").asBoolean(true), "success 应为 false");
        assertNoSqlLeak(body);
    }

    @Test
    @DisplayName("同一冲突在 Accept-Language: en 下返回英文文案")
    void customerCodeConflict409English() throws Exception {
        String body = adviceMvc()
                .perform(post("/test-conflict/db").param("kind", "customer-code")
                        .header("Accept-Language", "en-US,en;q=0.9"))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();

        assertEquals("Customer code already exists, please choose another",
                objectMapper.readTree(body).path("message").asText(), body);
        assertNoSqlLeak(body);
    }

    @Test
    @DisplayName("项目 code 唯一索引冲突 → 409「项目编码已存在」")
    void projectCodeConflict409() throws Exception {
        String body = adviceMvc()
                .perform(post("/test-conflict/db").param("kind", "project-code"))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();
        assertEquals("项目编码已存在，请更换编码", objectMapper.readTree(body).path("message").asText(), body);
        assertNoSqlLeak(body);
    }

    @Test
    @DisplayName("重复用户名 → 409「用户名已存在」（Hibernate 生成名字形态也识别）")
    void duplicateUsername409() throws Exception {
        String body = adviceMvc()
                .perform(post("/test-conflict/db").param("kind", "username"))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();
        assertEquals("用户名已存在，请更换", objectMapper.readTree(body).path("message").asText(), body);
    }

    @Test
    @DisplayName("NOT NULL 失败 → 400（不得误判为 409）")
    void notNullViolation400() throws Exception {
        String body = adviceMvc()
                .perform(post("/test-conflict/db").param("kind", "not-null"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertEquals("数据完整性校验失败，请检查必填项与关联数据",
                objectMapper.readTree(body).path("message").asText(), body);
        assertNoSqlLeak(body);
    }

    @Test
    @DisplayName("外键失败 → 400（不得误判为 409）")
    void foreignKeyViolation400() throws Exception {
        adviceMvc().perform(post("/test-conflict/db").param("kind", "foreign-key"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("无法识别的唯一约束 → 409 + 通用文案「数据冲突，请检查唯一字段」")
    void unknownUniqueConstraint409Generic() throws Exception {
        String body = adviceMvc()
                .perform(post("/test-conflict/db").param("kind", "unknown-unique"))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();
        assertEquals("数据冲突，请检查唯一字段", objectMapper.readTree(body).path("message").asText(), body);
        assertNoSqlLeak(body);
    }

    // ------------------------------------------------------------------ 二、约束识别（不同数据库命名形态）

    @Test
    @DisplayName("H2 列形态（ON PUBLIC.CUSTOMERS(CODE)）→ 仍识别为客户编码冲突")
    void h2ColumnShapeRecognized() {
        ResponseEntity<ApiResponse<Void>> response = globalExceptionHandler.handleDataIntegrityViolation(
                h2Unique("Unique index or primary key violation: \"PUBLIC.UK_CUSTOMERS_CODE_INDEX_2 "
                        + "ON PUBLIC.CUSTOMERS(CODE NULLS FIRST) VALUES ( /* 1 */ 'CUST-X' )\""));

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertEquals("客户编码已存在，请更换编码",
                Objects.requireNonNull(response.getBody()).getMessage());
    }

    @Test
    @DisplayName("Hibernate 生成名（for key 'users.username'）→ 识别为用户名冲突")
    void hibernateGeneratedNameRecognized() {
        assertEquals("用户名已存在，请更换",
                Objects.requireNonNull(globalExceptionHandler.handleDataIntegrityViolation(
                        mariaDbDuplicate("users.username", "admin")).getBody()).getMessage());
    }

    @Test
    @DisplayName("已知约束映射：remember-me hash / 文档版本")
    void knownConstraintsMapped() {
        assertEquals("登录状态冲突，请重新登录",
                Objects.requireNonNull(globalExceptionHandler.handleDataIntegrityViolation(
                        mariaDbDuplicate("uk_rm_hash", "abc")).getBody()).getMessage());

        assertEquals("文档版本号冲突，请刷新后重试",
                Objects.requireNonNull(globalExceptionHandler.handleDataIntegrityViolation(
                        h2Unique("Unique index or primary key violation: \"PUBLIC.UK_DOCUMENT_VERSIONS_DOCUMENT_VERSION_INDEX "
                                + "ON PUBLIC.DOCUMENT_VERSIONS(DOCUMENT_ID, VERSION_NO)\"")).getBody()).getMessage());
    }

    @Test
    @DisplayName("分类口径：23505/duplicate → 唯一键；23502/23506 → 非唯一键；未知约束无专用文案")
    void classification() {
        assertTrue(DataConflictMessages.isUniqueViolation(mariaDbDuplicate("uk_customers_code", "C")));
        assertTrue(DataConflictMessages.isUniqueViolation(h2Unique("Unique index or primary key violation")));
        assertFalse(DataConflictMessages.isUniqueViolation(notNull("CODE")));
        assertFalse(DataConflictMessages.isUniqueViolation(foreignKey("FK_ASSET_PROJECT")));
        assertTrue(DataConflictMessages.isNotNullViolation(notNull("CODE")));

        assertNull(DataConflictMessages.messageKeyFor(mariaDbDuplicate("UK_deadbeef001", "x")),
                "未知约束应返回 null（走通用文案）");
        assertEquals("error.customer.code.exists",
                DataConflictMessages.messageKeyFor(mariaDbDuplicate("uk_customers_code", "x")));
    }

    // ------------------------------------------------------------------ 三、真实接口回归（客户此前是 500）

    @Test
    @DisplayName("POST /customers 重复 code → 409（此前 500），消息可读且无 SQL 细节")
    void apiDuplicateCustomerCode409() throws Exception {
        String code = "T133-DUP-" + System.nanoTime();
        String body = "{\"code\":\"" + code + "\",\"name\":\"重复客户\"}";

        mockMvc.perform(post(CUSTOMERS).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        MvcResult second = mockMvc.perform(post(CUSTOMERS).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andReturn();
        String responseBody = second.getResponse().getContentAsString();

        assertTrue(responseBody.contains("客户编码已存在"), "消息应可读: " + responseBody);
        assertNoSqlLeak(responseBody);
    }

    @Test
    @DisplayName("POST /projects 重复 code → 409（回归，行为不变）")
    void apiDuplicateProjectCode409() throws Exception {
        String customerId;
        String customerBody = "{\"name\":\"项目冲突客户\"}";
        String created = mockMvc.perform(post(CUSTOMERS).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content(customerBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        customerId = objectMapper.readTree(created).path("data").path("id").asText();

        String code = "PRJ-2026-02-03-ABCDEF";
        String projectBody = "{\"code\":\"" + code + "\",\"customerId\":\"" + customerId
                + "\",\"name\":\"重复项目\",\"managerId\":\"" + adminUserId + "\"}";

        mockMvc.perform(post(PROJECTS).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content(projectBody))
                .andExpect(status().isCreated());

        MvcResult second = mockMvc.perform(post(PROJECTS).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content(projectBody))
                .andExpect(status().isConflict())
                .andReturn();
        assertNoSqlLeak(second.getResponse().getContentAsString());
    }

    // ------------------------------------------------------------------ 探针控制器（仅测试用）

    /** 测试专用「抛出数据库完整性异常」的端点：走真实 DispatcherServlet + 真实 advice */
    @RestController
    static class ConflictProbeController {

        @PostMapping("/test-conflict/db")
        void boom(@RequestParam("kind") String kind) {
            throw switch (kind) {
                case "customer-code" -> mariaDbDuplicate("uk_customers_code", "CUST-EXIST");
                case "project-code" -> h2Unique("Unique index or primary key violation: "
                        + "\"PUBLIC.UK_PROJECTS_CODE_INDEX_1 ON PUBLIC.PROJECTS(CODE NULLS FIRST)\"");
                case "username" -> mariaDbDuplicate("users.username", "admin");
                case "not-null" -> notNull("CODE");
                case "foreign-key" -> foreignKey("FK_ASSET_PROJECT_LINK_PROJECT");
                case "unknown-unique" -> mariaDbDuplicate("UK_deadbeef001", "x");
                default -> new IllegalStateException("unknown kind: " + kind);
            };
        }
    }
}
