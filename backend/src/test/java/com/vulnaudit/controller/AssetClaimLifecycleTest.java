package com.vulnaudit.controller;

import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.AssetClaim;
import com.vulnaudit.entity.User;
import com.vulnaudit.repository.AssetClaimRepository;
import com.vulnaudit.repository.AssetRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 资产认领生命周期测试（t39）
 *
 * <p>背景：控制器把 userId 硬编码为 null 传给 completeClaim/abandonClaim，
 * 服务层「必须是认领人」校验必然失败 → 两个端点恒 403，认领状态机只能走到「申请 → 审批」。
 *
 * <p>修复后的语义：
 * <ul>
 *     <li>complete：仅认领人本人可完成（管理层不代办），状态必须 approved，否则 400；非本人 403；不存在 404；</li>
 *     <li>abandon：本人可放弃；管理层（ADMIN/PROJECT_LEADER/TEAM_LEADER）可强制放弃并记录操作人；
 *         状态必须 approved；其余身份 403；不存在 404。</li>
 * </ul>
 */
@DisplayName("资产认领生命周期测试")
class AssetClaimLifecycleTest extends BaseTest {

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private AssetClaimRepository assetClaimRepository;

    private Asset asset;
    private UUID claimId;
    private String otherEngineerToken;
    private UUID otherEngineerId;

    @BeforeEach
    void setUpClaimData() throws Exception {
        asset = assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name("认领测试资产-" + UUID.randomUUID())
                .code("CLAIM-" + UUID.randomUUID().toString().substring(0, 8))
                .targetType("web")
                .targetSource("internal")
                .description("认领生命周期测试")
                .priority("P2")
                .status("active")
                .ownerId(engineerUserId)
                .build()));

        // 第三个工程师（无管理层角色）：用于验证「非认领人且非管理层 → 403」
        User other = userRepository.save(Objects.requireNonNull(User.builder()
                .username("other_engineer_" + System.nanoTime())
                .passwordHash(passwordEncoder.encode("Other@123456"))
                .email("other_" + System.nanoTime() + "@test.com")
                .fullName("其他工程师")
                .status("active")
                .build()));
        otherEngineerId = other.getId();
        otherEngineerToken = Objects.requireNonNull(
                generateTestToken(other.getUsername(), "ROLE_VULNERABILITY_ENGINEER"));

        // 工程师发起认领申请
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("note", "我来认领该资产");
        var created = mockMvc.perform(post("/api/v1/assets/" + asset.getId() + "/claims")
                        .header("Authorization", engineerToken)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(body))))
                .andExpect(status().isCreated())
                .andReturn();
        claimId = UUID.fromString(extractId(created.getResponse().getContentAsString(
                java.nio.charset.StandardCharsets.UTF_8)));
    }

    private void approve() throws Exception {
        Map<String, Object> review = new LinkedHashMap<>();
        review.put("approved", true);
        review.put("reviewNote", "同意认领");
        mockMvc.perform(post("/api/v1/assets/claims/" + claimId + "/review")
                        .header("Authorization", adminToken)
                        .contentType(Objects.requireNonNull(MediaType.APPLICATION_JSON))
                        .content(Objects.requireNonNull(objectMapper.writeValueAsString(review))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("approved"));
    }

    private String extractId(String body) {
        String marker = "\"id\":\"";
        int start = body.indexOf(marker);
        if (start < 0) {
            throw new AssertionError("响应中未找到 id: " + body);
        }
        int begin = start + marker.length();
        return body.substring(begin, body.indexOf('"', begin));
    }

    private int complete(String token, UUID id) throws Exception {
        return mockMvc.perform(post("/api/v1/assets/claims/" + id + "/complete")
                        .header("Authorization", token))
                .andReturn().getResponse().getStatus();
    }

    private int abandon(String token, UUID id, String note) throws Exception {
        var request = post("/api/v1/assets/claims/" + id + "/abandon").header("Authorization", token);
        if (note != null) {
            // 用 param 交给 MockMvc 编码/解码，避免手工拼接 query 带来的百分号编码问题
            request = request.param("note", note);
        }
        return mockMvc.perform(request).andReturn().getResponse().getStatus();
    }

    @Test
    @DisplayName("认领人完成 - 200，状态 completed 且 completedAt 有值")
    void ownerComplete_success() throws Exception {
        approve();

        assertEquals(200, complete(engineerToken, claimId));

        AssetClaim claim = assetClaimRepository.findById(claimId).orElseThrow();
        assertEquals("completed", claim.getStatus());
        assertNotNull(claim.getCompletedAt(), "completedAt 应被写入");
    }

    @Test
    @DisplayName("非认领人完成 - 403（管理员也不例外，完成动作仅限本人）")
    void nonOwnerComplete_forbidden() throws Exception {
        approve();

        assertEquals(403, complete(otherEngineerToken, claimId));
        assertEquals(403, complete(adminToken, claimId), "管理员不代办完成（SRS 语义）");
        assertEquals(403, complete(leaderToken, claimId));

        AssetClaim claim = assetClaimRepository.findById(claimId).orElseThrow();
        assertEquals("approved", claim.getStatus(), "越权完成不应改变状态");
    }

    @Test
    @DisplayName("非法状态完成 - 400（pending 不可完成）")
    void completePending_badRequest() throws Exception {
        // 未审批 → 状态仍为 pending
        assertEquals("pending", assetClaimRepository.findById(claimId).orElseThrow().getStatus());

        assertEquals(400, complete(engineerToken, claimId));
        assertEquals("pending", assetClaimRepository.findById(claimId).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("认领人放弃 - 200，状态 abandoned 且备注保留")
    void ownerAbandon_success() throws Exception {
        approve();

        assertEquals(200, abandon(engineerToken, claimId, "项目调整，放弃认领"));

        AssetClaim claim = assetClaimRepository.findById(claimId).orElseThrow();
        assertEquals("abandoned", claim.getStatus());
        assertEquals("项目调整，放弃认领", claim.getReviewNote());
        // 本人放弃不改写审批人（reviewedBy 仍为审批该申请的 admin）
        assertEquals(adminUserId, claim.getReviewedBy(), "本人放弃不应覆盖审批人");
    }

    @Test
    @DisplayName("非认领人且非管理层放弃 - 403")
    void nonOwnerNonManagementAbandon_forbidden() throws Exception {
        approve();

        assertEquals(403, abandon(otherEngineerToken, claimId, "抢着放弃"));
        assertEquals("approved", assetClaimRepository.findById(claimId).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("管理层强制放弃 - 200，状态 abandoned 且记录操作人")
    void managementForcedAbandon_success() throws Exception {
        approve();

        assertEquals(200, abandon(adminToken, claimId, "认领人离职"));

        AssetClaim claim = assetClaimRepository.findById(claimId).orElseThrow();
        assertEquals("abandoned", claim.getStatus());
        assertEquals(adminUserId, claim.getReviewedBy(), "强制放弃必须记录操作人");
        assertNotNull(claim.getReviewedAt());
        org.junit.jupiter.api.Assertions.assertTrue(
                claim.getReviewNote() != null && claim.getReviewNote().contains("强制放弃"),
                "备注应标注强制放弃: " + claim.getReviewNote());
        org.junit.jupiter.api.Assertions.assertTrue(
                claim.getReviewNote().contains(adminUserId.toString()),
                "备注应包含操作人ID: " + claim.getReviewNote());
    }

    @Test
    @DisplayName("team_leader 也可强制放弃 - 200")
    void teamLeaderForcedAbandon_success() throws Exception {
        approve();

        assertEquals(200, abandon(leaderToken, claimId, "组长代放弃"));

        AssetClaim claim = assetClaimRepository.findById(claimId).orElseThrow();
        assertEquals("abandoned", claim.getStatus());
        assertEquals(leaderUserId, claim.getReviewedBy());
    }

    @Test
    @DisplayName("非法状态放弃 - 400（pending 不可放弃）")
    void abandonPending_badRequest() throws Exception {
        assertEquals(400, abandon(engineerToken, claimId, null));
        assertEquals("pending", assetClaimRepository.findById(claimId).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("认领记录不存在 - 404（complete 与 abandon）")
    void missingClaim_notFound() throws Exception {
        UUID missing = UUID.randomUUID();
        assertEquals(404, complete(engineerToken, missing));
        assertEquals(404, abandon(adminToken, missing, null));
    }

    @Test
    @DisplayName("生命周期串联 - 申请(pending) → 审批(approved) → 完成(completed)")
    void fullLifecycle_pendingToCompleted() throws Exception {
        assertEquals("pending", assetClaimRepository.findById(claimId).orElseThrow().getStatus());

        approve();
        assertEquals("approved", assetClaimRepository.findById(claimId).orElseThrow().getStatus());

        mockMvc.perform(post("/api/v1/assets/claims/" + claimId + "/complete")
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        assertEquals("completed", assetClaimRepository.findById(claimId).orElseThrow().getStatus());
        // 「我的认领」应能看到该记录
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/assets/my/claims")
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].id").value(claimId.toString()))
                .andExpect(jsonPath("$.data.content[0].status").value("completed"));
    }

    @Test
    @DisplayName("无权访问他人认领备注 - 断言 reviewNote 含强制标记时不影响普通用户读取自己的列表")
    void otherEngineerCannotSeeClaimInMyList() throws Exception {
        approve();
        assertEquals(200, complete(engineerToken, claimId));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/v1/assets/my/claims")
                        .header("Authorization", otherEngineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("认领备注为空时仍可放弃（note 可选）")
    void abandonWithoutNote_success() throws Exception {
        approve();
        assertEquals(200, abandon(engineerToken, claimId, null));
        assertEquals("abandoned", assetClaimRepository.findById(claimId).orElseThrow().getStatus());
    }

    @Test
    @DisplayName("强制放弃的备注包含原 note 内容")
    void forcedAbandon_keepsOriginalNote() throws Exception {
        approve();
        assertEquals(200, abandon(adminToken, claimId, "客户要求回收"));

        AssetClaim claim = assetClaimRepository.findById(claimId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertTrue(claim.getReviewNote() != null
                        && claim.getReviewNote().contains("客户要求回收"),
                "强制放弃备注应保留原 note: " + claim.getReviewNote());
    }
}
