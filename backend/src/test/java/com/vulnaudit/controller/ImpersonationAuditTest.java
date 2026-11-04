package com.vulnaudit.controller;

import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.AuditLog;
import com.vulnaudit.entity.Team;
import com.vulnaudit.entity.TeamMember;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.AuditLogRepository;
import com.vulnaudit.repository.TeamMemberRepository;
import com.vulnaudit.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 模拟视角审计补全 + 访问申请状态码统一（t93）。
 *
 * <p>背景（t92 实测）：发起/停止此前只留下拦截器生成的
 * {@code POST AdminImpersonationController.impersonate}（细节仅 URI），
 * 事后无法回答「谁在什么时候以什么理由模拟了谁」。
 *
 * <p>本轮口径：
 * <ul>
 *   <li>服务层显式写 {@code IMPERSONATE_START}/{@code IMPERSONATE_STOP}，
 *       details 含 actor / target / reason / ttlSeconds；</li>
 *   <li>控制器加入 {@code AuditTrailInterceptor.SKIP_CONTROLLERS}，一次模拟只产生一条审计；</li>
 *   <li>{@code POST /assets/{id}/access-requests} 对不可见资产由 400 改为 <b>403</b>。</li>
 * </ul>
 */
@DisplayName("模拟视角审计与访问申请状态码测试")
public class ImpersonationAuditTest extends BaseTest {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    private Asset engineerVisibleAsset;
    private Asset hiddenAsset;

    @BeforeEach
    void setUpAssets() {
        String suffix = String.valueOf(System.currentTimeMillis());

        // 工程师可见：他本人负责的资产（团队可不填）
        engineerVisibleAsset = assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name("审计测试可见资产_" + suffix)
                .code("AUD-VIS-" + suffix)
                .targetType("Web应用")
                .targetSource("internal")
                .priority("P1")
                .status("active")
                .ownerId(engineerUserId)
                .build()));

        // 工程师不可见：他人团队（工程师不在成员表里）的资产
        Team otherTeam = teamRepository.save(Objects.requireNonNull(Team.builder()
                .name("审计隔离团队_" + suffix)
                .description("工程师非成员")
                .leaderId(leaderUserId)
                .createdBy(leaderUserId)
                .build()));
        hiddenAsset = assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name("审计测试隐藏资产_" + suffix)
                .code("AUD-HID-" + suffix)
                .targetType("Web应用")
                .targetSource("internal")
                .priority("P1")
                .status("active")
                .ownerId(leaderUserId)
                .teamId(otherTeam.getId())
                .build()));
    }

    private String impersonate(String reason) throws Exception {
        String body = reason == null
                ? "{\"userId\":\"" + engineerUserId + "\"}"
                : "{\"userId\":\"" + engineerUserId + "\",\"reason\":\"" + reason + "\"}";
        MvcResult result = mockMvc.perform(post("/api/v1/admin/impersonate")
                        .header("Authorization", adminToken)
                        .contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn();
        String json = result.getResponse().getContentAsString();
        int idx = json.indexOf("\"token\":\"");
        assertTrue(idx > 0, "响应应包含 token: " + json);
        int start = idx + "\"token\":\"".length();
        return json.substring(start, json.indexOf('"', start));
    }

    private List<AuditLog> impersonateStartLogs() {
        return auditLogRepository.findByAction("IMPERSONATE_START");
    }

    // ------------------------------------------------------------------ 发起审计四要素

    @Test
    @DisplayName("发起：action/resourceType/resourceId 正确，details 含 actor+target+reason+ttlSeconds")
    void startAuditHasAllFourElements() throws Exception {
        impersonate("季度权限审阅");

        List<AuditLog> logs = impersonateStartLogs();
        assertEquals(1, logs.size(), "应恰好 1 条 IMPERSONATE_START");

        AuditLog log = logs.get(0);
        assertEquals("IMPERSONATE_START", log.getAction());
        assertEquals("user", log.getResourceType());
        assertEquals(engineerUserId, log.getResourceId(), "resourceId 必须是目标用户");
        assertEquals(adminUserId, log.getUserId(), "user_id 必须是真实操作者");

        String details = log.getDetails();
        assertTrue(details.contains("actor="), "details 缺 actor: " + details);
        assertTrue(details.contains(adminUserId.toString()), "details 缺 actorId: " + details);
        assertTrue(details.contains("target="), "details 缺 target: " + details);
        assertTrue(details.contains(engineerUserId.toString()), "details 缺 targetId: " + details);
        assertTrue(details.contains("reason=季度权限审阅"), "details 缺 reason: " + details);
        assertTrue(details.contains("ttlSeconds=7200"), "details 缺 ttlSeconds: " + details);
        // 40 字符截断只作用于拦截器生成的 action；服务层 details 远长于 40 且完整落库
        assertTrue(details.length() > 40, "服务层 details 不应被 40 字符截断: " + details.length());
    }

    @Test
    @DisplayName("发起：未填 reason 时 details 仍完整（reason 记为 (未填写)）")
    void startAuditWithoutReason() throws Exception {
        impersonate(null);

        AuditLog log = impersonateStartLogs().get(0);
        assertTrue(log.getDetails().contains("reason="), "缺 reason 键: " + log.getDetails());
        assertTrue(log.getDetails().contains("(未填写)"), "应显式标注未填写: " + log.getDetails());
    }

    @Test
    @DisplayName("一次模拟只产生 1 条审计：不存在拦截器生成的 AdminImpersonationController 行")
    void onlyOneAuditPerImpersonation() throws Exception {
        impersonate("去重校验");

        long controllerRows = auditLogRepository.findAll().stream()
                .filter(log -> log.getAction() != null
                        && log.getAction().contains("AdminImpersonationController"))
                .count();
        assertEquals(0, controllerRows,
                "控制器已加入 SKIP_CONTROLLERS，不应再有拦截器审计行");
        assertEquals(1, impersonateStartLogs().size(), "START 只应有 1 条");
    }

    // ------------------------------------------------------------------ 停止审计

    @Test
    @DisplayName("停止：IMPERSONATE_STOP 含 actor/target，且不含 reason 键")
    void stopAuditElements() throws Exception {
        String token = "Bearer " + impersonate("停止审计");

        mockMvc.perform(post("/api/v1/admin/impersonate/stop").header("Authorization", token))
                .andExpect(status().isNoContent());

        List<AuditLog> stops = auditLogRepository.findByAction("IMPERSONATE_STOP");
        assertEquals(1, stops.size(), "应恰好 1 条 IMPERSONATE_STOP");
        AuditLog log = stops.get(0);
        assertEquals(adminUserId, log.getUserId(), "user_id 必须是真实操作者");
        assertEquals(engineerUserId, log.getResourceId(), "resourceId 必须是目标用户");
        assertTrue(log.getDetails().contains("actor="), "缺 actor: " + log.getDetails());
        assertTrue(log.getDetails().contains("target="), "缺 target: " + log.getDetails());
        assertFalse(log.getDetails().contains("reason="), "STOP 不应含 reason: " + log.getDetails());
        assertEquals(0, auditLogRepository.findAll().stream()
                        .filter(l -> l.getAction() != null && l.getAction().contains("AdminImpersonationController"))
                        .count(),
                "STOP 也不应有拦截器重复行");
    }

    // ------------------------------------------------------------------ 业务写归属仍生效

    @Test
    @DisplayName("模拟期业务写：user_id=目标用户，details 含 impersonatedBy=<adminId>")
    void businessWriteKeepsImpersonatedBy() throws Exception {
        String token = "Bearer " + impersonate("业务写归属");

        mockMvc.perform(post("/api/v1/assets/" + engineerVisibleAsset.getId() + "/tags")
                        .header("Authorization", token).param("tag", "aud-tag-" + System.nanoTime()))
                .andExpect(status().isCreated());

        boolean attributed = auditLogRepository.findAll().stream()
                .anyMatch(log -> "POST AssetController.addAssetTag".equals(log.getAction())
                        && engineerUserId.equals(log.getUserId())
                        && log.getDetails() != null
                        && log.getDetails().contains("impersonatedBy=" + adminUserId));
        assertTrue(attributed, "业务审计应记目标用户 + impersonatedBy=" + adminUserId);
    }

    // ------------------------------------------------------------------ 访问申请状态码统一

    @Test
    @DisplayName("不可见资产的访问申请 → 403（此前 400），且不写入申请")
    void accessRequestOnHiddenAssetForbidden() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/assets/" + hiddenAsset.getId() + "/access-requests")
                        .header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"canViewFiles\":true,\"requestNote\":\"想看\"}"))
                .andExpect(status().isForbidden())
                .andReturn();

        assertTrue(result.getResponse().getContentAsString().contains("无权访问该资产"),
                "应返回统一的权限拒绝文案");
    }

    @Test
    @DisplayName("可见资产的访问申请 → 201（不因新守卫误伤正常流程）")
    void accessRequestOnVisibleAssetCreated() throws Exception {
        mockMvc.perform(post("/api/v1/assets/" + engineerVisibleAsset.getId() + "/access-requests")
                        .header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"canViewFiles\":true,\"requestNote\":\"需要安装包\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("已有授权后重复申请 → 409（不再复用「无权访问」文案）")
    void duplicateAccessRequestAfterApprovalConflicts() throws Exception {
        // 可见性不依赖人工审批：直接插入一条 approved 行，验证重复申请语义
        mockMvc.perform(post("/api/v1/assets/" + engineerVisibleAsset.getId() + "/access-requests")
                        .header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"canViewFiles\":true}"))
                .andExpect(status().isCreated());

        // 第二次（pending）→ 409 CONFLICT
        mockMvc.perform(post("/api/v1/assets/" + engineerVisibleAsset.getId() + "/access-requests")
                        .header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"canViewFiles\":true}"))
                .andExpect(status().isConflict());

        // 资产仍可读（守卫未误判）
        mockMvc.perform(get("/api/v1/assets/" + engineerVisibleAsset.getId())
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("不存在的资产申请访问 → 404（存在性由 404 表达，不用 403 枚举）")
    void accessRequestOnMissingAssetNotFound() throws Exception {
        mockMvc.perform(post("/api/v1/assets/" + UUID.randomUUID() + "/access-requests")
                        .header("Authorization", engineerToken)
                        .contentType(APPLICATION_JSON)
                        .content("{\"canViewFiles\":true}"))
                .andExpect(status().isNotFound());
    }

    // ==================== 事务只读性守卫（t94 线上缺陷回归） ====================

    /**
     * t94 线上实测：t93 的 {@code impersonate()/stop()} 标了 {@code @Transactional(readOnly = true)}，
     * 方法内的审计插入在 Hibernate 的只读会话里被**静默丢弃**（不报错也不落库）；
     * BaseTest 的读写事务会把该标记「吞掉」，所以 9 个用例全绿却漏检。
     *
     * <p>这里直接守住注解语义：这两个方法必须开**读写**事务，否则审计永远写不进去。
     */
    @Test
    @DisplayName("impersonate/stop 必须是读写事务（否则审计被静默丢弃）")
    void impersonationServiceTransactionsMustBeWritable() throws Exception {
        for (String name : new String[]{"impersonate", "stop"}) {
            var method = java.util.Arrays.stream(
                            com.vulnaudit.service.impl.ImpersonationServiceImpl.class.getMethods())
                    .filter(m -> m.getName().equals(name))
                    .findFirst()
                    .orElseThrow();
            Transactional tx = method.getAnnotation(Transactional.class);
            assertTrue(tx != null, name + "() 应带 @Transactional");
            assertFalse(tx.readOnly(), name + "() 必须 readOnly=false（内部要写审计）");
        }
    }
}