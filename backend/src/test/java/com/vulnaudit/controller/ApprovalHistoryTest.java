package com.vulnaudit.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.AssetAccess;
import com.vulnaudit.repository.AssetAccessRepository;
import com.vulnaudit.repository.AssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 审批历史查询（t156）
 *
 * <p>背景：`GET /assets/my/access-approval-requests` 只返回 **pending**，已通过/已拒绝取不到，
 * 前端只能用 localStorage 兜底（换浏览器即丢）。本类覆盖新增的两个只读端点。
 *
 * <p>语义：
 * <ul>
 *   <li>默认 `as=reviewer`（我审批过的：`reviewed_by = 我`）；`as=requester`（我申请过且已被处理的）；</li>
 *   <li>只含 `approved`/`rejected`，**pending 不出现**；默认 `reviewedAt desc`；</li>
 *   <li>只能看与自己相关的记录；资产软删后记录仍可见并标 `assetDeleted=true`；</li>
 *   <li>只读端点**不写审计**。</li>
 * </ul>
 */
@DisplayName("审批历史测试")
public class ApprovalHistoryTest extends BaseTest {

    private static final String HISTORY = "/api/v1/assets/my/access-approval-history";
    private static final String COUNT = HISTORY + "/count";

    @Autowired
    private AssetAccessRepository assetAccessRepository;
    @Autowired
    private AssetRepository assetRepository;

    private Asset asset;

    @BeforeEach
    void setUpFixtures() {
        asset = assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name("审批历史资产")
                .code("APR-" + System.nanoTime())
                .targetType("web")
                .targetSource("internal")
                .priority("P2")
                .status("active")
                .ownerId(adminUserId)
                .build()));
    }

    // ------------------------------------------------------------------ 工具

    /** 造一条「已处理」的申请：reviewedBy=审批人，userId=申请人 */
    private AssetAccess reviewed(UUID applicantId, UUID reviewerId, String status,
                                 LocalDateTime reviewedAt, String reviewNote) {
        AssetAccess access = AssetAccess.builder()
                .assetId(asset.getId())
                .userId(applicantId)
                .status(status)
                .approverId(reviewerId)
                .reviewedBy(reviewerId)
                .reviewedAt(reviewedAt)
                .reviewNote(reviewNote)
                .canViewDocuments(true)
                .canViewEnvironments(false)
                .canViewFiles(true)
                .allowedFileTypes("pdf,docx")
                .requestNote("申请理由")
                .build();
        AssetAccess saved = assetAccessRepository.save(Objects.requireNonNull(access));
        assetAccessRepository.flush();
        return saved;
    }

    private AssetAccess pending(UUID applicantId) {
        AssetAccess access = AssetAccess.builder()
                .assetId(asset.getId())
                .userId(applicantId)
                .status("pending")
                .approverId(adminUserId)
                .build();
        AssetAccess saved = assetAccessRepository.save(Objects.requireNonNull(access));
        assetAccessRepository.flush();
        return saved;
    }

    private JsonNode history(String token, String query) throws Exception {
        String body = mockMvc.perform(get(HISTORY + query).header("Authorization", token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).path("data");
    }

    // ------------------------------------------------------------------ 一、基础语义

    @Test
    @DisplayName("历史只含已处理：pending 不出现，approved/rejected 出现且字段完整")
    void historyContainsOnlyReviewedRecords() throws Exception {
        AssetAccess approved = reviewed(leaderUserId, adminUserId, "approved",
                LocalDateTime.now().minusHours(2), "同意");
        reviewed(leaderUserId, adminUserId, "rejected", LocalDateTime.now().minusHours(1), "资料不足");
        pending(leaderUserId);

        JsonNode data = history(adminToken, "?as=reviewer");
        assertEquals(2, data.path("totalElements").asInt(), "pending 不应出现: " + data);

        JsonNode item = null;
        for (JsonNode node : data.path("content")) {
            if (node.path("id").asText().equals(approved.getId().toString())) {
                item = node;
            }
        }
        assertTrue(item != null, "approved 记录应出现");
        assertEquals(asset.getId().toString(), item.path("assetId").asText());
        assertEquals(asset.getCode(), item.path("assetCode").asText());
        assertEquals(asset.getName(), item.path("assetName").asText());
        assertFalse(item.path("assetDeleted").asBoolean(), "未删资产应 false");
        assertEquals(leaderUserId.toString(), item.path("applicant").path("id").asText());
        assertFalse(item.path("applicant").path("username").asText().isBlank());
        assertEquals(adminUserId.toString(), item.path("reviewer").path("id").asText());
        assertEquals("approved", item.path("status").asText());
        assertEquals("同意", item.path("reviewNote").asText());
        assertFalse(item.path("requestedAt").isNull(), "应有 requestedAt");
        assertFalse(item.path("reviewedAt").isNull(), "应有 reviewedAt");
        assertTrue(item.path("scope").path("canViewDocuments").asBoolean());
        assertTrue(item.path("scope").path("canViewFiles").asBoolean());
        assertEquals("pdf,docx", item.path("scope").path("allowedFileTypes").asText());
    }

    @Test
    @DisplayName("status=approved / rejected 分别过滤；非法 status → 400")
    void statusFilterAndValidation() throws Exception {
        reviewed(leaderUserId, adminUserId, "approved", LocalDateTime.now().minusHours(3), "ok");
        reviewed(leaderUserId, adminUserId, "rejected", LocalDateTime.now().minusHours(2), "no");

        assertEquals(1, history(adminToken, "?status=approved").path("totalElements").asInt());
        assertEquals(1, history(adminToken, "?status=rejected").path("totalElements").asInt());
        assertEquals(2, history(adminToken, "?status=all").path("totalElements").asInt());

        mockMvc.perform(get(HISTORY).header("Authorization", adminToken).param("status", "pending"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(HISTORY).header("Authorization", adminToken).param("status", "whatever"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(HISTORY).header("Authorization", adminToken).param("as", "nobody"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("排序：默认 reviewedAt 倒序（最新处理在前）")
    void defaultSortByReviewedAtDesc() throws Exception {
        reviewed(leaderUserId, adminUserId, "approved", LocalDateTime.now().minusHours(5), "最早");
        reviewed(leaderUserId, adminUserId, "rejected", LocalDateTime.now().minusHours(1), "最新");

        JsonNode content = history(adminToken, "?as=reviewer").path("content");
        assertTrue(content.size() >= 2);
        assertEquals("最新", content.get(0).path("reviewNote").asText(), "第 1 条应是最新处理的");
        assertEquals("最早", content.get(1).path("reviewNote").asText());
    }

    @Test
    @DisplayName("分页：total/content 一致，第 2 页内容正确")
    void paginationCorrect() throws Exception {
        for (int i = 0; i < 5; i++) {
            reviewed(leaderUserId, adminUserId, "approved", LocalDateTime.now().minusMinutes(10L - i), "n" + i);
        }
        JsonNode page0 = history(adminToken, "?as=reviewer&page=0&size=2");
        JsonNode page1 = history(adminToken, "?as=reviewer&page=1&size=2");

        assertEquals(5, page0.path("totalElements").asInt());
        assertEquals(2, page0.path("content").size());
        assertEquals(2, page1.path("content").size());
        assertEquals(1, page1.path("currentPage").asInt());
        assertEquals(page0.path("totalPages").asInt(), 3);
    }

    // ------------------------------------------------------------------ 二、视角与隔离

    @Test
    @DisplayName("隔离：两个审批人互不可见对方的处理记录")
    void reviewersCannotSeeEachOther() throws Exception {
        reviewed(leaderUserId, adminUserId, "approved", LocalDateTime.now().minusHours(1), "admin 处理");
        reviewed(leaderUserId, leaderUserId, "rejected", LocalDateTime.now().minusHours(2), "leader 处理");

        JsonNode adminView = history(adminToken, "?as=reviewer");
        assertEquals(1, adminView.path("totalElements").asInt());
        assertEquals("admin 处理", adminView.path("content").get(0).path("reviewNote").asText());

        JsonNode leaderView = history(leaderToken, "?as=reviewer");
        assertEquals(1, leaderView.path("totalElements").asInt());
        assertEquals("leader 处理", leaderView.path("content").get(0).path("reviewNote").asText());
    }

    @Test
    @DisplayName("as=requester：返回我申请且已被处理的记录（不是审批视角）")
    void requesterPerspective() throws Exception {
        // leader 申请、admin 审批
        reviewed(leaderUserId, adminUserId, "approved", LocalDateTime.now().minusHours(1), "通过");
        // admin 自己申请、leader 审批（不应出现在 admin 的 requester 视角以外的地方）
        reviewed(adminUserId, leaderUserId, "rejected", LocalDateTime.now().minusHours(2), "驳回");

        JsonNode asRequester = history(leaderToken, "?as=requester");
        assertEquals(1, asRequester.path("totalElements").asInt(), "leader 作为申请人只有 1 条: " + asRequester);
        assertEquals(leaderUserId.toString(), asRequester.path("content").get(0).path("applicant").path("id").asText());

        JsonNode asReviewer = history(leaderToken, "?as=reviewer");
        assertEquals(1, asReviewer.path("totalElements").asInt(), "leader 作为审批人也只有 1 条");
        assertEquals(adminUserId.toString(), asReviewer.path("content").get(0).path("applicant").path("id").asText());
    }

    // ------------------------------------------------------------------ 三、软删除口径

    @Test
    @DisplayName("资产软删后：记录仍可见且 assetDeleted=true（历史不随资产消失）")
    void softDeletedAssetStillVisible() throws Exception {
        reviewed(leaderUserId, adminUserId, "approved", LocalDateTime.now().minusHours(1), "历史需留存");

        entityManager.createNativeQuery("update assets set deleted_at = now() where id = :id")
                .setParameter("id", asset.getId()).executeUpdate();
        entityManager.clear();

        JsonNode data = history(adminToken, "?as=reviewer");
        assertEquals(1, data.path("totalElements").asInt(), "资产软删不应让历史消失");
        assertTrue(data.path("content").get(0).path("assetDeleted").asBoolean(), "应标注 assetDeleted=true");
        assertEquals(asset.getCode(), data.path("content").get(0).path("assetCode").asText(),
                "仍应给出资产编号（历史可读）");
    }

    @Test
    @DisplayName("筛选：assetId 与 keyword（含已软删资产）")
    void assetIdAndKeywordFilters() throws Exception {
        reviewed(leaderUserId, adminUserId, "approved", LocalDateTime.now().minusHours(1), "命中");
        Asset other = assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name("另一个资产")
                .code("APR-OTHER-" + System.nanoTime())
                .targetType("web").targetSource("internal").priority("P2").status("active")
                .ownerId(adminUserId).build()));
        assetAccessRepository.saveAndFlush(Objects.requireNonNull(AssetAccess.builder()
                .assetId(other.getId()).userId(leaderUserId).status("rejected")
                .reviewedBy(adminUserId).reviewedAt(LocalDateTime.now().minusHours(2))
                .build()));

        assertEquals(1, history(adminToken, "?as=reviewer&assetId=" + asset.getId())
                .path("totalElements").asInt());
        assertEquals(1, history(adminToken, "?as=reviewer&keyword=" + asset.getName())
                .path("totalElements").asInt());
        assertEquals(0, history(adminToken, "?as=reviewer&keyword=不存在的关键字xyz")
                .path("totalElements").asInt());
    }

    // ------------------------------------------------------------------ 四、计数端点

    @Test
    @DisplayName("count：与列表一致（approved/rejected 分别计数）")
    void countMatchesList() throws Exception {
        reviewed(leaderUserId, adminUserId, "approved", LocalDateTime.now().minusHours(1), "a1");
        reviewed(leaderUserId, adminUserId, "approved", LocalDateTime.now().minusHours(2), "a2");
        reviewed(leaderUserId, adminUserId, "rejected", LocalDateTime.now().minusHours(3), "r1");
        pending(leaderUserId);

        String body = mockMvc.perform(get(COUNT).header("Authorization", adminToken).param("as", "reviewer"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).path("data");
        assertEquals(2, data.path("approved").asInt());
        assertEquals(1, data.path("rejected").asInt());

        assertEquals(2, history(adminToken, "?as=reviewer&status=approved").path("totalElements").asInt());
        assertEquals(1, history(adminToken, "?as=reviewer&status=rejected").path("totalElements").asInt());
    }

    @Test
    @DisplayName("count：as=requester 语义正确，非法 as → 400")
    void countRequesterAndValidation() throws Exception {
        reviewed(leaderUserId, adminUserId, "approved", LocalDateTime.now().minusHours(1), "x");
        reviewed(adminUserId, leaderUserId, "approved", LocalDateTime.now().minusHours(2), "y");

        String leaderRequester = mockMvc.perform(get(COUNT).header("Authorization", leaderToken)
                        .param("as", "requester"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(1, objectMapper.readTree(leaderRequester).path("data").path("approved").asInt());

        mockMvc.perform(get(COUNT).header("Authorization", adminToken).param("as", "boss"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ 五、安全与审计

    @Test
    @DisplayName("未登录 → 401；只读端点不写审计")
    void requiresLoginAndWritesNoAudit() throws Exception {
        mockMvc.perform(get(HISTORY)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(COUNT)).andExpect(status().isUnauthorized());

        long before = auditLogRepository.count();
        reviewed(leaderUserId, adminUserId, "approved", LocalDateTime.now().minusHours(1), "只读");
        auditLogRepository.flush();
        long mid = auditLogRepository.count();
        history(adminToken, "?as=reviewer");
        mockMvc.perform(get(COUNT).header("Authorization", adminToken));
        assertEquals(mid, auditLogRepository.count(), "只读端点不应写审计");
        assertTrue(mid >= before, "夹具写入不应减少审计行数");
    }

    @Autowired
    private com.vulnaudit.repository.AuditLogRepository auditLogRepository;
}
