package com.vulnaudit.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.vulnaudit.entity.AuditLog;
import com.vulnaudit.entity.Customer;
import com.vulnaudit.entity.Project;
import com.vulnaudit.repository.AuditLogRepository;
import com.vulnaudit.repository.CustomerRepository;
import com.vulnaudit.repository.ProjectRepository;
import com.vulnaudit.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("CustomerProjectImport")
class ImportCustomerProjectTest extends BaseTest {

    @Autowired private CustomerRepository customerRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private UserRepository userRepository;

    private String suffix;

    @BeforeEach
    void setUpImportData() {
        suffix = String.valueOf(System.nanoTime());
    }

    private String row(String... cells) {
        return Stream.of(cells).map(cell -> cell == null ? "" : cell).collect(Collectors.joining(","));
    }

    private MockMultipartFile file(String content) {
        return new MockMultipartFile("file", "t85.csv", "text/csv",
                content.getBytes(StandardCharsets.UTF_8));
    }

    private String customerCsv(String... rows) {
        String header = "code,name,industry,level,ownerName,contactName,contactPhone,contactEmail,description";
        return header + "\n" + String.join("\n", rows) + "\n";
    }

    private String projectCsv(String... rows) {
        String header = "code,name,customerCode,type,managerName,startDate,endDate,isPhased,contractNo,description";
        return header + "\n" + String.join("\n", rows) + "\n";
    }

    private JsonNode postImport(String path, MockMultipartFile upload, String token) throws Exception {
        String body = mockMvc.perform(multipart(path).file(upload).header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    private Customer existingCustomer(String code) {
        return customerRepository.save(Objects.requireNonNull(Customer.builder()
                .code(code).name("T85 c_" + code).status("active").createdBy(adminUserId).build()));
    }

    private String adminUsername() {
        return userRepository.findById(adminUserId).orElseThrow().getUsername();
    }

    private String leaderUsername() {
        return userRepository.findById(leaderUserId).orElseThrow().getUsername();
    }

    @Test
    @DisplayName("customer import: two rows success + list + audit")
    void importCustomers_twoRowsSuccess() throws Exception {
        String codeA = "CUST-T85-A-" + suffix;
        String codeB = "CUST-T85-B-" + suffix;

        JsonNode data = postImport("/api/v1/import/customers", file(customerCsv(
                row(codeA, "T85 BankA", "finance", "A", adminUsername(), "zs", "13800000000", "a@example.com", "A"),
                row(codeB, "T85 MakeB", "manufacture", "C", null, "ls", "13900000000", "b@example.com", "B"))),
                adminToken);

        assertEquals(2, data.path("totalRows").asInt());
        assertEquals(2, data.path("successRows").asInt(), data.path("failedRows").toString());
        assertEquals(0, data.path("failedRows").size());

        Customer a = customerRepository.findByCode(codeA).orElseThrow();
        assertEquals("T85 BankA", a.getName());
        assertEquals("A", a.getLevel());
        assertNotNull(a.getOwnerId());
        assertEquals("active", a.getStatus());

        Customer b = customerRepository.findByCode(codeB).orElseThrow();
        assertEquals("C", b.getLevel());
        assertNull(b.getOwnerId());

        mockMvc.perform(get("/api/v1/customers").param("keyword", codeA).header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].code", containsString(codeA)));

        List<AuditLog> logs = auditLogRepository.findAll().stream()
                .filter(log -> "customer".equals(log.getResourceType())
                        && "import_customer".equals(log.getAction()))
                .toList();
        assertTrue(logs.stream().anyMatch(log -> a.getId().equals(log.getResourceId())));
        assertTrue(logs.stream().anyMatch(log -> b.getId().equals(log.getResourceId())));
    }

    @Test
    @DisplayName("customer import: duplicate code -> failedRows")
    void importCustomers_duplicateCodeFails() throws Exception {
        String duplicated = "CUST-T85-DUP-" + suffix;
        existingCustomer(duplicated);

        JsonNode data = postImport("/api/v1/import/customers", file(customerCsv(
                row("CUST-T85-OK-" + suffix, "T85 new", "finance", "B", null, null, null, null, "ok"),
                row(duplicated, "T85 dup", "finance", "B", null, null, null, null, "fail"))), adminToken);

        assertEquals(1, data.path("successRows").asInt());
        assertEquals(1, data.path("failedRows").size());
        assertTrue(data.path("failedRows").get(0).path("reason").asText().contains("XXX") == false);
        assertEquals(1, customerRepository.findAll().stream()
                .filter(item -> duplicated.equals(item.getCode())).count());
    }

    @Test
    @DisplayName("customer import: unknown ownerName -> failedRows")
    void importCustomers_unknownOwnerFails() throws Exception {
        String code = "CUST-T85-NOOWNER-" + suffix;
        JsonNode data = postImport("/api/v1/import/customers", file(customerCsv(
                row(code, "T85 noowner", "finance", "A", "not_exist_user_xyz", null, null, null, "x"))),
                adminToken);

        assertEquals(0, data.path("successRows").asInt());
        assertEquals(1, data.path("failedRows").size());
        assertTrue(customerRepository.findByCode(code).isEmpty());
    }

    @Test
    @DisplayName("customer import: invalid level -> failedRows")
    void importCustomers_invalidLevelFails() throws Exception {
        String code = "CUST-T85-BADLEVEL-" + suffix;
        JsonNode data = postImport("/api/v1/import/customers", file(customerCsv(
                row(code, "T85 badlevel", "finance", "SUPER", null, null, null, null, "x"))), adminToken);

        assertEquals(0, data.path("successRows").asInt());
        assertTrue(customerRepository.findByCode(code).isEmpty());
    }

    @Test
    @DisplayName("project import: two rows success + audit")
    void importProjects_twoRowsSuccess() throws Exception {
        Customer customer = existingCustomer("CUST-T85-PRJ-" + suffix);
        String codeA = "PRJ-T85-A-" + suffix;
        String codeB = "PRJ-T85-B-" + suffix;

        JsonNode data = postImport("/api/v1/import/projects", file(projectCsv(
                row(codeA, "T85 pentest", customer.getCode(), "PENTEST", adminUsername(),
                        "2025-01-01", "2025-12-31", "true", "HT-1", "A"),
                row(codeB, "T85 consult", customer.getCode(), "CONSULTING", leaderUsername(),
                        "2025-03-01", null, "0", null, "B"))), adminToken);

        assertEquals(2, data.path("successRows").asInt(), data.path("failedRows").toString());

        Project a = projectRepository.findByCode(codeA).orElseThrow();
        assertEquals("PENTEST", a.getType());
        assertEquals(Boolean.TRUE, a.getIsPhased());
        assertEquals("2025-01-01", a.getStartDate().toString());
        assertEquals(customer.getId(), a.getCustomerId());

        Project b = projectRepository.findByCode(codeB).orElseThrow();
        assertEquals(Boolean.FALSE, b.getIsPhased());
        assertNull(b.getEndDate());

        mockMvc.perform(get("/api/v1/projects").param("keyword", codeA).header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].code", containsString(codeA)));

        List<AuditLog> logs = auditLogRepository.findAll().stream()
                .filter(log -> "project".equals(log.getResourceType())
                        && "import_project".equals(log.getAction()))
                .toList();
        assertTrue(logs.stream().anyMatch(log -> a.getId().equals(log.getResourceId())));
        assertTrue(logs.stream().anyMatch(log -> b.getId().equals(log.getResourceId())));
    }

    @Test
    @DisplayName("project import: unknown customerCode -> failedRows")
    void importProjects_unknownCustomerFails() throws Exception {
        String code = "PRJ-T85-NOCUST-" + suffix;
        JsonNode data = postImport("/api/v1/import/projects", file(projectCsv(
                row(code, "T85 nocust", "CUST-NOT-EXIST-XYZ", "PENTEST", adminUsername(),
                        "2025-01-01", null, "false", null, "x"))), adminToken);

        assertEquals(0, data.path("successRows").asInt());
        assertTrue(projectRepository.findByCode(code).isEmpty());
    }

    @Test
    @DisplayName("project import: bad manager / bad type / bad date -> failedRows")
    void importProjects_fieldValidationFails() throws Exception {
        Customer customer = existingCustomer("CUST-T85-VAL-" + suffix);
        String badManagerCode = "PRJ-T85-BADMGR-" + suffix;
        String badTypeCode = "PRJ-T85-BADTYPE-" + suffix;
        String badDateCode = "PRJ-T85-BADDATE-" + suffix;

        JsonNode badManager = postImport("/api/v1/import/projects", file(projectCsv(
                row(badManagerCode, "T85 badmgr", customer.getCode(), "PENTEST", "nobody_xyz",
                        null, null, null, null, "x"))), adminToken);
        assertEquals(0, badManager.path("successRows").asInt());

        JsonNode badType = postImport("/api/v1/import/projects", file(projectCsv(
                row(badTypeCode, "T85 badtype", customer.getCode(), "WRONG_TYPE", adminUsername(),
                        null, null, null, null, "x"))), adminToken);
        assertEquals(0, badType.path("successRows").asInt());

        JsonNode badDate = postImport("/api/v1/import/projects", file(projectCsv(
                row(badDateCode, "T85 baddate", customer.getCode(), "PENTEST", adminUsername(),
                        "2025/01/01", null, "false", null, "x"))), adminToken);
        assertEquals(0, badDate.path("successRows").asInt());

        assertTrue(projectRepository.findByCode(badManagerCode).isEmpty());
        assertTrue(projectRepository.findByCode(badTypeCode).isEmpty());
        assertTrue(projectRepository.findByCode(badDateCode).isEmpty());
    }

    @Test
    @DisplayName("project import: partial success with correct line number")
    void importProjects_partialSuccess() throws Exception {
        Customer customer = existingCustomer("CUST-T85-MIX-" + suffix);
        String okCode = "PRJ-T85-MIXOK-" + suffix;
        String badCode = "PRJ-T85-MIXBAD-" + suffix;

        JsonNode data = postImport("/api/v1/import/projects", file(projectCsv(
                row(okCode, "T85 ok", customer.getCode(), "REAUDIT", adminUsername(),
                        "2025-02-01", null, "1", null, "ok"),
                row(badCode, "T85 bad", "CUST-NOPE", "PENTEST", adminUsername(),
                        null, null, null, null, "bad"))), adminToken);

        assertEquals(2, data.path("totalRows").asInt());
        assertEquals(1, data.path("successRows").asInt());
        assertEquals(1, data.path("failedRows").size());
        // 行号含表头：第 2 条数据行 = 第 3 行（与既有导入用例口径一致）
        assertEquals(3, data.path("failedRows").get(0).path("line").asInt());
        assertTrue(projectRepository.findByCode(okCode).isPresent());
        assertTrue(projectRepository.findByCode(badCode).isEmpty());
    }

    @Test
    @DisplayName("templates: customers / projects have BOM and two samples")
    void templates_haveBomAndTwoSamples() throws Exception {
        String customers = mockMvc.perform(get("/api/v1/import/templates/customers")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(customers.startsWith("\uFEFF"));
        assertTrue(customers.contains(
                "code,name,industry,level,ownerName,contactName,contactPhone,contactEmail,description"));
        assertEquals(3, customers.split("\r?\n").length);

        String projects = mockMvc.perform(get("/api/v1/import/templates/projects")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(projects.startsWith("\uFEFF"));
        assertTrue(projects.contains(
                "code,name,customerCode,type,managerName,startDate,endDate,isPhased,contractNo,description"));
        assertEquals(3, projects.split("\r?\n").length);
    }

    @Test
    @DisplayName("permissions: engineer / team leader -> 403")
    void importCustomersAndProjects_forbiddenRoles() throws Exception {
        String custCode = "CUST-T85-FORBID-" + suffix;
        String prjCode = "PRJ-T85-FORBID-" + suffix;

        mockMvc.perform(multipart("/api/v1/import/customers")
                        .file(file(customerCsv(row(custCode, "T85 forbid", "finance", "A",
                                null, null, null, null, "x"))))
                        .header("Authorization", engineerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(multipart("/api/v1/import/projects")
                        .file(file(projectCsv(row(prjCode, "T85 forbid", "CUST-X", "PENTEST",
                                adminUsername(), null, null, null, null, "x"))))
                        .header("Authorization", engineerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(multipart("/api/v1/import/customers")
                        .file(file(customerCsv(row("CUST-T85-TL-" + suffix, "T85 tl", "finance", "B",
                                null, null, null, null, "x"))))
                        .header("Authorization", leaderToken))
                .andExpect(status().isForbidden());

        assertTrue(customerRepository.findByCode(custCode).isEmpty());
        assertTrue(projectRepository.findByCode(prjCode).isEmpty());
    }
}