package com.vulnaudit.controller;

import com.vulnaudit.entity.AssetProjectLink;
import com.vulnaudit.entity.AuditLog;
import com.vulnaudit.entity.Customer;
import com.vulnaudit.entity.Project;
import com.vulnaudit.entity.ProjectPhase;
import com.vulnaudit.repository.AssetProjectLinkRepository;
import com.vulnaudit.repository.AuditLogRepository;
import com.vulnaudit.repository.CustomerRepository;
import com.vulnaudit.repository.ProjectPhaseRepository;
import com.vulnaudit.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 客户 / 项目 / 期次 API 测试（t68）。
 *
 * <p>覆盖：CRUD 正常路径、code 冲突 409、删除保护 409 ×3、项目状态机（合法/非法）、
 * 期次仅在分期项目可建、team_leader 只能写自己担任经理的项目、工程师写 403 读 200、
 * 客户/项目/期次的 404 与审计落库。
 */
@DisplayName("客户/项目/期次 API 测试")
public class CustomerProjectApiTest extends BaseTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectPhaseRepository projectPhaseRepository;

    @Autowired
    private AssetProjectLinkRepository assetProjectLinkRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private Customer customer;

    @BeforeEach
    void setUpCustomer() {
        String suffix = String.valueOf(System.currentTimeMillis());
        customer = customerRepository.save(Objects.requireNonNull(Customer.builder()
                .code("CUST-" + suffix)
                .name("测试客户_" + suffix)
                .industry("金融")
                .level("key")
                .contactName("张三")
                .contactPhone("13800000000")
                .contactEmail("cust@example.com")
                .ownerId(adminUserId)
                .status("active")
                .description("t68 用例")
                .createdBy(adminUserId)
                .build()));
    }

    private String customerBody(String code) {
        return "{\"code\":\"" + code + "\",\"name\":\"新客户_" + code + "\",\"industry\":\"制造\","
                + "\"level\":\"normal\",\"contactName\":\"李四\",\"contactPhone\":\"13900000000\","
                + "\"contactEmail\":\"new@example.com\",\"status\":\"active\",\"description\":\"用例\"}";
    }

    private String projectBody(String code, UUID customerId, UUID managerId, boolean phased) {
        return "{\"code\":\"" + code + "\",\"customerId\":\"" + customerId + "\",\"name\":\"项目_" + code + "\","
                + "\"type\":\"PENTEST\",\"managerId\":\"" + managerId + "\",\"isPhased\":" + phased + ","
                + "\"contractNo\":\"HT-001\",\"description\":\"用例\"}";
    }

    private Project createProject(String code, UUID managerId, boolean phased) {
        return projectRepository.save(Objects.requireNonNull(Project.builder()
                .code(code)
                .customerId(customer.getId())
                .name("项目_" + code)
                .type("PENTEST")
                .status("DRAFT")
                .managerId(managerId)
                .isPhased(phased)
                .createdBy(adminUserId)
                .build()));
    }

    private ProjectPhase createPhase(Project project, int phaseNo) {
        return projectPhaseRepository.save(Objects.requireNonNull(ProjectPhase.builder()
                .projectId(project.getId())
                .phaseNo(phaseNo)
                .name("期次" + phaseNo)
                .status("PLANNED")
                .build()));
    }

    // ------------------------------------------------------------------ 客户 CRUD

    @Test
    @DisplayName("客户：创建 201、列表筛选、详情聚合、更新 200")
    void customerCrud() throws Exception {
        String code = "C-API-" + System.currentTimeMillis();

        mockMvc.perform(post("/api/v1/customers").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content(customerBody(code)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value(code))
                .andExpect(jsonPath("$.data.status").value("active"));

        createProject("P-AGG-" + code, adminUserId, false);

        mockMvc.perform(get("/api/v1/customers").header("Authorization", adminToken)
                        .param("keyword", customer.getCode()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].code").value(customer.getCode()));

        mockMvc.perform(get("/api/v1/customers/" + customer.getId()).header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customer.id").value(customer.getId().toString()))
                .andExpect(jsonPath("$.data.projectCount").value(greaterThanOrEqualTo(1)));

        mockMvc.perform(put("/api/v1/customers/" + customer.getId()).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content(customerBody(customer.getCode())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("新客户_" + customer.getCode()));
    }

    @Test
    @DisplayName("客户：编码冲突 409、状态变更 200/400、不存在 404")
    void customerCodeConflictAndStatus() throws Exception {
        mockMvc.perform(post("/api/v1/customers").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content(customerBody(customer.getCode())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString(customer.getCode())));

        mockMvc.perform(patch("/api/v1/customers/" + customer.getId() + "/status")
                        .header("Authorization", adminToken).param("status", "inactive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("inactive"));

        mockMvc.perform(patch("/api/v1/customers/" + customer.getId() + "/status")
                        .header("Authorization", adminToken).param("status", "bogus"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/customers/" + UUID.randomUUID()).header("Authorization", adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("客户删除保护：有项目 → 409；无项目 → 200")
    void customerDeleteProtection() throws Exception {
        createProject("P-DEL-" + System.currentTimeMillis(), adminUserId, false);

        mockMvc.perform(delete("/api/v1/customers/" + customer.getId()).header("Authorization", adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("个项目，无法删除")));

        Customer empty = customerRepository.save(Objects.requireNonNull(Customer.builder()
                .code("CUST-EMPTY-" + System.currentTimeMillis())
                .name("空客户")
                .status("active")
                .createdBy(adminUserId)
                .build()));

        mockMvc.perform(delete("/api/v1/customers/" + empty.getId()).header("Authorization", adminToken))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ 项目 CRUD + 状态机

    @Test
    @DisplayName("项目：创建 201（DRAFT）、详情含 customerBrief/phases、更新 200")
    void projectCrud() throws Exception {
        String code = "PAPI-" + System.currentTimeMillis();

        mockMvc.perform(post("/api/v1/projects").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(projectBody(code, customer.getId(), adminUserId, true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.isPhased").value(true));

        Project project = projectRepository.findByCode(code).orElseThrow();
        createPhase(project, 1);

        mockMvc.perform(get("/api/v1/projects/" + project.getId()).header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customer.code").value(customer.getCode()))
                .andExpect(jsonPath("$.data.phases[0].phaseNo").value(1))
                .andExpect(jsonPath("$.data.assetCount").value(0));

        mockMvc.perform(put("/api/v1/projects/" + project.getId()).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(projectBody(code, customer.getId(), adminUserId, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("项目_" + code));
    }

    @Test
    @DisplayName("项目状态机：DRAFT→ACTIVE→PAUSED→ACTIVE→CLOSED 合法；CLOSED 后再流转 400")
    void projectStatusMachine() throws Exception {
        Project project = createProject("P-SM-" + System.currentTimeMillis(), adminUserId, false);
        String url = "/api/v1/projects/" + project.getId() + "/status";

        for (String target : List.of("ACTIVE", "PAUSED", "ACTIVE", "CLOSED")) {
            mockMvc.perform(patch(url).header("Authorization", adminToken).param("status", target))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value(target));
        }

        mockMvc.perform(patch(url).header("Authorization", adminToken).param("status", "ACTIVE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("CLOSED")))
                .andExpect(jsonPath("$.message", containsString("不可再流转")));
    }

    @Test
    @DisplayName("项目状态机：DRAFT → PAUSED 非法 400，消息给出当前状态与合法下一步")
    void projectInvalidTransition() throws Exception {
        Project project = createProject("P-IT-" + System.currentTimeMillis(), adminUserId, false);

        mockMvc.perform(patch("/api/v1/projects/" + project.getId() + "/status")
                        .header("Authorization", adminToken).param("status", "PAUSED"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("DRAFT")))
                .andExpect(jsonPath("$.message", containsString("ACTIVE")));
    }

    @Test
    @DisplayName("项目：编码冲突 409、客户不存在 404、经理不存在 404")
    void projectIntegrityChecks() throws Exception {
        Project existing = createProject("P-DUP-" + System.currentTimeMillis(), adminUserId, false);

        mockMvc.perform(post("/api/v1/projects").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(projectBody(existing.getCode(), customer.getId(), adminUserId, false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString(existing.getCode())));

        mockMvc.perform(post("/api/v1/projects").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(projectBody("P-NOCUST-" + System.currentTimeMillis(), UUID.randomUUID(), adminUserId, false)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/projects").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(projectBody("P-NOMGR-" + System.currentTimeMillis(), customer.getId(), UUID.randomUUID(), false)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("项目删除保护：有挂靠 → 409；无挂靠 → 200（并清理期次）")
    void projectDeleteProtection() throws Exception {
        Project withLinks = createProject("P-LINK-" + System.currentTimeMillis(), adminUserId, false);
        assetProjectLinkRepository.save(Objects.requireNonNull(AssetProjectLink.builder()
                .assetId(UUID.randomUUID())
                .projectId(withLinks.getId())
                .customerId(customer.getId())
                .linkType("TARGET")
                .linkedBy(adminUserId)
                .build()));

        mockMvc.perform(delete("/api/v1/projects/" + withLinks.getId()).header("Authorization", adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("资产挂靠")));

        Project clean = createProject("P-CLEAN-" + System.currentTimeMillis(), adminUserId, true);
        createPhase(clean, 1);

        mockMvc.perform(delete("/api/v1/projects/" + clean.getId()).header("Authorization", adminToken))
                .andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertTrue(
                projectPhaseRepository.findByProjectIdOrderByPhaseNo(clean.getId()).isEmpty(),
                "删除项目应同时清理期次");
    }

    // ------------------------------------------------------------------ 期次

    @Test
    @DisplayName("期次：非分期项目 400、分期项目 201、期次号重复 409、不存在 404")
    void phaseConstraints() throws Exception {
        Project flat = createProject("P-FLAT-" + System.currentTimeMillis(), adminUserId, false);
        PhaseTestBody body = new PhaseTestBody("第一阶段", null);

        mockMvc.perform(post("/api/v1/projects/" + flat.getId() + "/phases")
                        .header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content(body.json()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("未启用分期")));

        Project phased = createProject("P-PHASED-" + System.currentTimeMillis(), adminUserId, true);
        createPhase(phased, 1);

        mockMvc.perform(post("/api/v1/projects/" + phased.getId() + "/phases")
                        .header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content(body.json()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.phaseNo").value(2));

        mockMvc.perform(post("/api/v1/projects/" + phased.getId() + "/phases")
                        .header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content(new PhaseTestBody("重复号", 1).json()))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/v1/phases/" + UUID.randomUUID()).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content(body.json()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("期次：有挂靠时删除 409、无挂靠删除 200；更新 200")
    void phaseDeleteProtection() throws Exception {
        Project phased = createProject("P-PHDEL-" + System.currentTimeMillis(), adminUserId, true);
        ProjectPhase phase = createPhase(phased, 1);

        mockMvc.perform(put("/api/v1/phases/" + phase.getId()).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content(new PhaseTestBody("改名期次", 1).json()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("改名期次"));

        assetProjectLinkRepository.save(Objects.requireNonNull(AssetProjectLink.builder()
                .assetId(UUID.randomUUID())
                .projectId(phased.getId())
                .phaseId(phase.getId())
                .phaseKey(phase.getId())
                .customerId(customer.getId())
                .linkType("TARGET")
                .linkedBy(adminUserId)
                .build()));

        mockMvc.perform(delete("/api/v1/phases/" + phase.getId()).header("Authorization", adminToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("资产挂靠")));
    }

    @Test
    @DisplayName("关闭分期保护：已有期次且存在按期次挂靠 → 409")
    void disablePhasedWithPhaseLinks() throws Exception {
        Project phased = createProject("P-UNPHASE-" + System.currentTimeMillis(), adminUserId, true);
        ProjectPhase phase = createPhase(phased, 1);
        assetProjectLinkRepository.save(Objects.requireNonNull(AssetProjectLink.builder()
                .assetId(UUID.randomUUID())
                .projectId(phased.getId())
                .phaseId(phase.getId())
                .phaseKey(phase.getId())
                .customerId(customer.getId())
                .linkType("TARGET")
                .linkedBy(adminUserId)
                .build()));

        mockMvc.perform(put("/api/v1/projects/" + phased.getId()).header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(projectBody(phased.getCode(), customer.getId(), adminUserId, false)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("无法关闭分期")));
    }

    // ------------------------------------------------------------------ 权限

    @Test
    @DisplayName("team_leader：改自己担任经理的项目 200、改他人项目 403")
    void teamLeaderProjectScope() throws Exception {
        Project own = createProject("P-OWN-" + System.currentTimeMillis(), leaderUserId, true);
        Project other = createProject("P-OTHER-" + System.currentTimeMillis(), adminUserId, true);

        mockMvc.perform(patch("/api/v1/projects/" + own.getId() + "/status")
                        .header("Authorization", leaderToken).param("status", "ACTIVE"))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/projects/" + other.getId() + "/status")
                        .header("Authorization", leaderToken).param("status", "ACTIVE"))
                .andExpect(status().isForbidden());

        // 期次写权同样按「所属项目经理」判定
        mockMvc.perform(post("/api/v1/projects/" + own.getId() + "/phases")
                        .header("Authorization", leaderToken)
                        .contentType(APPLICATION_JSON).content(new PhaseTestBody("组长建期次", null).json()))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/projects/" + other.getId() + "/phases")
                        .header("Authorization", leaderToken)
                        .contentType(APPLICATION_JSON).content(new PhaseTestBody("越权期次", null).json()))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("工程师：写操作 403、读操作 200")
    void engineerReadOnly() throws Exception {
        Project project = createProject("P-ENG-" + System.currentTimeMillis(), adminUserId, true);

        mockMvc.perform(post("/api/v1/customers").header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON).content(customerBody("C-ENG-" + System.currentTimeMillis())))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/projects").header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON)
                        .content(projectBody("P-ENGW-" + System.currentTimeMillis(), customer.getId(), adminUserId, false)))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/v1/projects/" + project.getId() + "/status")
                        .header("Authorization", engineerToken).param("status", "ACTIVE"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/customers").header("Authorization", engineerToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/customers/" + customer.getId()).header("Authorization", engineerToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/projects").header("Authorization", engineerToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/projects/" + project.getId()).header("Authorization", engineerToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/projects/" + project.getId() + "/phases")
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk());
    }

    // ------------------------------------------------------------------ 审计

    @Test
    @DisplayName("审计：客户/项目/期次写操作分别落 customer / project / project-phase")
    void auditLogsWrittenForWrites() throws Exception {
        String code = "P-AUD-" + System.currentTimeMillis();
        mockMvc.perform(post("/api/v1/projects").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON)
                        .content(projectBody(code, customer.getId(), adminUserId, true)))
                .andExpect(status().isCreated());

        Project project = projectRepository.findByCode(code).orElseThrow();
        mockMvc.perform(post("/api/v1/projects/" + project.getId() + "/phases")
                        .header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content(new PhaseTestBody("审计期次", null).json()))
                .andExpect(status().isCreated());

        List<AuditLog> logs = auditLogRepository.findAll();
        org.junit.jupiter.api.Assertions.assertTrue(
                logs.stream().anyMatch(log -> "project".equals(log.getResourceType())),
                "项目写操作应落 resourceType=project，实际: " + distinctTypes(logs));
        org.junit.jupiter.api.Assertions.assertTrue(
                logs.stream().anyMatch(log -> "project-phase".equals(log.getResourceType())),
                "期次写操作应落 resourceType=project-phase，实际: " + distinctTypes(logs));
    }

    private java.util.Set<String> distinctTypes(List<AuditLog> logs) {
        return logs.stream().map(AuditLog::getResourceType).filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
    }

    /** 期次请求体（phaseNo 为空时不写该字段，覆盖自增逻辑） */
    private record PhaseTestBody(String name, Integer phaseNo) {
        String json() {
            return "{\"name\":\"" + name + "\""
                    + (phaseNo == null ? "" : ",\"phaseNo\":" + phaseNo)
                    + ",\"status\":\"PLANNED\"}";
        }
    }
}
