package com.vulnaudit.controller;

import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.Team;
import com.vulnaudit.entity.TeamMember;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.AssetVersionRepository;
import com.vulnaudit.repository.TeamMemberRepository;
import com.vulnaudit.repository.TeamRepository;
import com.vulnaudit.repository.VulnerabilityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 批量导入的授权与逐行数据边界测试（t33）。
 *
 * <p>背景：{@code POST /api/v1/import/assets|vulnerabilities} 此前没有任何角色或数据级校验，
 * 任何登录用户都能批量写入资产与漏洞（写侧越权）。本用例验证：
 * <ol>
 *   <li>资产导入：仅 admin/project_leader/team_leader 可用；team_leader 只能导入自己领导/所属的团队；</li>
 *   <li>漏洞导入：逐行按目标资产判定 {@code canWriteAsset}，无权行进 failedRows 且不落库（含不创建版本）；</li>
 *   <li>权限拒绝与校验失败在 reason 上可区分（前者以「权限拒绝：」开头）。</li>
 * </ol>
 */
@DisplayName("批量导入授权测试")
public class ImportAuthorizationTest extends BaseTest {

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private AssetVersionRepository assetVersionRepository;

    @Autowired
    private VulnerabilityRepository vulnerabilityRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private TeamMemberRepository teamMemberRepository;

    private Team ownTeam;
    private Team otherTeam;
    private Asset engineerAsset;
    private Asset adminAsset;

    @BeforeEach
    void setUpImportData() {
        String suffix = String.valueOf(System.currentTimeMillis());

        // leaderToken 对应的小组长所领导的团队
        ownTeam = teamRepository.save(Objects.requireNonNull(Team.builder()
                .name("导入测试-自己的团队_" + suffix)
                .description("小组长可导入")
                .leaderId(leaderUserId)
                .createdBy(adminUserId)
                .build()));

        // 与小组长无关的团队
        otherTeam = teamRepository.save(Objects.requireNonNull(Team.builder()
                .name("导入测试-他人团队_" + suffix)
                .description("小组长不可导入")
                .leaderId(adminUserId)
                .createdBy(adminUserId)
                .build()));

        engineerAsset = assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name("工程师负责的资产_" + suffix)
                .code("IMP-ENG-" + suffix)
                .targetType("Web应用")
                .targetSource("internal")
                .priority("P1")
                .status("active")
                .ownerId(engineerUserId)
                .teamId(ownTeam.getId())
                .build()));

        adminAsset = assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name("管理员负责的资产_" + suffix)
                .code("IMP-ADM-" + suffix)
                .targetType("Web应用")
                .targetSource("internal")
                .priority("P2")
                .status("active")
                .ownerId(adminUserId)
                .teamId(otherTeam.getId())
                .build()));
    }

    private MockMultipartFile csv(String fileName, String... lines) {
        String content = String.join("\n", lines) + "\n";
        return new MockMultipartFile("file", fileName, "text/csv",
                content.getBytes(StandardCharsets.UTF_8));
    }

    // ------------------------------------------------------------------ 资产导入

    @Test
    @DisplayName("工程师导入资产 → 403（整单拒绝，不落库）")
    void importAssets_forbiddenForEngineer() throws Exception {
        long before = assetRepository.count();

        MockMultipartFile file = csv("assets.csv",
                "name,targetType,targetSource,priority,status,teamId,description",
                "工程师越权资产,Web应用,internal,P1,active," + ownTeam.getId() + ",越权");

        mockMvc.perform(multipart("/api/v1/import/assets").file(file)
                        .header("Authorization", engineerToken))
                .andExpect(status().isForbidden());

        assertEquals(before, assetRepository.count(), "被拒绝的导入不应写入任何资产");
    }

    @Test
    @DisplayName("小组长导入：本团队成功、他人团队该行 failed 且不落库")
    void importAssets_teamLeaderScopedToOwnTeams() throws Exception {
        MockMultipartFile file = csv("assets.csv",
                "name,targetType,targetSource,priority,status,teamId,description",
                "小组长本团队资产,Web应用,internal,P1,active," + ownTeam.getId() + ",允许",
                "小组长越权资产,Web应用,internal,P1,active," + otherTeam.getId() + ",越权");

        mockMvc.perform(multipart("/api/v1/import/assets").file(file)
                        .header("Authorization", leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRows").value(2))
                .andExpect(jsonPath("$.data.successRows").value(1))
                .andExpect(jsonPath("$.data.failedRows", hasSize(1)))
                .andExpect(jsonPath("$.data.failedRows[0].line").value(3))
                .andExpect(jsonPath("$.data.failedRows[0].reason", containsString("权限拒绝")))
                .andExpect(jsonPath("$.data.failedRows[0].reason", containsString("无权导入该团队")));

        assertTrue(assetRepository.findAll().stream()
                        .anyMatch(asset -> "小组长本团队资产".equals(asset.getName())),
                "本团队资产应已落库");
        assertTrue(assetRepository.findAll().stream()
                        .noneMatch(asset -> "小组长越权资产".equals(asset.getName())),
                "他人团队资产不应落库");
    }

    @Test
    @DisplayName("小组长导入未指定团队 → 该行失败且不落库（fail-closed，不属于默认放行）")
    void importAssets_teamLeaderWithoutTeamFailsClosed() throws Exception {
        MockMultipartFile file = csv("assets.csv",
                "name,targetType,targetSource,priority,status,description",
                "无团队资产,Web应用,internal,P1,active,缺少团队");

        mockMvc.perform(multipart("/api/v1/import/assets").file(file)
                        .header("Authorization", leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successRows").value(0))
                .andExpect(jsonPath("$.data.failedRows", hasSize(1)))
                // 缺列属于「校验失败」而非「权限拒绝」，两类原因文案可区分
                .andExpect(jsonPath("$.data.failedRows[0].reason", containsString("缺少 teamId 或 teamCode")));

        assertTrue(assetRepository.findAll().stream()
                        .noneMatch(asset -> "无团队资产".equals(asset.getName())),
                "未指定团队的行不应落库（不存在默认放行分支）");
    }

    @Test
    @DisplayName("管理员导入任意团队 → 全部成功")
    void importAssets_adminUnrestricted() throws Exception {
        MockMultipartFile file = csv("assets.csv",
                "name,targetType,targetSource,priority,status,teamId,description",
                "管理员导入A,Web应用,internal,P1,active," + ownTeam.getId() + ",允许",
                "管理员导入B,Web应用,internal,P3,active," + otherTeam.getId() + ",允许");

        mockMvc.perform(multipart("/api/v1/import/assets").file(file)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successRows").value(2))
                .andExpect(jsonPath("$.data.failedRows", hasSize(0)));
    }

    // ------------------------------------------------------------------ 漏洞导入

    @Test
    @DisplayName("工程师导入漏洞：自己资产成功、他人资产该行 failed 且无任何写入")
    void importVulnerabilities_perRowAuthorization() throws Exception {
        MockMultipartFile file = csv("vulns.csv",
                "title,assetCode,type,severity,description",
                "自己的漏洞," + engineerAsset.getCode() + ",SQL注入,high,允许",
                "越权漏洞," + adminAsset.getCode() + ",XSS,critical,越权");

        mockMvc.perform(multipart("/api/v1/import/vulnerabilities").file(file)
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRows").value(2))
                .andExpect(jsonPath("$.data.successRows").value(1))
                .andExpect(jsonPath("$.data.failedRows", hasSize(1)))
                .andExpect(jsonPath("$.data.failedRows[0].line").value(3))
                .andExpect(jsonPath("$.data.failedRows[0].reason", containsString("权限拒绝")))
                .andExpect(jsonPath("$.data.failedRows[0].reason", containsString("无权在该资产下创建漏洞")));

        assertEquals(1, vulnerabilityRepository.findByAssetId(engineerAsset.getId()).size(),
                "自己资产下的漏洞应成功落库");
        assertEquals(0, vulnerabilityRepository.findByAssetId(adminAsset.getId()).size(),
                "他人资产下不应落库");
        assertEquals(0, assetVersionRepository.findByAssetIdOrderByVersionNoDesc(adminAsset.getId()).size(),
                "被拒绝的行不应产生副作用（如自动创建资产版本）");
    }

    @Test
    @DisplayName("管理员导入漏洞：跨资产全部成功")
    void importVulnerabilities_adminUnrestricted() throws Exception {
        MockMultipartFile file = csv("vulns.csv",
                "title,assetCode,type,severity,description",
                "管理员导入1," + engineerAsset.getCode() + ",SQL注入,high,允许",
                "管理员导入2," + adminAsset.getCode() + ",XSS,critical,允许");

        mockMvc.perform(multipart("/api/v1/import/vulnerabilities").file(file)
                        .header("Authorization", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successRows").value(2))
                .andExpect(jsonPath("$.data.failedRows", hasSize(0)));

        assertEquals(1, vulnerabilityRepository.findByAssetId(engineerAsset.getId()).size());
        assertEquals(1, vulnerabilityRepository.findByAssetId(adminAsset.getId()).size());
    }

    @Test
    @DisplayName("校验失败与权限拒绝在 reason 上可区分")
    void importVulnerabilities_distinguishesValidationFromPermission() throws Exception {
        MockMultipartFile file = csv("vulns.csv",
                "title,assetCode,type,severity,description",
                "校验失败行," + adminAsset.getCode() + ",SQL注入,不存在的等级,描述",
                "权限拒绝行," + adminAsset.getCode() + ",XSS,high,描述");

        mockMvc.perform(multipart("/api/v1/import/vulnerabilities").file(file)
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successRows").value(0))
                .andExpect(jsonPath("$.data.failedRows", hasSize(2)));

        // 第 2 行：权限拒绝（先于严重程度校验，避免泄露他人资产的校验细节）
        mockMvc.perform(multipart("/api/v1/import/vulnerabilities").file(csv("vulns.csv",
                        "title,assetCode,type,severity,description",
                        "权限拒绝行," + adminAsset.getCode() + ",XSS,不存在的等级,描述"))
                        .header("Authorization", engineerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.failedRows[0].reason", containsString("权限拒绝")));
    }

    @Test
    @DisplayName("小组长作为团队成员（非领导）也可导入该团队资产")
    void importAssets_teamLeaderAsMemberAllowed() throws Exception {
        // 让小组长成为 otherTeam 的普通成员，验证「领导 ∪ 所属」口径
        teamMemberRepository.save(Objects.requireNonNull(TeamMember.builder()
                .teamId(otherTeam.getId())
                .userId(leaderUserId)
                .isLeader(false)
                .status("available")
                .build()));

        MockMultipartFile file = csv("assets.csv",
                "name,targetType,targetSource,priority,status,teamId,description",
                "成员团队资产,Web应用,internal,P2,active," + otherTeam.getId() + ",成员可导入");

        mockMvc.perform(multipart("/api/v1/import/assets").file(file)
                        .header("Authorization", leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successRows").value(1))
                .andExpect(jsonPath("$.data.failedRows", hasSize(0)));
    }
}
