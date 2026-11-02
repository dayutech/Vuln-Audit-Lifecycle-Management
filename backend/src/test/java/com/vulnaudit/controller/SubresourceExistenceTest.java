package com.vulnaudit.controller;

import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.AssetTag;
import com.vulnaudit.entity.AssetVersion;
import com.vulnaudit.entity.Document;
import com.vulnaudit.entity.DocumentTag;
import com.vulnaudit.entity.Environment;
import com.vulnaudit.entity.EnvironmentTemplate;
import com.vulnaudit.entity.EnvironmentSnapshot;
import com.vulnaudit.entity.KnowledgeArticle;
import com.vulnaudit.entity.Notification;
import com.vulnaudit.entity.NotificationRecipient;
import com.vulnaudit.entity.Role;
import com.vulnaudit.entity.Task;
import com.vulnaudit.entity.TaskAttachment;
import com.vulnaudit.entity.Team;
import com.vulnaudit.entity.TeamMember;
import com.vulnaudit.entity.User;
import com.vulnaudit.entity.UserRole;
import com.vulnaudit.entity.Vulnerability;
import com.vulnaudit.entity.VulnerabilityAttachment;
import com.vulnaudit.entity.VulnerabilityTag;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.AssetTagRepository;
import com.vulnaudit.repository.AssetVersionRepository;
import com.vulnaudit.repository.DocumentRepository;
import com.vulnaudit.repository.DocumentTagRepository;
import com.vulnaudit.repository.EnvironmentRepository;
import com.vulnaudit.repository.EnvironmentTemplateRepository;
import com.vulnaudit.repository.EnvironmentSnapshotRepository;
import com.vulnaudit.repository.KnowledgeArticleRepository;
import com.vulnaudit.repository.NotificationRecipientRepository;
import com.vulnaudit.repository.NotificationRepository;
import com.vulnaudit.repository.RoleRepository;
import com.vulnaudit.repository.TaskAttachmentRepository;
import com.vulnaudit.repository.TaskRepository;
import com.vulnaudit.repository.TeamMemberRepository;
import com.vulnaudit.repository.TeamRepository;
import com.vulnaudit.repository.UserRepository;
import com.vulnaudit.repository.UserRoleRepository;
import com.vulnaudit.repository.VulnerabilityAttachmentRepository;
import com.vulnaudit.repository.VulnerabilityRepository;
import com.vulnaudit.repository.VulnerabilityTagRepository;
import com.vulnaudit.security.JwtUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

/**
 * 子资源写操作的存在性统一测试（t65）
 *
 * <p>统一规则：<b>父资源不存在 → 404</b>；<b>子资源不属于该父资源 → 404</b>；正常路径语义不变。
 *
 * <p>本类不使用 {@code BaseTest}（避免与其事务夹具互相干扰），自建 admin 用户与 JWT，
 * 用例结束按记录 ID 清理。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("子资源存在性统一测试")
class SubresourceExistenceTest {

    private static final UUID MISSING = UUID.fromString("00000000-0000-0000-0000-0000000000ff");

    @Autowired private MockMvc mockMvc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtils jwtUtils;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRoleRepository userRoleRepository;
    @Autowired private AssetRepository assetRepository;
    @Autowired private AssetTagRepository assetTagRepository;
    @Autowired private AssetVersionRepository assetVersionRepository;
    @Autowired private VulnerabilityRepository vulnerabilityRepository;
    @Autowired private VulnerabilityTagRepository vulnerabilityTagRepository;
    @Autowired private VulnerabilityAttachmentRepository vulnerabilityAttachmentRepository;
    @Autowired private KnowledgeArticleRepository knowledgeArticleRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private DocumentTagRepository documentTagRepository;
    @Autowired private TeamRepository teamRepository;
    @Autowired private TeamMemberRepository teamMemberRepository;
    @Autowired private TaskRepository taskRepository;
    @Autowired private TaskAttachmentRepository taskAttachmentRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationRecipientRepository notificationRecipientRepository;
    @Autowired private EnvironmentRepository environmentRepository;
    @Autowired private EnvironmentTemplateRepository environmentTemplateRepository;
    @Autowired private EnvironmentSnapshotRepository environmentSnapshotRepository;

    private String adminToken;
    private String engineerToken;
    private UUID adminId;
    private UUID engineerId;

    private Asset assetA;
    private Asset assetB;
    private AssetTag tagOfB;
    private AssetVersion versionOfA;
    private Vulnerability vulnA;
    private Vulnerability vulnB;
    private VulnerabilityTag vulnTagOfB;
    private VulnerabilityAttachment attachmentOfB;
    private KnowledgeArticle article;
    private Document documentA;
    private Document documentB;
    private DocumentTag docTagOfB;
    private Team team;
    private Task task;
    private Notification notification;
    private EnvironmentTemplate template;
    private Environment envA;
    private Environment envB;
    private EnvironmentSnapshot snapshotOfB;

    @BeforeEach
    void setUpUsers() {
        Role adminRole = roleRepository.findByCode("admin").orElseGet(() ->
                roleRepository.save(Objects.requireNonNull(Role.builder()
                        .name("管理员").code("admin").description("管理员").isSystem(true).build())));
        Role engineerRole = roleRepository.findByCode("vulnerability_engineer").orElseGet(() ->
                roleRepository.save(Objects.requireNonNull(Role.builder()
                        .name("工程师").code("vulnerability_engineer").description("工程师").isSystem(false).build())));

        String suffix = String.valueOf(System.nanoTime());
        User admin = userRepository.save(Objects.requireNonNull(User.builder()
                .username("t65_admin_" + suffix)
                .passwordHash(passwordEncoder.encode("T65@123456"))
                .email("t65_admin_" + suffix + "@test.com").fullName("T65管理员").status("active").build()));
        User engineer = userRepository.save(Objects.requireNonNull(User.builder()
                .username("t65_engineer_" + suffix)
                .passwordHash(passwordEncoder.encode("T65@123456"))
                .email("t65_engineer_" + suffix + "@test.com").fullName("T65工程师").status("active").build()));
        adminId = admin.getId();
        engineerId = engineer.getId();
        userRoleRepository.save(Objects.requireNonNull(UserRole.builder()
                .userId(adminId).roleId(adminRole.getId()).assignedBy(adminId).assignedAt(LocalDateTime.now()).build()));
        userRoleRepository.save(Objects.requireNonNull(UserRole.builder()
                .userId(engineerId).roleId(engineerRole.getId()).assignedBy(adminId).assignedAt(LocalDateTime.now()).build()));

        adminToken = "Bearer " + jwtUtils.generateToken(admin.getUsername(),
                List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
        engineerToken = "Bearer " + jwtUtils.generateToken(engineer.getUsername(),
                List.of(new SimpleGrantedAuthority("ROLE_VULNERABILITY_ENGINEER")));
    }

    @BeforeEach
    void setUpSubresources() {
        assetA = assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name("T65-资产A-" + UUID.randomUUID()).code("T65A-" + UUID.randomUUID().toString().substring(0, 8))
                .targetType("web").targetSource("internal").description("A").priority("P2").status("active")
                .ownerId(engineerId).build()));
        assetB = assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name("T65-资产B-" + UUID.randomUUID()).code("T65B-" + UUID.randomUUID().toString().substring(0, 8))
                .targetType("web").targetSource("internal").description("B").priority("P3").status("active")
                .ownerId(engineerId).build()));

        tagOfB = assetTagRepository.save(Objects.requireNonNull(AssetTag.builder()
                .assetId(assetB.getId()).tag("t65-tag-b").createdBy(adminId).build()));
        versionOfA = assetVersionRepository.save(Objects.requireNonNull(AssetVersion.builder()
                .assetId(assetA.getId()).versionNo(1).name("v1").isDefault(true).isLatest(true)
                .status("active").createdBy(engineerId).build()));

        vulnA = vulnerabilityRepository.save(Objects.requireNonNull(Vulnerability.builder()
                .assetId(assetA.getId()).title("T65 漏洞A").type("XSS").severity("MEDIUM")
                .description("a").detail("a").workflowStatus("discovered").customerStatus("pending")
                .disclosureStatus("undisclosed").exploitStage("探测").fieldTestStatus("待测试").vendorFixed(false)
                .vulnerabilityNo("VUL-T65A-" + System.nanoTime()).discovererId(engineerId).build()));
        vulnB = vulnerabilityRepository.save(Objects.requireNonNull(Vulnerability.builder()
                .assetId(assetB.getId()).title("T65 漏洞B").type("SQL").severity("HIGH")
                .description("b").detail("b").workflowStatus("discovered").customerStatus("pending")
                .disclosureStatus("undisclosed").exploitStage("探测").fieldTestStatus("待测试").vendorFixed(false)
                .vulnerabilityNo("VUL-T65B-" + System.nanoTime()).discovererId(engineerId).build()));
        vulnTagOfB = vulnerabilityTagRepository.save(Objects.requireNonNull(VulnerabilityTag.builder()
                .vulnerabilityId(vulnB.getId()).tag("t65-vtag-b").createdBy(adminId).build()));
        attachmentOfB = vulnerabilityAttachmentRepository.save(Objects.requireNonNull(VulnerabilityAttachment.builder()
                .vulnerabilityId(vulnB.getId()).fileName("t65.txt").filePath("./uploads/t65-missing.txt")
                .fileType("text/plain").fileSize(1L).uploadedBy(adminId).build()));

        article = knowledgeArticleRepository.save(Objects.requireNonNull(KnowledgeArticle.builder()
                .title("T65 文章").category("漏洞分析").content("x").authorId(engineerId).status("draft").build()));

        documentA = documentRepository.save(Objects.requireNonNull(Document.builder()
                .title("T65 文档A").category("漏洞报告").content("a").authorId(engineerId)
                .createdBy(engineerId).build()));
        documentB = documentRepository.save(Objects.requireNonNull(Document.builder()
                .title("T65 文档B").category("漏洞报告").content("b").authorId(engineerId)
                .createdBy(engineerId).build()));
        docTagOfB = documentTagRepository.save(Objects.requireNonNull(DocumentTag.builder()
                .documentId(documentB.getId()).tag("t65-dtag-b").createdBy(adminId).build()));

        team = teamRepository.save(Objects.requireNonNull(Team.builder()
                .name("T65 团队-" + UUID.randomUUID()).leaderId(adminId).createdBy(adminId).build()));
        teamMemberRepository.save(Objects.requireNonNull(TeamMember.builder()
                .teamId(team.getId()).userId(engineerId).isLeader(false).build()));

        task = taskRepository.save(Objects.requireNonNull(Task.builder()
                .title("T65 任务").assignedTo(engineerId).assignedBy(adminId).status("pending").build()));

        notification = notificationRepository.save(Objects.requireNonNull(Notification.builder()
                .title("T65 通知").content("c").type("vulnerability_discovered").priority("normal")
                .senderId(adminId).build()));
        notificationRecipientRepository.save(Objects.requireNonNull(NotificationRecipient.builder()
                .notificationId(notification.getId()).userId(engineerId).channel("in_app")
                .sendStatus("sent").isRead(false).build()));

        template = environmentTemplateRepository.save(Objects.requireNonNull(EnvironmentTemplate.builder()
                .name("T65 模板-" + UUID.randomUUID()).type("docker").configSchema("{}").createdBy(adminId).build()));
        envA = environmentRepository.save(Objects.requireNonNull(Environment.builder()
                .name("T65 环境A").assetId(assetA.getId()).templateId(template.getId())
                .config("{}").createdBy(adminId).build()));
        envB = environmentRepository.save(Objects.requireNonNull(Environment.builder()
                .name("T65 环境B").assetId(assetB.getId()).templateId(template.getId())
                .config("{}").createdBy(adminId).build()));
        snapshotOfB = environmentSnapshotRepository.save(Objects.requireNonNull(EnvironmentSnapshot.builder()
                .environmentId(envB.getId()).version(1).configSnapshot("{}").description("b-snap")
                .createdBy(adminId).build()));
    }

    @AfterEach
    void cleanUp() {
        taskAttachmentRepository.deleteAll(taskAttachmentRepository.findByTaskIdOrderByCreatedAtDesc(task.getId()));
        if (attachmentOfB != null) {
            vulnerabilityAttachmentRepository.deleteById(attachmentOfB.getId());
        }
        vulnerabilityTagRepository.deleteAll(vulnerabilityTagRepository.findByVulnerabilityId(vulnA.getId()));
        vulnerabilityTagRepository.deleteAll(vulnerabilityTagRepository.findByVulnerabilityId(vulnB.getId()));
        documentTagRepository.deleteAll(documentTagRepository.findByDocumentId(documentA.getId()));
        documentTagRepository.deleteAll(documentTagRepository.findByDocumentId(documentB.getId()));
        assetTagRepository.deleteAll(assetTagRepository.findByAssetId(assetA.getId()));
        assetTagRepository.deleteAll(assetTagRepository.findByAssetId(assetB.getId()));
        environmentSnapshotRepository.deleteAll(environmentSnapshotRepository.findByEnvironmentIdOrderByVersionDesc(envA.getId()));
        environmentSnapshotRepository.deleteAll(environmentSnapshotRepository.findByEnvironmentIdOrderByVersionDesc(envB.getId()));
        notificationRecipientRepository.deleteAll(notificationRecipientRepository.findByNotificationId(notification.getId()));
        notificationRepository.deleteById(notification.getId());
        teamMemberRepository.deleteAll(teamMemberRepository.findByTeamId(team.getId()));
        teamRepository.deleteById(team.getId());
        taskRepository.deleteById(task.getId());
        knowledgeArticleRepository.deleteById(article.getId());
        documentRepository.deleteById(documentA.getId());
        documentRepository.deleteById(documentB.getId());
        environmentRepository.deleteById(envA.getId());
        environmentRepository.deleteById(envB.getId());
        environmentTemplateRepository.deleteById(template.getId());
        vulnerabilityRepository.deleteById(vulnA.getId());
        vulnerabilityRepository.deleteById(vulnB.getId());
        assetVersionRepository.deleteById(versionOfA.getId());
        assetRepository.deleteById(assetA.getId());
        assetRepository.deleteById(assetB.getId());
        userRoleRepository.deleteAll(userRoleRepository.findByUserId(adminId));
        userRoleRepository.deleteAll(userRoleRepository.findByUserId(engineerId));
        userRepository.deleteById(adminId);
        userRepository.deleteById(engineerId);
    }

    private int status(org.springframework.test.web.servlet.RequestBuilder builder) throws Exception {
        return mockMvc.perform(builder).andReturn().getResponse().getStatus();
    }

    // ------------------------------------------------------------------ 资产标签

    @Test
    @DisplayName("POST /assets/{id}/tags：父资产不存在 → 404；正常 → 201")
    void addAssetTag() throws Exception {
        assertEquals(404, status(post("/api/v1/assets/" + MISSING + "/tags")
                .param("tag", "t65").header("Authorization", adminToken)));
        assertTrue(assetTagRepository.findByAssetId(MISSING).isEmpty(), "不应写入孤儿标签");
        assertEquals(201, status(post("/api/v1/assets/" + assetA.getId() + "/tags")
                .param("tag", "t65-normal").header("Authorization", adminToken)));
    }

    @Test
    @DisplayName("DELETE /assets/{assetId}/tags/{tagId}：标签属于其他资产 → 404；本资产 → 200")
    void removeAssetTag() throws Exception {
        assertEquals(404, status(delete("/api/v1/assets/" + assetA.getId() + "/tags/" + tagOfB.getId())
                .header("Authorization", adminToken)));
        assertTrue(assetTagRepository.findById(tagOfB.getId()).isPresent(), "不应跨资产删除标签");
        assertEquals(200, status(delete("/api/v1/assets/" + assetB.getId() + "/tags/" + tagOfB.getId())
                .header("Authorization", adminToken)));
    }

    // ------------------------------------------------------------------ 资产版本 default/latest

    @Test
    @DisplayName("PUT /assets/{aid}/versions/{vid}/default|latest：版本不存在 → HTTP 404（此前 200+body404）")
    void setAssetVersionFlag() throws Exception {
        assertEquals(404, status(put("/api/v1/assets/" + assetA.getId() + "/versions/" + MISSING + "/default")
                .header("Authorization", adminToken)));
        assertEquals(404, status(put("/api/v1/assets/" + assetA.getId() + "/versions/" + MISSING + "/latest")
                .header("Authorization", adminToken)));
        assertEquals(200, status(put("/api/v1/assets/" + assetA.getId() + "/versions/"
                + versionOfA.getId() + "/default").header("Authorization", adminToken)));
    }

    // ------------------------------------------------------------------ 漏洞标签 / 附件

    @Test
    @DisplayName("POST /vulnerabilities/{id}/tags：父漏洞不存在 → 404；正常 → 201")
    void addVulnerabilityTag() throws Exception {
        assertEquals(404, status(post("/api/v1/vulnerabilities/" + MISSING + "/tags")
                .param("tag", "t65").header("Authorization", adminToken)));
        assertEquals(201, status(post("/api/v1/vulnerabilities/" + vulnA.getId() + "/tags")
                .param("tag", "t65-vtag-a").header("Authorization", adminToken)));
    }

    @Test
    @DisplayName("DELETE /vulnerabilities/{vid}/tags/{tagId}：标签属于其他漏洞 → 404")
    void removeVulnerabilityTag() throws Exception {
        assertEquals(404, status(delete("/api/v1/vulnerabilities/" + vulnA.getId() + "/tags/"
                + vulnTagOfB.getId()).header("Authorization", adminToken)));
        assertTrue(vulnerabilityTagRepository.findById(vulnTagOfB.getId()).isPresent(), "不应跨漏洞删除标签");
    }

    @Test
    @DisplayName("POST /vulnerabilities/{id}/attachments：父漏洞不存在 → 404")
    void uploadVulnerabilityAttachment() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "t65.txt",
                MediaType.TEXT_PLAIN_VALUE, "hello".getBytes());
        assertEquals(404, status(multipart("/api/v1/vulnerabilities/" + MISSING + "/attachments")
                .file(file).header("Authorization", adminToken)));
    }

    @Test
    @DisplayName("DELETE /vulnerabilities/{vid}/attachments/{aid}：附件属于其他漏洞 → 404")
    void deleteVulnerabilityAttachment() throws Exception {
        assertEquals(404, status(delete("/api/v1/vulnerabilities/" + vulnA.getId() + "/attachments/"
                + attachmentOfB.getId()).header("Authorization", adminToken)));
        assertTrue(vulnerabilityAttachmentRepository.findById(attachmentOfB.getId()).isPresent(),
                "不应跨漏洞删除附件");
    }

    // ------------------------------------------------------------------ 知识库评论 / 文档标签

    @Test
    @DisplayName("POST /knowledge/{id}/comments：父文章不存在 → 404；正常 → 200")
    void addComment() throws Exception {
        assertEquals(404, status(post("/api/v1/knowledge/" + MISSING + "/comments")
                .param("content", "t65").header("Authorization", engineerToken)));
        assertEquals(200, status(post("/api/v1/knowledge/" + article.getId() + "/comments")
                .param("content", "t65 正常评论").header("Authorization", engineerToken)));
    }

    @Test
    @DisplayName("DELETE /documents/{docId}/tags/{tagId}：标签属于其他文档 → 404")
    void removeDocumentTag() throws Exception {
        assertEquals(404, status(delete("/api/v1/documents/" + documentA.getId() + "/tags/"
                + docTagOfB.getId()).header("Authorization", adminToken)));
        assertTrue(documentTagRepository.findById(docTagOfB.getId()).isPresent(), "不应跨文档删除标签");
    }

    // ------------------------------------------------------------------ 任务附件

    @Test
    @DisplayName("POST /tasks/{id}/attachments：父任务不存在 → 404；正常 → 200")
    void addTaskAttachment() throws Exception {
        assertEquals(404, status(post("/api/v1/tasks/" + MISSING + "/attachments")
                .param("fileName", "t65.txt").param("filePath", "./uploads/t65.txt")
                .header("Authorization", adminToken)));
        assertEquals(200, status(post("/api/v1/tasks/" + task.getId() + "/attachments")
                .param("fileName", "t65-ok.txt").param("filePath", "./uploads/t65-ok.txt")
                .header("Authorization", adminToken)));
    }

    // ------------------------------------------------------------------ 团队成员

    @Test
    @DisplayName("DELETE /teams/{id}/members/{userId}：团队不存在 / 非成员 → 404；真实成员 → 200")
    void removeTeamMember() throws Exception {
        assertEquals(404, status(delete("/api/v1/teams/" + MISSING + "/members/" + engineerId)
                .header("Authorization", adminToken)), "团队不存在 → 404");
        assertEquals(404, status(delete("/api/v1/teams/" + team.getId() + "/members/" + adminId)
                .header("Authorization", adminToken)), "该用户不是团队成员 → 404");
        assertEquals(200, status(delete("/api/v1/teams/" + team.getId() + "/members/" + engineerId)
                .header("Authorization", adminToken)));
    }

    // ------------------------------------------------------------------ 通知已读 / 删除

    @Test
    @DisplayName("POST /notifications/{id}/read：通知不存在 / 非接收人 → 404；接收人 → 200")
    void markNotificationRead() throws Exception {
        assertEquals(404, status(post("/api/v1/notifications/" + MISSING + "/read")
                .header("Authorization", engineerToken)), "通知不存在 → 404");
        assertEquals(404, status(post("/api/v1/notifications/" + notification.getId() + "/read")
                .header("Authorization", adminToken)), "非接收人 → 404");
        assertEquals(200, status(post("/api/v1/notifications/" + notification.getId() + "/read")
                .header("Authorization", engineerToken)));
    }

    @Test
    @DisplayName("DELETE /notifications/{id}：通知不存在 / 非接收人 → 404")
    void deleteNotification() throws Exception {
        assertEquals(404, status(delete("/api/v1/notifications/" + MISSING)
                .header("Authorization", engineerToken)));
        assertEquals(404, status(delete("/api/v1/notifications/" + notification.getId())
                .header("Authorization", adminToken)));
    }

    // ------------------------------------------------------------------ 环境快照回滚

    @Test
    @DisplayName("POST /environments/{id}/snapshots/{sid}/rollback：快照属于其他环境 → 404（此前 400）")
    void rollbackSnapshot() throws Exception {
        assertEquals(404, status(post("/api/v1/environments/" + envA.getId() + "/snapshots/"
                + snapshotOfB.getId() + "/rollback").header("Authorization", adminToken)));
        assertEquals(404, status(post("/api/v1/environments/" + MISSING + "/snapshots/"
                + snapshotOfB.getId() + "/rollback").header("Authorization", adminToken)), "环境不存在 → 404");
        assertEquals(200, status(post("/api/v1/environments/" + envB.getId() + "/snapshots/"
                + snapshotOfB.getId() + "/rollback").header("Authorization", adminToken)));
    }

    // ------------------------------------------------------------------ 已正确端点（防回归）

    @Test
    @DisplayName("已正确的端点保持 404：工具/文章/文档标签、文档版本回滚、任务提交审核")
    void alreadyCorrectEndpoints() throws Exception {
        assertEquals(404, status(post("/api/v1/tools/" + MISSING + "/tags")
                .param("tag", "t65").header("Authorization", adminToken)), "工具不存在（t55）");
        assertEquals(404, status(post("/api/v1/knowledge/" + MISSING + "/tags")
                .param("tag", "t65").header("Authorization", adminToken)), "文章不存在（t61）");
        assertEquals(404, status(post("/api/v1/documents/" + MISSING + "/tags")
                .param("tag", "t65").header("Authorization", adminToken)), "文档不存在（t61）");
        assertEquals(404, status(post("/api/v1/documents/" + documentA.getId() + "/versions/999/rollback")
                .header("Authorization", engineerToken)), "目标版本不存在（t37）");
        assertEquals(404, status(post("/api/v1/tasks/" + MISSING + "/submit-review")
                .header("Authorization", adminToken)), "任务不存在（t50）");
    }

    @Test
    @DisplayName("查询类端点不受影响：GET 资产/文档标签仍 200")
    void readEndpointsUnaffected() throws Exception {
        assertEquals(200, status(get("/api/v1/assets/" + assetB.getId() + "/tags")
                .header("Authorization", adminToken)));
        assertEquals(200, status(get("/api/v1/documents/" + documentB.getId() + "/tags")
                .header("Authorization", adminToken)));
    }
}
