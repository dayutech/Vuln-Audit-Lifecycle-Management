package com.vulnaudit.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.vulnaudit.entity.Customer;
import com.vulnaudit.entity.Project;
import com.vulnaudit.repository.CustomerRepository;
import com.vulnaudit.repository.DocumentAttachmentRepository;
import com.vulnaudit.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 项目附件测试（t193）：新建项目时可上传合同附件，项目详情可查看/下载/删除。
 *
 * <p>实现复用 t162 的独立附件模型（`document_attachments` 的 owner_type/owner_id，
 * 新归属 {@code PROJECT}），因此这里重点验证：
 * <ul>
 *   <li>上传 → 列表 → 下载 → 删除 全链路；</li>
 *   <li>归属校验：A 项目的附件不能用 B 项目的路径下载（404）；</li>
 *   <li>权限：工程师上传被拒（403），列表/下载对登录用户开放；</li>
 *   <li>项目不存在 → 404；</li>
 *   <li>软删后列表不再出现。</li>
 * </ul>
 */
@DisplayName("项目附件（合同）测试")
@TestPropertySource(properties = {
        "app.attachments.dir=target/test-project-attachments",
        "app.attachments.max-bytes=1048576"
})
class ProjectAttachmentTest extends BaseTest {

    @Autowired
    private CustomerRepository customerRepository;
    @Autowired
    private ProjectRepository projectRepository;
    @Autowired
    private DocumentAttachmentRepository attachmentRepository;

    private Customer customer;
    private Project projectA;
    private Project projectB;

    @BeforeEach
    void setUpFixtures() {
        String suffix = String.valueOf(System.nanoTime());
        customer = customerRepository.save(Objects.requireNonNull(Customer.builder()
                .code("CUST-T193-" + suffix).name("t193 客户").level("A").status("active")
                .createdBy(adminUserId).build()));
        projectA = projectRepository.save(Objects.requireNonNull(Project.builder()
                .code("PRJ-T193A-" + suffix).customerId(customer.getId()).name("t193 项目A")
                .type("PENTEST").status("ACTIVE").managerId(adminUserId).isPhased(false)
                .createdBy(adminUserId).build()));
        projectB = projectRepository.save(Objects.requireNonNull(Project.builder()
                .code("PRJ-T193B-" + suffix).customerId(customer.getId()).name("t193 项目B")
                .type("PENTEST").status("ACTIVE").managerId(adminUserId).isPhased(false)
                .createdBy(adminUserId).build()));
    }

    private MockMultipartFile pdf(String name) {
        byte[] payload = new byte[4 * 1024];
        java.util.Arrays.fill(payload, (byte) 'c');
        return new MockMultipartFile("file", name, "application/pdf", payload);
    }

    private String upload(String token, Project project, MockMultipartFile file) throws Exception {
        String body = mockMvc.perform(multipart("/api/v1/projects/" + project.getId() + "/attachments")
                        .file(file).header("Authorization", token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asText();
    }

    @Test
    @DisplayName("上传 → 列表 → 下载 → 删除 全链路（中文文件名）")
    void uploadListDownloadDelete() throws Exception {
        String attachmentId = upload(adminToken, projectA, pdf("合同-2026.pdf"));

        mockMvc.perform(get("/api/v1/projects/" + projectA.getId() + "/attachments")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].fileName").value("合同-2026.pdf"))
                .andExpect(jsonPath("$.data[0].ownerType").value("PROJECT"));

        mockMvc.perform(get("/api/v1/projects/" + projectA.getId() + "/attachments/" + attachmentId + "/download")
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/projects/" + projectA.getId() + "/attachments/" + attachmentId)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/projects/" + projectA.getId() + "/attachments")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    @DisplayName("归属校验：A 项目的附件用 B 项目路径下载 → 404")
    void downloadWithWrongOwner_returns404() throws Exception {
        String attachmentId = upload(adminToken, projectA, pdf("合同A.pdf"));

        mockMvc.perform(get("/api/v1/projects/" + projectB.getId() + "/attachments/" + attachmentId + "/download")
                        .header("Authorization", adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("权限：工程师上传 → 403；列表对登录用户开放")
    void uploadRequiresWritePermission() throws Exception {
        mockMvc.perform(multipart("/api/v1/projects/" + projectA.getId() + "/attachments")
                        .file(pdf("x.pdf")).header("Authorization", engineerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/projects/" + projectA.getId() + "/attachments")
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("项目不存在 → 404（上传与列表都拦截）")
    void unknownProject_returns404() throws Exception {
        UUID missing = UUID.randomUUID();
        mockMvc.perform(get("/api/v1/projects/" + missing + "/attachments")
                        .header("Authorization", adminToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(multipart("/api/v1/projects/" + missing + "/attachments")
                        .file(pdf("x.pdf")).header("Authorization", adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("附件按项目隔离落库（ownerId = 项目 id，ownerType = PROJECT）")
    void attachmentsPersistedWithProjectOwner() throws Exception {
        String attachmentId = upload(adminToken, projectA, pdf("合同B.pdf"));
        var row = attachmentRepository.findById(UUID.fromString(attachmentId)).orElseThrow();
        assertEquals("PROJECT", row.getOwnerType());
        assertEquals(projectA.getId(), row.getOwnerId());
        assertTrue(row.getStoredName().endsWith("合同B.pdf"), "落盘名应保留清洗后的原文件名: " + row.getStoredName());
    }
}