package com.vulnaudit.controller;

import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.KnowledgeArticle;
import com.vulnaudit.entity.Notification;
import com.vulnaudit.entity.NotificationRecipient;
import com.vulnaudit.entity.Task;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.KnowledgeArticleRepository;
import com.vulnaudit.repository.NotificationRecipientRepository;
import com.vulnaudit.repository.NotificationRepository;
import com.vulnaudit.repository.TaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * 协作域（知识库 / 通知 / 任务）鉴权测试（t50）
 *
 * <p>对应验收报告 §2.3.2 剩余缺口的 4 组收口：
 * <ol>
 *     <li>{@code DELETE /knowledge/{id}}：管理层或作者本人；他人文章 403；</li>
 *     <li>{@code POST /notifications/vulnerability-discovered}：限管理层（工程师 403）；</li>
 *     <li>{@code GET /notifications/{id}/recipients}：接收人本人或 admin/project_leader，其余 403；</li>
 *     <li>{@code POST /tasks} 限管理层 + 资产写权；{@code POST /tasks/{id}/submit-review} 负责人/协作者或管理层。</li>
 * </ol>
 */
@DisplayName("协作域鉴权测试")
class CollaborationDomainAuthorizationTest extends BaseTest {

    @Autowired
    private KnowledgeArticleRepository knowledgeArticleRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private NotificationRecipientRepository notificationRecipientRepository;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private AssetRepository assetRepository;

    private KnowledgeArticle adminArticle;
    private KnowledgeArticle engineerArticle;
    private Notification notificationToEngineer;
    private Task taskOfEngineer;
    private Asset leaderAsset;

    @BeforeEach
    void setUpCollaborationData() {
        adminArticle = knowledgeArticleRepository.save(Objects.requireNonNull(KnowledgeArticle.builder()
                .title("管理员文章-" + UUID.randomUUID())
                .category("漏洞分析")
                .content("管理员撰写的内容")
                .authorId(adminUserId)
                .status("published")
                .build()));

        engineerArticle = knowledgeArticleRepository.save(Objects.requireNonNull(KnowledgeArticle.builder()
                .title("工程师文章-" + UUID.randomUUID())
                .category("漏洞分析")
                .content("工程师撰写的内容")
                .authorId(engineerUserId)
                .status("draft")
                .build()));

        notificationToEngineer = notificationRepository.save(Objects.requireNonNull(Notification.builder()
                .title("漏洞发现通知")
                .content("有新的漏洞被提交")
                .type("vulnerability_discovered")
                .priority("normal")
                .senderId(adminUserId)
                .build()));
        notificationRecipientRepository.save(Objects.requireNonNull(NotificationRecipient.builder()
                .notificationId(notificationToEngineer.getId())
                .userId(engineerUserId)
                .channel("in_app")
                .sendStatus("sent")
                .isRead(false)
                .build()));

        // 工程师被指派的任务（assignedBy=admin）；审计结论由业务规则要求先填写
        taskOfEngineer = taskRepository.save(Objects.requireNonNull(Task.builder()
                .title("工程师负责的审计任务")
                .description("t50 鉴权测试")
                .assignedTo(engineerUserId)
                .assignedBy(adminUserId)
                .status("pending")
                .auditConclusion("审计已完成，待审核")
                .build()));

        // 团队长自己的资产（用于「团队范围内建任务 200」）
        leaderAsset = assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name("团队长资产-" + UUID.randomUUID())
                .code("COL-" + UUID.randomUUID().toString().substring(0, 8))
                .targetType("web").targetSource("internal").description("团队长负责")
                .priority("P2").status("active").ownerId(leaderUserId)
                .build()));
    }

    private Map<String, Object> taskBody(String title, UUID assetId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", title);
        if (assetId != null) {
            body.put("assetId", assetId.toString());
        }
        return body;
    }

    // ------------------------------------------------------------------ #7 知识库删除

    @Test
    @DisplayName("#7 工程师删除他人文章 - 403")
    void engineerDeleteOthersArticle_forbidden() throws Exception {
        assertEquals(403, mockMvc.perform(delete("/api/v1/knowledge/" + adminArticle.getId())
                        .header("Authorization", engineerToken))
                .andReturn().getResponse().getStatus());
        assertTrue(knowledgeArticleRepository.findById(adminArticle.getId()).isPresent(), "他人文章不应被删除");
    }

    @Test
    @DisplayName("#7 工程师删除自己的文章 - 200（作者校验，见 AccessGuard.canDeleteKnowledge 注释）")
    void engineerDeleteOwnArticle_allowed() throws Exception {
        assertEquals(200, mockMvc.perform(delete("/api/v1/knowledge/" + engineerArticle.getId())
                        .header("Authorization", engineerToken))
                .andReturn().getResponse().getStatus());
        assertTrue(knowledgeArticleRepository.findById(engineerArticle.getId()).isEmpty(), "自己的文章应被删除");
    }

    @Test
    @DisplayName("#7 管理层删除他人文章 - 200")
    void managementDeleteOthersArticle_allowed() throws Exception {
        assertEquals(200, mockMvc.perform(delete("/api/v1/knowledge/" + engineerArticle.getId())
                        .header("Authorization", leaderToken))
                .andReturn().getResponse().getStatus());

        KnowledgeArticle another = knowledgeArticleRepository.save(Objects.requireNonNull(KnowledgeArticle.builder()
                .title("再来一篇-" + UUID.randomUUID())
                .category("漏洞分析").content("x").authorId(engineerUserId).status("draft").build()));
        assertEquals(200, mockMvc.perform(delete("/api/v1/knowledge/" + another.getId())
                        .header("Authorization", adminToken))
                .andReturn().getResponse().getStatus());
    }

    @Test
    @DisplayName("#7 PUT 知识文章对全角色保持开放（矩阵允许协作编辑，不改动）")
    void updateArticle_stillOpen() throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "工程师协作编辑的标题");
        body.put("content", "协作内容");
        assertEquals(200, mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/knowledge/" + adminArticle.getId())
                        .header("Authorization", engineerToken)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(body))))
                .andReturn().getResponse().getStatus());
    }

    // ------------------------------------------------------------------ #8 漏洞通知

    @Test
    @DisplayName("#8 工程师调用 vulnerability-discovered - 403")
    void engineerSendVulnerabilityNotification_forbidden() throws Exception {
        UUID vulnerabilityId = UUID.randomUUID();
        assertEquals(403, mockMvc.perform(post("/api/v1/notifications/vulnerability-discovered")
                        .param("vulnerabilityId", vulnerabilityId.toString())
                        .param("recipientIds", engineerUserId.toString())
                        .header("Authorization", engineerToken))
                .andReturn().getResponse().getStatus());
    }

    @Test
    @DisplayName("#8 管理层（team_leader）调用 vulnerability-discovered - 200")
    void managementSendVulnerabilityNotification_allowed() throws Exception {
        UUID vulnerabilityId = UUID.randomUUID();
        assertEquals(200, mockMvc.perform(post("/api/v1/notifications/vulnerability-discovered")
                        .param("vulnerabilityId", vulnerabilityId.toString())
                        .param("recipientIds", engineerUserId.toString())
                        .header("Authorization", leaderToken))
                .andReturn().getResponse().getStatus());
    }

    // ------------------------------------------------------------------ #9 投递记录

    @Test
    @DisplayName("#9 工程师读他人通知投递记录 - 403")
    void engineerReadOthersRecipients_forbidden() throws Exception {
        assertEquals(403, mockMvc.perform(get("/api/v1/notifications/" + notificationToEngineer.getId() + "/recipients")
                        .header("Authorization", leaderToken))
                .andReturn().getResponse().getStatus(), "非接收人（组长）应 403");
    }

    @Test
    @DisplayName("#9 接收人本人可读自己的投递记录 - 200")
    void recipientReadsOwnRecipients_allowed() throws Exception {
        var result = mockMvc.perform(get("/api/v1/notifications/" + notificationToEngineer.getId() + "/recipients")
                        .header("Authorization", engineerToken))
                .andReturn();
        assertEquals(200, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentAsString(StandardCharsets.UTF_8).contains(engineerUserId.toString()),
                "响应应包含接收人记录");
    }

    @Test
    @DisplayName("#9 管理员读任意通知投递记录 - 200")
    void adminReadsAnyRecipients_allowed() throws Exception {
        assertEquals(200, mockMvc.perform(get("/api/v1/notifications/" + notificationToEngineer.getId() + "/recipients")
                        .header("Authorization", adminToken))
                .andReturn().getResponse().getStatus());
    }

    // ------------------------------------------------------------------ #10 任务创建与提交审核

    @Test
    @DisplayName("#10 工程师创建任务 - 403（矩阵 2.9：创建限管理层）")
    void engineerCreateTask_forbidden() throws Exception {
        assertEquals(403, mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", engineerToken)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(
                                taskBody("工程师想建的任务", null)))))
                .andReturn().getResponse().getStatus());
    }

    @Test
    @DisplayName("#10 team_leader 在自有资产上创建任务 - 201")
    void teamLeaderCreateTaskOnOwnAsset_allowed() throws Exception {
        var result = mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", leaderToken)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(
                                taskBody("组长建的任务", leaderAsset.getId())))))
                .andReturn();
        assertEquals(201, result.getResponse().getStatus());
        assertNotNull(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("#10 team_leader 在无权资产上创建任务 - 403（资产写权校验）")
    void teamLeaderCreateTaskOnForeignAsset_forbidden() throws Exception {
        Asset foreign = assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name("他人资产-" + UUID.randomUUID())
                .code("COLX-" + UUID.randomUUID().toString().substring(0, 8))
                .targetType("web").targetSource("internal").description("admin 负责")
                .priority("P3").status("active").ownerId(adminUserId)
                .build()));

        assertEquals(403, mockMvc.perform(post("/api/v1/tasks")
                        .header("Authorization", leaderToken)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(
                                taskBody("越界建任务", foreign.getId())))))
                .andReturn().getResponse().getStatus());
    }

    @Test
    @DisplayName("#10 非负责人（且非管理层、非协作者）提交审核 - 403")
    void nonAssigneeSubmitReview_forbidden() throws Exception {
        // 另建一个普通工程师：既非负责人、非协作者，也无管理层角色
        com.vulnaudit.entity.User bystander = userRepository.save(Objects.requireNonNull(com.vulnaudit.entity.User.builder()
                .username("bystander_" + System.nanoTime())
                .passwordHash(passwordEncoder.encode("Bystander@123456"))
                .email("bystander_" + System.nanoTime() + "@test.com")
                .fullName("无关工程师")
                .status("active")
                .build()));
        String bystanderToken = Objects.requireNonNull(
                generateTestToken(bystander.getUsername(), "ROLE_VULNERABILITY_ENGINEER"));

        assertEquals(403, mockMvc.perform(post("/api/v1/tasks/" + taskOfEngineer.getId() + "/submit-review")
                        .header("Authorization", bystanderToken))
                .andReturn().getResponse().getStatus(), "非负责人/非协作者/非管理层 → 403");
        assertEquals("pending", taskRepository.findById(taskOfEngineer.getId()).orElseThrow().getStatus(),
                "越权提交不应改变任务状态");
    }

    @Test
    @DisplayName("#10 负责人提交审核 - 200")
    void assigneeSubmitReview_allowed() throws Exception {
        assertEquals(200, mockMvc.perform(post("/api/v1/tasks/" + taskOfEngineer.getId() + "/submit-review")
                        .header("Authorization", engineerToken))
                .andReturn().getResponse().getStatus());
    }

    @Test
    @DisplayName("#10 管理层（team_leader）代办提交审核 - 200")
    void managementSubmitReview_allowed() throws Exception {
        assertEquals(200, mockMvc.perform(post("/api/v1/tasks/" + taskOfEngineer.getId() + "/submit-review")
                        .header("Authorization", leaderToken))
                .andReturn().getResponse().getStatus());

        // 重置为 pending 后再由 admin 代办一次
        Task task = taskRepository.findById(taskOfEngineer.getId()).orElseThrow();
        task.setStatus("pending");
        taskRepository.save(task);

        assertEquals(200, mockMvc.perform(post("/api/v1/tasks/" + taskOfEngineer.getId() + "/submit-review")
                        .header("Authorization", adminToken))
                .andReturn().getResponse().getStatus());
    }
}
