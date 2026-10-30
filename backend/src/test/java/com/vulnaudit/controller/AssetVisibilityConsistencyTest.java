package com.vulnaudit.controller;

import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.Team;
import com.vulnaudit.entity.TeamMember;
import com.vulnaudit.entity.Vulnerability;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.TeamMemberRepository;
import com.vulnaudit.repository.TeamRepository;
import com.vulnaudit.repository.VulnerabilityRepository;
import com.vulnaudit.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * 资产可见性一致性（不变量）测试（t46）
 *
 * <p>背景：`GET /assets` 列表此前完全不做范围过滤，而 `GET /assets/{id}` 走
 * {@code AccessGuard.canReadAsset}，导致「列表里能看到的资产，点进去 403」。
 *
 * <p>修复：可见性收敛为单一事实来源 {@code AccessGuard.visibleAssetIds(auth)}——
 * 列表用它过滤，详情用它做成员判定（{@code canReadAsset}），写权限以读为前提（写 ⊂ 读）。
 *
 * <p>本测试对三类角色（负责人 / 团队成员 / 无关工程师）逐个执行
 * 「列表 → 逐条取详情」，断言：
 * <ol>
 *   <li>列表里出现的每个资产，详情必 200；</li>
 *   <li>不在列表中的资产，详情必 403（防枚举）；</li>
 *   <li>漏洞列表与漏洞详情的可见性同源（列表内的漏洞详情 200，列表外的 403）。</li>
 * </ol>
 */
@DisplayName("资产可见性一致性测试")
class AssetVisibilityConsistencyTest extends BaseTest {

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    @Autowired
    private VulnerabilityRepository vulnerabilityRepository;

    private Team team;

    private Asset ownerAsset;      // 归属：负责人（engineer）
    private Asset teamAsset;       // 归属：team（leader 领导），owner=leader
    private Asset memberAsset;     // 归属：团队成员本人
    private Asset foreignAsset;    // 归属：admin，无团队 → 谁都不可见（除管理层）

    private String memberToken;    // 团队成员（普通工程师，非 leader）
    private String unrelatedToken; // 无关工程师（无任何资产/团队关系）

    private Vulnerability ownerVulnerability;
    private Vulnerability foreignVulnerability;

    @BeforeEach
    void setUpVisibilityData() {
        team = teamRepository.save(Objects.requireNonNull(Team.builder()
                .name("可见性测试团队-" + UUID.randomUUID())
                .leaderId(leaderUserId)
                .createdBy(adminUserId)
                .build()));

        ownerAsset = assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name("负责人资产-" + UUID.randomUUID())
                .code("VIS-A-" + UUID.randomUUID().toString().substring(0, 8))
                .targetType("web").targetSource("internal").description("负责人可见")
                .priority("P1").status("active").ownerId(engineerUserId)
                .build()));

        teamAsset = assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name("团队资产-" + UUID.randomUUID())
                .code("VIS-B-" + UUID.randomUUID().toString().substring(0, 8))
                .targetType("web").targetSource("internal").description("团队长可见")
                .priority("P2").status("active").ownerId(leaderUserId).teamId(team.getId())
                .build()));

        foreignAsset = assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name("无关资产-" + UUID.randomUUID())
                .code("VIS-C-" + UUID.randomUUID().toString().substring(0, 8))
                .targetType("web").targetSource("internal").description("仅管理层可见")
                .priority("P3").status("active").ownerId(adminUserId)
                .build()));

        // 团队成员：普通工程师，且是 team 的普通成员（非 leader）
        User member = userRepository.save(Objects.requireNonNull(User.builder()
                .username("vis_member_" + System.nanoTime())
                .passwordHash(passwordEncoder.encode("Member@123456"))
                .email("vis_member_" + System.nanoTime() + "@test.com")
                .fullName("团队成员")
                .status("active")
                .build()));
        teamMemberRepository.save(Objects.requireNonNull(TeamMember.builder()
                .teamId(team.getId())
                .userId(member.getId())
                .isLeader(false)
                .build()));
        memberToken = Objects.requireNonNull(generateTestToken(member.getUsername(), "ROLE_VULNERABILITY_ENGINEER"));

        memberAsset = assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name("成员资产-" + UUID.randomUUID())
                .code("VIS-D-" + UUID.randomUUID().toString().substring(0, 8))
                .targetType("web").targetSource("internal").description("团队成员自己负责")
                .priority("P2").status("active").ownerId(member.getId()).teamId(team.getId())
                .build()));

        // 无关工程师：无资产、无团队、无授权
        User unrelated = userRepository.save(Objects.requireNonNull(User.builder()
                .username("vis_unrelated_" + System.nanoTime())
                .passwordHash(passwordEncoder.encode("Unrelated@123456"))
                .email("vis_unrelated_" + System.nanoTime() + "@test.com")
                .fullName("无关工程师")
                .status("active")
                .build()));
        unrelatedToken = Objects.requireNonNull(
                generateTestToken(unrelated.getUsername(), "ROLE_VULNERABILITY_ENGINEER"));

        ownerVulnerability = vulnerabilityRepository.save(Objects.requireNonNull(Vulnerability.builder()
                .assetId(ownerAsset.getId())
                .title("负责人资产漏洞")
                .type("XSS").severity("MEDIUM")
                .description("可见性测试").detail("可见性测试")
                .workflowStatus("discovered").customerStatus("pending").disclosureStatus("undisclosed")
                .exploitStage("探测").fieldTestStatus("待测试").vendorFixed(false)
                .vulnerabilityNo("VUL-VIS-" + System.nanoTime())
                .discovererId(engineerUserId)
                .build()));

        foreignVulnerability = vulnerabilityRepository.save(Objects.requireNonNull(Vulnerability.builder()
                .assetId(foreignAsset.getId())
                .title("无关资产漏洞")
                .type("SQL").severity("HIGH")
                .description("可见性测试").detail("可见性测试")
                .workflowStatus("discovered").customerStatus("pending").disclosureStatus("undisclosed")
                .exploitStage("探测").fieldTestStatus("待测试").vendorFixed(false)
                .vulnerabilityNo("VUL-VIS-" + System.nanoTime())
                .discovererId(adminUserId)
                .build()));
    }

    // ------------------------------------------------------------------ 工具方法

    private List<UUID> listAssetIds(String token) throws Exception {
        String body = mockMvc.perform(get("/api/v1/assets")
                        .param("page", "0").param("size", "200")
                        .header("Authorization", token))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        List<UUID> ids = new ArrayList<>();
        int idx = 0;
        while ((idx = body.indexOf("\"id\":\"", idx)) >= 0) {
            int begin = idx + "\"id\":\"".length();
            ids.add(UUID.fromString(body.substring(begin, body.indexOf('"', begin))));
            idx = begin;
        }
        return ids;
    }

    private int assetDetailStatus(String token, UUID assetId) throws Exception {
        return mockMvc.perform(get("/api/v1/assets/" + assetId).header("Authorization", token))
                .andReturn().getResponse().getStatus();
    }

    private List<UUID> listVulnerabilityIds(String token) throws Exception {
        String body = mockMvc.perform(get("/api/v1/vulnerabilities")
                        .param("page", "0").param("size", "200")
                        .header("Authorization", token))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        List<UUID> ids = new ArrayList<>();
        int idx = 0;
        while ((idx = body.indexOf("\"id\":\"", idx)) >= 0) {
            int begin = idx + "\"id\":\"".length();
            ids.add(UUID.fromString(body.substring(begin, body.indexOf('"', begin))));
            idx = begin;
        }
        return ids;
    }

    private int vulnerabilityDetailStatus(String token, UUID id) throws Exception {
        return mockMvc.perform(get("/api/v1/vulnerabilities/" + id).header("Authorization", token))
                .andReturn().getResponse().getStatus();
    }

    private Set<UUID> allAssetIds() {
        Set<UUID> all = new LinkedHashSet<>();
        all.add(ownerAsset.getId());
        all.add(teamAsset.getId());
        all.add(memberAsset.getId());
        all.add(foreignAsset.getId());
        return all;
    }

    /** 不变量：列表内必 200、列表外必 403 */
    private void assertInvariant(String role, String token) throws Exception {
        List<UUID> listed = listAssetIds(token);
        Set<UUID> all = allAssetIds();

        for (UUID id : listed) {
            assertEquals(200, assetDetailStatus(token, id),
                    role + "：列表中出现的资产 " + id + " 详情必须 200（不得列表可见、详情 403）");
        }

        Set<UUID> notListed = new LinkedHashSet<>(all);
        notListed.removeAll(listed);
        for (UUID id : notListed) {
            assertEquals(403, assetDetailStatus(token, id),
                    role + "：列表外的资产 " + id + " 详情必须 403（防枚举）");
        }
        assertFalse(listed.isEmpty() && role.contains("负责人"), role + " 的列表不应为空");
    }

    // ------------------------------------------------------------------ 用例

    @Test
    @DisplayName("负责人：列表 = 自己负责的资产，列表内详情 200、列表外 403")
    void ownerInvariant() throws Exception {
        List<UUID> listed = listAssetIds(engineerToken);
        assertTrue(listed.contains(ownerAsset.getId()), "负责人应看到自己负责的资产");
        assertFalse(listed.contains(foreignAsset.getId()), "负责人不应看到无关资产");
        assertInvariant("负责人", engineerToken);
    }

    @Test
    @DisplayName("团队成员（非 leader）：只看到自己负责的资产，团队资产不自动可见")
    void teamMemberInvariant() throws Exception {
        List<UUID> listed = listAssetIds(memberToken);
        assertTrue(listed.contains(memberAsset.getId()), "团队成员应看到自己负责的资产");
        assertFalse(listed.contains(teamAsset.getId()), "团队普通成员不自动看到团队资产（SRS 口径）");
        assertInvariant("团队成员", memberToken);
    }

    @Test
    @DisplayName("无关工程师：列表为空，所有详情 403（不泄漏存在性）")
    void unrelatedEngineerInvariant() throws Exception {
        List<UUID> listed = listAssetIds(unrelatedToken);
        assertTrue(listed.isEmpty(), "无关工程师的资产列表应为空，实际: " + listed);
        for (UUID id : allAssetIds()) {
            assertEquals(403, assetDetailStatus(unrelatedToken, id), "无关工程师对任何资产详情都应 403");
        }
    }

    @Test
    @DisplayName("团队长：列表 = 本团队资产，越界资产 403")
    void teamLeaderInvariant() throws Exception {
        List<UUID> listed = listAssetIds(leaderToken);
        assertTrue(listed.contains(teamAsset.getId()), "团队长应看到本团队资产");
        assertFalse(listed.contains(foreignAsset.getId()), "团队长不应看到无关资产");
        assertInvariant("团队长", leaderToken);
    }

    @Test
    @DisplayName("管理层：列表包含全部资产，逐条详情 200")
    void adminInvariant() throws Exception {
        List<UUID> listed = listAssetIds(adminToken);
        assertTrue(listed.containsAll(allAssetIds()), "管理员列表应包含全部资产，实际: " + listed);
        for (UUID id : allAssetIds()) {
            assertEquals(200, assetDetailStatus(adminToken, id), "管理员详情应 200");
        }
    }

    @Test
    @DisplayName("漏洞列表与漏洞详情同源：列表内详情 200、列表外 403")
    void vulnerabilityVisibilitySameSource() throws Exception {
        // 负责人：能看到自己资产的漏洞
        List<UUID> ownerListed = listVulnerabilityIds(engineerToken);
        assertTrue(ownerListed.contains(ownerVulnerability.getId()), "负责人应看到本资产漏洞");
        assertFalse(ownerListed.contains(foreignVulnerability.getId()), "负责人不应看到无关资产漏洞");
        assertEquals(200, vulnerabilityDetailStatus(engineerToken, ownerVulnerability.getId()));
        assertEquals(403, vulnerabilityDetailStatus(engineerToken, foreignVulnerability.getId()));

        // 无关工程师：列表为空，两条漏洞详情都 403
        List<UUID> unrelatedListed = listVulnerabilityIds(unrelatedToken);
        assertTrue(unrelatedListed.isEmpty(), "无关工程师漏洞列表应为空，实际: " + unrelatedListed);
        assertEquals(403, vulnerabilityDetailStatus(unrelatedToken, ownerVulnerability.getId()));
        assertEquals(403, vulnerabilityDetailStatus(unrelatedToken, foreignVulnerability.getId()));

        // 管理层：全部可见
        List<UUID> adminListed = listVulnerabilityIds(adminToken);
        assertTrue(adminListed.contains(ownerVulnerability.getId()) && adminListed.contains(foreignVulnerability.getId()),
                "管理员应看到全部漏洞");
    }

    @Test
    @DisplayName("写 ⊂ 读：团队长对本团队资产可读，对越界资产既不可读也不可写")
    void writeImpliesRead() throws Exception {
        assertEquals(200, assetDetailStatus(leaderToken, teamAsset.getId()), "团队长本团队资产可读");
        assertEquals(403, assetDetailStatus(leaderToken, foreignAsset.getId()), "团队长越界资产不可读");

        // 写接口（PUT）对越界资产同样 403（canWriteAsset 以 canReadAsset 为前提）
        int writeStatus = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .put("/api/v1/assets/" + foreignAsset.getId())
                        .header("Authorization", leaderToken)
                        .contentType(Objects.requireNonNull(org.springframework.http.MediaType.APPLICATION_JSON))
                        .content("{\"name\":\"越权改名\"}"))
                .andReturn().getResponse().getStatus();
        assertEquals(403, writeStatus, "团队长不应能写越界资产（写 ⊂ 读）");
        assertEquals("无关资产-" , assetRepository.findById(foreignAsset.getId()).orElseThrow().getName().substring(0, 5));
    }
}
