package com.vulnaudit.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.vulnaudit.entity.UploadApprovalRequest;
import com.vulnaudit.repository.ToolRepository;
import com.vulnaudit.repository.UploadApprovalRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 大文件上传审批（t160）
 *
 * <p>门限在这里被压到 1KB，便于构造「超门限」与「未超门限」两个分支；目录指到 target 下的临时目录，
 * 不污染仓库（也不依赖运行目录）。
 *
 * <p>核心断言：**PENDING 期间业务表零记录**（不是靠过滤隐藏，而是根本没写）→ 拒绝后临时文件消失。
 */
@DisplayName("大文件上传审批测试")
@TestPropertySource(properties = {
        "app.upload.approval-threshold-bytes=1024",
        "app.upload.max-bytes=1048576",
        "app.upload.pending-dir=target/test-uploads/pending",
        "app.upload.approved-dir=target/test-uploads/approved",
        "app.upload.purge-days=7"
})
public class UploadApprovalTest extends BaseTest {

    private static final String SUBMIT = "/api/v1/uploads/pending";
    private static final String ADMIN_LIST = "/api/v1/admin/upload-approvals";

    @Autowired
    private UploadApprovalRequestRepository uploadApprovalRequestRepository;
    @Autowired
    private ToolRepository toolRepository;
    @Autowired
    private com.vulnaudit.repository.AuditLogRepository auditLogRepository;

    @BeforeEach
    void setUpDir() throws Exception {
        Files.createDirectories(Paths.get("target/test-uploads/pending"));
        Files.createDirectories(Paths.get("target/test-uploads/approved"));
    }

    // ------------------------------------------------------------------ 工具

    private MockMultipartFile smallFile(String name) {
        return new MockMultipartFile("file", name, "application/octet-stream",
                "small-content".getBytes(StandardCharsets.UTF_8));
    }

    private MockMultipartFile bigFile(String name) {
        byte[] payload = new byte[2048];
        java.util.Arrays.fill(payload, (byte) 'x');
        return new MockMultipartFile("file", name, "application/octet-stream", payload);
    }

    private JsonNode submit(String token, MockMultipartFile file, String targetType) throws Exception {
        String body = mockMvc.perform(multipart(SUBMIT).file(file)
                        .param("targetType", targetType)
                        .header("Authorization", token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    private Path pendingPath(UploadApprovalRequest request) {
        return Paths.get("target/test-uploads/pending")
                .resolve(Paths.get(request.getStorageKey()).getFileName().toString());
    }

    // ------------------------------------------------------------------ 一、超门限走审批

    @Test
    @DisplayName("超门限：返回 PENDING，业务表零记录，临时文件存在")
    void bigFileGoesToPendingWithoutBusinessRecord() throws Exception {
        long before = toolRepository.count();

        JsonNode data = submit(engineerToken, bigFile("big-tool.zip"), "TOOL");

        assertEquals("PENDING", data.path("status").asText());
        assertNotNull(data.path("requestId").asText());
        assertTrue(data.path("targetId").isNull(), "PENDING 不应有业务实体");
        assertEquals(before, toolRepository.count(), "业务表不能多出记录");

        UploadApprovalRequest request = uploadApprovalRequestRepository
                .findById(UUID.fromString(data.path("requestId").asText())).orElseThrow();
        assertEquals(UploadApprovalRequest.STATUS_PENDING, request.getStatus());
        assertNotNull(request.getPurgeAfter(), "应有清理水位");
        assertTrue(Files.exists(pendingPath(request)), "临时区应存在文件");
    }

    @Test
    @DisplayName("PENDING 不在任何业务列表/下载中可见（GET /tools 无该记录）")
    void pendingInvisibleInBusinessList() throws Exception {
        JsonNode data = submit(engineerToken, bigFile("hidden-tool.zip"), "TOOL");
        String fileName = data.path("fileName").asText();

        String tools = mockMvc.perform(get("/api/v1/tools").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertFalse(tools.contains(fileName), "PENDING 文件不应出现在工具列表: " + tools);

        // 也没有可下载的目标实体
        assertTrue(data.path("targetId").isNull());
    }

    // ------------------------------------------------------------------ 二、未超门限直传

    @Test
    @DisplayName("未超门限：直传成功（status=DIRECT），业务记录立即可见")
    void smallFileDirectUpload() throws Exception {
        JsonNode data = submit(engineerToken, smallFile("small-tool.txt"), "TOOL");

        assertEquals("DIRECT", data.path("status").asText());
        assertNotNull(data.path("targetId").asText(), "直传应返回业务实体 id");
        assertTrue(toolRepository.findById(UUID.fromString(data.path("targetId").asText())).isPresent());
        assertNull(data.path("requestId").textValue(), "直传不应产生审批请求");
    }

    // ------------------------------------------------------------------ 三、批准 / 拒绝

    @Test
    @DisplayName("批准：临时文件移入正式区，业务记录出现，状态 APPROVED")
    void approveMovesFileAndCreatesRecord() throws Exception {
        JsonNode data = submit(engineerToken, bigFile("approve-me.zip"), "TOOL");
        UUID id = UUID.fromString(data.path("requestId").asText());
        UploadApprovalRequest before = uploadApprovalRequestRepository.findById(id).orElseThrow();
        Path pending = pendingPath(before);
        assertTrue(Files.exists(pending));

        String body = mockMvc.perform(post(ADMIN_LIST + "/" + id + "/approve")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode item = objectMapper.readTree(body).path("data");

        assertEquals("APPROVED", item.path("status").asText());
        assertNotNull(item.path("targetId").asText());
        assertFalse(Files.exists(pending), "临时文件应被移走");
        assertTrue(toolRepository.findById(UUID.fromString(item.path("targetId").asText())).isPresent(),
                "批准后应创建业务记录");
        assertTrue(Files.exists(Paths.get("target/test-uploads/approved")
                        .resolve(Paths.get(uploadApprovalRequestRepository.findById(id).orElseThrow()
                                .getStorageKey()).getFileName().toString())),
                "正式区应有文件");
    }

    @Test
    @DisplayName("拒绝：临时文件被删除，状态 REJECTED，业务表仍零记录")
    void rejectDeletesTempFile() throws Exception {
        JsonNode data = submit(engineerToken, bigFile("reject-me.zip"), "TOOL");
        UUID id = UUID.fromString(data.path("requestId").asText());
        UploadApprovalRequest before = uploadApprovalRequestRepository.findById(id).orElseThrow();
        Path pending = pendingPath(before);
        long toolsBefore = toolRepository.count();

        mockMvc.perform(post(ADMIN_LIST + "/" + id + "/reject")
                        .header("Authorization", adminToken).contentType(APPLICATION_JSON)
                        .content("{\"note\":\"体积过大，请拆分\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.reviewNote").value("体积过大，请拆分"));

        assertFalse(Files.exists(pending), "拒绝后临时文件必须删除");
        assertEquals(toolsBefore, toolRepository.count(), "拒绝不应创建业务记录");
    }

    @Test
    @DisplayName("重复审批：已处理的请求再批准/拒绝 → 409")
    void repeatedReviewConflicts() throws Exception {
        JsonNode data = submit(engineerToken, bigFile("twice.zip"), "TOOL");
        UUID id = UUID.fromString(data.path("requestId").asText());
        mockMvc.perform(post(ADMIN_LIST + "/" + id + "/reject").header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        mockMvc.perform(post(ADMIN_LIST + "/" + id + "/approve").header("Authorization", adminToken))
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------ 四、安全

    @Test
    @DisplayName("文件名净化：路径穿越名不落盘到上级目录，展示名被截成 basename")
    void filenameSanitized() throws Exception {
        JsonNode data = submit(engineerToken, bigFile("../../etc/passwd.zip"), "TOOL");
        assertEquals("passwd.zip", data.path("fileName").asText());

        UploadApprovalRequest request = uploadApprovalRequestRepository
                .findById(UUID.fromString(data.path("requestId").asText())).orElseThrow();
        Path pending = pendingPath(request);
        assertTrue(Files.exists(pending));
        assertEquals(Paths.get("target/test-uploads/pending").toAbsolutePath().normalize(),
                pending.getParent().toAbsolutePath().normalize(), "文件必须落在临时区内");
    }

    @Test
    @DisplayName("超单文件上限 → 413；非法 targetType → 400")
    void sizeAndTypeValidation() throws Exception {
        byte[] huge = new byte[1024 * 1024 + 1];
        mockMvc.perform(multipart(SUBMIT)
                        .file(new MockMultipartFile("file", "huge.bin", "application/octet-stream", huge))
                        .param("targetType", "TOOL").header("Authorization", engineerToken))
                .andExpect(status().isPayloadTooLarge());

        mockMvc.perform(multipart(SUBMIT).file(bigFile("x.zip"))
                        .param("targetType", "UNKNOWN").header("Authorization", engineerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("非 ADMIN 不能列表/批准/拒绝 → 403")
    void adminEndpointsRequireAdmin() throws Exception {
        JsonNode data = submit(engineerToken, bigFile("pl-cannot.zip"), "TOOL");
        UUID id = UUID.fromString(data.path("requestId").asText());

        mockMvc.perform(get(ADMIN_LIST).header("Authorization", leaderToken)).andExpect(status().isForbidden());
        mockMvc.perform(post(ADMIN_LIST + "/" + id + "/approve").header("Authorization", leaderToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(ADMIN_LIST + "/" + id + "/reject").header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());

        // 仍然 PENDING（没有被越权处理）
        assertEquals(UploadApprovalRequest.STATUS_PENDING,
                uploadApprovalRequestRepository.findById(id).orElseThrow().getStatus());
    }

    // ------------------------------------------------------------------ 五、申请人隔离

    @Test
    @DisplayName("申请人只能查自己的请求（他人 → 404）")
    void requesterIsolation() throws Exception {
        JsonNode data = submit(engineerToken, bigFile("mine.zip"), "TOOL");
        UUID id = UUID.fromString(data.path("requestId").asText());

        mockMvc.perform(get(SUBMIT + "/" + id).header("Authorization", engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id.toString()));

        mockMvc.perform(get(SUBMIT + "/" + id).header("Authorization", leaderToken))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ 六、过期清理

    @Test
    @DisplayName("过期清理：purge_after 已过 → EXPIRED 且临时文件删除")
    void expiredPendingIsPurged() throws Exception {
        JsonNode data = submit(engineerToken, bigFile("expired.zip"), "TOOL");
        UUID id = UUID.fromString(data.path("requestId").asText());
        UploadApprovalRequest request = uploadApprovalRequestRepository.findById(id).orElseThrow();
        Path pending = pendingPath(request);
        assertTrue(Files.exists(pending));

        // 把 purge_after 拨到过去，模拟过期
        entityManager.createNativeQuery(
                        "update upload_approval_requests set purge_after = :past where id = :id")
                .setParameter("past", LocalDateTime.now().minusDays(1))
                .setParameter("id", id)
                .executeUpdate();
        entityManager.clear();

        int purged = uploadApprovalService.purgeExpired();

        assertTrue(purged >= 1, "应清理到过期请求");
        assertEquals(UploadApprovalRequest.STATUS_EXPIRED,
                uploadApprovalRequestRepository.findById(id).orElseThrow().getStatus());
        assertFalse(Files.exists(pending), "过期后临时文件应删除");
    }

    @Autowired
    private com.vulnaudit.service.UploadApprovalService uploadApprovalService;

    // ------------------------------------------------------------------ 七、审计

    @Test
    @DisplayName("审计：提交/批准/拒绝各 1 条")
    void auditEachStepOnce() throws Exception {
        JsonNode first = submit(engineerToken, bigFile("audit-approve.zip"), "TOOL");
        mockMvc.perform(post(ADMIN_LIST + "/" + first.path("requestId").asText() + "/approve")
                .header("Authorization", adminToken)).andExpect(status().isOk());

        JsonNode second = submit(engineerToken, bigFile("audit-reject.zip"), "TOOL");
        mockMvc.perform(post(ADMIN_LIST + "/" + second.path("requestId").asText() + "/reject")
                        .header("Authorization", adminToken).contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        assertEquals(2, auditLogRepository.findByAction("SUBMIT_UPLOAD_APPROVAL").size(), "两次提交两条");
        assertEquals(1, auditLogRepository.findByAction("APPROVE_UPLOAD").size());
        assertEquals(1, auditLogRepository.findByAction("REJECT_UPLOAD").size());
    }

    @Test
    @DisplayName("管理端列表：按 status 过滤且分页结构正确")
    void adminListFiltersByStatus() throws Exception {
        JsonNode pending = submit(engineerToken, bigFile("list-pending.zip"), "TOOL");
        JsonNode approved = submit(engineerToken, bigFile("list-approved.zip"), "TOOL");
        mockMvc.perform(post(ADMIN_LIST + "/" + approved.path("requestId").asText() + "/approve")
                .header("Authorization", adminToken)).andExpect(status().isOk());

        String body = mockMvc.perform(get(ADMIN_LIST).header("Authorization", adminToken)
                        .param("status", "PENDING").param("size", "100"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode content = objectMapper.readTree(body).path("data").path("content");
        assertTrue(content.size() >= 1);
        for (JsonNode item : content) {
            assertEquals("PENDING", item.path("status").asText());
        }
        boolean sawPendingId = false;
        for (JsonNode item : content) {
            if (item.path("id").asText().equals(pending.path("requestId").asText())) {
                sawPendingId = true;
            }
        }
        assertTrue(sawPendingId, "PENDING 列表应包含刚提交的请求");
    }

    @Test
    @DisplayName("门限自身：approvalThresholdBytes 与配置一致（1024）")
    void thresholdConfigured() {
        assertEquals(1024L, uploadApprovalService.approvalThresholdBytes());
    }

    // ------------------------------------------------------------------ 八、t165：displayName / description 保留与回填

    private JsonNode submitWithMeta(MockMultipartFile file, String displayName, String description) throws Exception {
        var request = multipart(SUBMIT).file(file).param("targetType", "TOOL")
                .header("Authorization", engineerToken);
        if (displayName != null) {
            request = request.param("displayName", displayName);
        }
        if (description != null) {
            request = request.param("description", description);
        }
        String body = mockMvc.perform(request)
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    @Test
    @DisplayName("t165：超门限上传带 displayName/description → PENDING 记录可查（不再丢失）")
    void pendingKeepsDisplayNameAndDescription() throws Exception {
        JsonNode data = submitWithMeta(bigFile("meta-upload.zip"), "我的工具", "这是我上传的工具说明");
        assertEquals("我的工具", data.path("displayName").asText());

        UploadApprovalRequest request = uploadApprovalRequestRepository
                .findById(UUID.fromString(data.path("requestId").asText())).orElseThrow();
        assertEquals("我的工具", request.getDisplayName());
        assertEquals("这是我上传的工具说明", request.getDescription());

        // 申请人查询端点与审批列表都返回这两个字段（增量，不破坏既有契约）
        mockMvc.perform(get(SUBMIT + "/" + request.getId()).header("Authorization", engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("我的工具"))
                .andExpect(jsonPath("$.data.description").value("这是我上传的工具说明"));
    }

    @Test
    @DisplayName("t165：批准后用 displayName 作为工具名（不回落文件名），description 一并落库")
    void approveBackfillsDisplayNameAndDescription() throws Exception {
        JsonNode data = submitWithMeta(bigFile("t165_big_1699999999.zip"), "我的工具", "工具说明 ABC");
        UUID id = UUID.fromString(data.path("requestId").asText());

        String body = mockMvc.perform(post(ADMIN_LIST + "/" + id + "/approve")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        UUID targetId = UUID.fromString(objectMapper.readTree(body).path("data").path("targetId").asText());

        var tool = toolRepository.findById(targetId).orElseThrow();
        assertEquals("我的工具", tool.getName(), "批准后工具名应使用 displayName");
        assertEquals("工具说明 ABC", tool.getDescription(), "description 应落库");
        assertFalse(tool.getName().startsWith("t165_big"), "不应回落到文件名去扩展名");
    }

    @Test
    @DisplayName("t165：不传 displayName/description 时回落行为不变（工具名 = 文件名去扩展名）")
    void approveFallsBackWhenMetaAbsent() throws Exception {
        JsonNode data = submitWithMeta(bigFile("fallback-tool.zip"), null, null);
        UUID id = UUID.fromString(data.path("requestId").asText());

        String body = mockMvc.perform(post(ADMIN_LIST + "/" + id + "/approve")
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var tool = toolRepository.findById(
                UUID.fromString(objectMapper.readTree(body).path("data").path("targetId").asText())).orElseThrow();

        assertEquals("fallback-tool", tool.getName(), "未传显示名时应回落文件名去扩展名");
        assertEquals("经管理员批准的大文件上传", tool.getDescription(), "未传描述时保留默认说明");
    }

    @Test
    @DisplayName("t165：未超门限（DIRECT）也使用 displayName/description")
    void directUploadUsesDisplayName() throws Exception {
        JsonNode data = submitWithMeta(smallFile("small-meta.txt"), "直传工具", "直传说明");
        UUID targetId = UUID.fromString(data.path("targetId").asText());
        var tool = toolRepository.findById(targetId).orElseThrow();
        assertEquals("直传工具", tool.getName());
        assertEquals("直传说明", tool.getDescription());
    }

    @Test
    @DisplayName("t165：displayName 超 200 / description 超 1000 → 400")
    void metaLengthValidation() throws Exception {
        mockMvc.perform(multipart(SUBMIT).file(bigFile("too-long-name.zip"))
                        .param("targetType", "TOOL").param("displayName", "名".repeat(201))
                        .header("Authorization", engineerToken))
                .andExpect(status().isBadRequest());
        mockMvc.perform(multipart(SUBMIT).file(bigFile("too-long-desc.zip"))
                        .param("targetType", "TOOL").param("description", "描".repeat(1001))
                        .header("Authorization", engineerToken))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("t165：V34 脚本守卫（两列 + 幂等 + manual-ddl 等价）")
    void migrationV34Guards() throws Exception {
        String sql = java.nio.file.Files.readString(
                java.nio.file.Paths.get("src/main/resources/db/migration/V34__upload_approval_display_name.sql"),
                java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS display_name VARCHAR(200)"));
        assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS description VARCHAR(1000)"));
        assertTrue(sql.contains("COLLATE utf8mb4_unicode_ci"));
        assertTrue(java.nio.file.Files.readString(java.nio.file.Paths.get("../deploy/manual-ddl.sql"),
                java.nio.charset.StandardCharsets.UTF_8).contains("V34：PENDING"));
    }

    // ------------------------------------------------------------------ 九、t166：我的上传列表 + 撤回

    private JsonNode myList(String token, String query) throws Exception {
        String body = mockMvc.perform(get("/api/v1/uploads/my" + query).header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    @Test
    @DisplayName("t166：我的上传含三态（PENDING/APPROVED/REJECTED），且只含自己提交的")
    void myUploadsContainsOwnThreeStates() throws Exception {
        JsonNode pending = submitWithMeta(bigFile("mine-pending.zip"), "我的待审", null);
        JsonNode approved = submitWithMeta(bigFile("mine-approved.zip"), "我的已批", null);
        JsonNode rejected = submitWithMeta(bigFile("mine-rejected.zip"), "我的被拒", null);
        mockMvc.perform(post(ADMIN_LIST + "/" + approved.path("requestId").asText() + "/approve")
                .header("Authorization", adminToken)).andExpect(status().isOk());
        mockMvc.perform(post(ADMIN_LIST + "/" + rejected.path("requestId").asText() + "/reject")
                .header("Authorization", adminToken).contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());

        JsonNode all = myList(engineerToken, "?status=all&size=50");
        assertTrue(all.path("totalElements").asInt() >= 3, "应有自己提交的 3 条: " + all);

        java.util.Set<String> ids = new java.util.HashSet<>();
        all.path("content").forEach(node -> ids.add(node.path("id").asText()));
        assertTrue(ids.contains(pending.path("requestId").asText()));
        assertTrue(ids.contains(approved.path("requestId").asText()));
        assertTrue(ids.contains(rejected.path("requestId").asText()));

        // APPROVED 项含 targetId；PENDING 项没有
        for (JsonNode node : all.path("content")) {
            if ("APPROVED".equals(node.path("status").asText())) {
                assertFalse(node.path("targetId").isNull(), "APPROVED 应带 targetId");
            }
            if ("PENDING".equals(node.path("status").asText())) {
                assertTrue(node.path("targetId").isNull(), "PENDING 不应有 targetId");
            }
        }
    }

    @Test
    @DisplayName("t166：我的上传不含他人请求（管理员看自己的列表也看不到工程师的）")
    void myUploadsExcludeOthers() throws Exception {
        submitWithMeta(bigFile("engineer-only.zip"), null, null);
        String body = mockMvc.perform(get("/api/v1/uploads/my?status=all").header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode content = objectMapper.readTree(body).path("data").path("content");
        for (JsonNode node : content) {
            assertFalse("engineer-only.zip".equals(node.path("fileName").asText()),
                    "管理员不应看到工程师的上传: " + node);
        }
    }

    @Test
    @DisplayName("t166：status 过滤（PENDING 只返回待审）+ 非法 status → 400")
    void myUploadsStatusFilter() throws Exception {
        JsonNode pending = submitWithMeta(bigFile("filter-pending.zip"), null, null);
        JsonNode approved = submitWithMeta(bigFile("filter-approved.zip"), null, null);
        mockMvc.perform(post(ADMIN_LIST + "/" + approved.path("requestId").asText() + "/approve")
                .header("Authorization", adminToken)).andExpect(status().isOk());

        JsonNode onlyPending = myList(engineerToken, "?status=PENDING&size=50");
        for (JsonNode node : onlyPending.path("content")) {
            assertEquals("PENDING", node.path("status").asText());
        }
        boolean sawPendingId = false;
        for (JsonNode node : onlyPending.path("content")) {
            if (node.path("id").asText().equals(pending.path("requestId").asText())) {
                sawPendingId = true;
            }
        }
        assertTrue(sawPendingId);

        mockMvc.perform(get("/api/v1/uploads/my").header("Authorization", engineerToken)
                        .param("status", "DONE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("t166：分页结构正确（total/content/pageSize 一致）")
    void myUploadsPagination() throws Exception {
        for (int i = 0; i < 5; i++) {
            submitWithMeta(bigFile("page-my-" + i + ".zip"), null, null);
        }
        JsonNode page0 = myList(engineerToken, "?status=all&page=0&size=2");
        JsonNode page1 = myList(engineerToken, "?status=all&page=1&size=2");

        assertTrue(page0.path("totalElements").asInt() >= 5);
        assertEquals(2, page0.path("content").size());
        assertEquals(2, page0.path("pageSize").asInt());
        assertEquals(1, page1.path("currentPage").asInt());
    }

    @Test
    @DisplayName("t166：keyword 命中 fileName 与 displayName")
    void myUploadsKeyword() throws Exception {
        submitWithMeta(bigFile("kw-file-name.zip"), "无关名字", null);
        submitWithMeta(bigFile("other.zip"), "关键字显示名", null);

        JsonNode byFileName = myList(engineerToken, "?status=all&keyword=kw-file");
        assertEquals(1, byFileName.path("totalElements").asInt());
        assertEquals("kw-file-name.zip", byFileName.path("content").get(0).path("fileName").asText());

        JsonNode byDisplayName = myList(engineerToken, "?status=all&keyword=关键字显示名");
        assertEquals(1, byDisplayName.path("totalElements").asInt());
        assertEquals("关键字显示名", byDisplayName.path("content").get(0).path("displayName").asText());
    }

    @Test
    @DisplayName("t166：撤回自己的 PENDING → 临时文件删除且状态 CANCELLED")
    void cancelOwnPending() throws Exception {
        JsonNode data = submitWithMeta(bigFile("cancel-me.zip"), "待撤回", null);
        UUID id = UUID.fromString(data.path("requestId").asText());
        UploadApprovalRequest request = uploadApprovalRequestRepository.findById(id).orElseThrow();
        Path pending = pendingPath(request);
        assertTrue(Files.exists(pending));

        mockMvc.perform(delete("/api/v1/uploads/my/" + id).header("Authorization", engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));

        assertFalse(Files.exists(pending), "撤回后临时文件应删除");
        assertEquals(UploadApprovalRequest.STATUS_CANCELLED,
                uploadApprovalRequestRepository.findById(id).orElseThrow().getStatus());

        // 撤回后管理员也批不了（非 PENDING → 409）
        mockMvc.perform(post(ADMIN_LIST + "/" + id + "/approve").header("Authorization", adminToken))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("t166：撤回他人请求 → 403；撤回已批准 → 409")
    void cancelRules() throws Exception {
        JsonNode mine = submitWithMeta(bigFile("others-cannot.zip"), null, null);
        UUID id = UUID.fromString(mine.path("requestId").asText());

        mockMvc.perform(delete("/api/v1/uploads/my/" + id).header("Authorization", leaderToken))
                .andExpect(status().isForbidden());

        JsonNode approved = submitWithMeta(bigFile("already-approved.zip"), null, null);
        UUID approvedId = UUID.fromString(approved.path("requestId").asText());
        mockMvc.perform(post(ADMIN_LIST + "/" + approvedId + "/approve").header("Authorization", adminToken))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/uploads/my/" + approvedId).header("Authorization", engineerToken))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("t166：管理端列表支持 keyword 与 requestedBy 过滤")
    void adminListFilters() throws Exception {
        submitWithMeta(bigFile("admin-filter-a.zip"), "管理端关键字甲", null);

        String body = mockMvc.perform(get(ADMIN_LIST).header("Authorization", adminToken)
                        .param("keyword", "管理端关键字甲").param("status", "PENDING"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(1, objectMapper.readTree(body).path("data").path("totalElements").asInt());

        String byRequester = mockMvc.perform(get(ADMIN_LIST).header("Authorization", adminToken)
                        .param("requestedBy", engineerUserId.toString()).param("size", "50"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        for (JsonNode node : objectMapper.readTree(byRequester).path("data").path("content")) {
            assertEquals(engineerUserId.toString(), node.path("requester").path("id").asText());
        }

        mockMvc.perform(get(ADMIN_LIST).header("Authorization", adminToken).param("status", "BOGUS"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("t166：未登录访问我的上传/撤回 → 401")
    void myUploadsRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/v1/uploads/my")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/v1/uploads/my/" + UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("t166：撤回不存在的请求 → 404")
    void cancelMissing() throws Exception {
        mockMvc.perform(delete("/api/v1/uploads/my/" + UUID.randomUUID())
                        .header("Authorization", engineerToken))
                .andExpect(status().isNotFound());
    }
}