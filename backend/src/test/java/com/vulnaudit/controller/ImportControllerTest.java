package com.vulnaudit.controller;

import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.Team;
import com.vulnaudit.entity.Vulnerability;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.TeamRepository;
import com.vulnaudit.repository.VulnerabilityRepository;
import com.vulnaudit.service.AssetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 批量导入接口测试
 *
 * <p>覆盖 t4 验收口径：上传含 3 行（1 行非法）的 CSV，返回 2 成功 1 失败并给出原因；
 * 同时覆盖全部成功、表头校验、模板下载与漏洞导入（复用 Service 的 severity 归一）。
 */
@DisplayName("批量导入接口测试")
class ImportControllerTest extends BaseTest {

    @Autowired
    private AssetService assetService;

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private VulnerabilityRepository vulnerabilityRepository;

    @Autowired
    private TeamRepository teamRepository;

    private Team testTeam;

    @BeforeEach
    void setUpTeam() {
        testTeam = teamRepository.save(Objects.requireNonNull(Team.builder()
                .name("红队一组")
                .leaderId(leaderUserId)
                .createdBy(adminUserId)
                .build()));
    }

    private MockMultipartFile csvFile(String fileName, String... lines) {
        String content = String.join("\n", lines) + "\n";
        return new MockMultipartFile("file", fileName, "text/csv",
                content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("资产导入 - 3 行 1 行非法：2 成功 1 失败并给出原因")
    void importAssets_partialFailure() throws Exception {
        MockMultipartFile file = csvFile("assets.csv",
                "name,targetType,targetSource,priority,status,teamCode,description",
                "客户门户,Web应用,客户委托,P1,active,红队一组,门户站点",
                "内部系统,系统,自主发现,P9,active,红队一组,优先级非法",
                "运维平台,服务,客户委托,P2,under_test,红队一组,运维入口");

        mockMvc.perform(multipart("/api/v1/import/assets").file(file)
                        .header("Authorization", leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalRows").value(3))
                .andExpect(jsonPath("$.data.successRows").value(2))
                .andExpect(jsonPath("$.data.failedRows", hasSize(1)))
                .andExpect(jsonPath("$.data.failedRows[0].line").value(3))
                .andExpect(jsonPath("$.data.failedRows[0].reason", containsString("无效的优先级")));

        List<Asset> assets = assetRepository.findAll();
        assertEquals(2, assets.size());
        assertTrue(assets.stream().anyMatch(asset -> "客户门户".equals(asset.getName())));
        assertTrue(assets.stream().anyMatch(asset -> "运维平台".equals(asset.getName())));
        assertTrue(assets.stream().allMatch(asset -> testTeam.getId().equals(asset.getTeamId())));
        assertTrue(assets.stream().allMatch(asset -> asset.getCode() != null && asset.getCode().startsWith("AST-")));
    }

    @Test
    @DisplayName("资产导入 - 两行全部成功，failedRows 为空")
    void importAssets_allSuccess() throws Exception {
        MockMultipartFile file = csvFile("assets.csv",
                "name,targetType,targetSource,priority,status,teamId,description",
                "客户门户,Web应用,客户委托,P1,active," + testTeam.getId() + ",门户站点",
                "内部系统,系统,自主发现,P2,active," + testTeam.getId() + ",内部系统");

        mockMvc.perform(multipart("/api/v1/import/assets").file(file)
                        .header("Authorization", leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.successRows").value(2))
                .andExpect(jsonPath("$.data.failedRows", hasSize(0)));

        assertEquals(2, assetRepository.count());
    }

    @Test
    @DisplayName("资产导入 - 表头缺少必需列返回 400")
    void importAssets_missingRequiredColumn() throws Exception {
        MockMultipartFile file = csvFile("bad.csv",
                "name,priority",
                "客户门户,P1");

        mockMvc.perform(multipart("/api/v1/import/assets").file(file)
                        .header("Authorization", leaderToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("漏洞导入 - severity 小写输入被服务层归一为 HIGH，非法行给出原因")
    void importVulnerabilities_partialFailure() throws Exception {
        Asset asset = assetService.createAsset(Objects.requireNonNull(Asset.builder()
                .name("客户门户")
                .targetType("Web应用")
                .targetSource("客户委托")
                .priority("P1")
                .status("active")
                .teamId(testTeam.getId())
                .ownerId(engineerUserId)
                .build()));

        MockMultipartFile file = csvFile("vulns.csv",
                "title,assetCode,type,severity,cvssScore,workflowStatus,description",
                "登录接口SQL注入," + asset.getCode() + ",SQL注入,high,8.8,discovered,登录接口存在注入点",
                "越权访问," + asset.getCode() + ",越权,unknown,5.0,discovered,越权访问他人订单");

        mockMvc.perform(multipart("/api/v1/import/vulnerabilities").file(file)
                        .header("Authorization", leaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalRows").value(2))
                .andExpect(jsonPath("$.data.successRows").value(1))
                .andExpect(jsonPath("$.data.failedRows", hasSize(1)))
                .andExpect(jsonPath("$.data.failedRows[0].line").value(3))
                .andExpect(jsonPath("$.data.failedRows[0].reason", containsString("无效的严重程度")));

        List<Vulnerability> vulnerabilities = vulnerabilityRepository.findAll();
        assertEquals(1, vulnerabilities.size());
        Vulnerability saved = vulnerabilities.get(0);
        assertEquals("HIGH", saved.getSeverity());
        assertEquals("登录接口SQL注入", saved.getTitle());
        assertEquals(asset.getId(), saved.getAssetId());
        assertEquals("discovered", saved.getWorkflowStatus());
        assertTrue(saved.getVulnerabilityNo() != null && !saved.getVulnerabilityNo().isBlank());
    }

    @Test
    @DisplayName("模板下载 - 返回 CSV 表头")
    void downloadTemplate() throws Exception {
        mockMvc.perform(get("/api/v1/import/templates/assets")
                        .header("Authorization", leaderToken))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name,targetType,targetSource,priority,status,teamCode,description")));

        mockMvc.perform(get("/api/v1/import/templates/vulnerabilities")
                        .header("Authorization", leaderToken))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("title,assetCode,type,severity,cvssScore,workflowStatus,description")));

        mockMvc.perform(get("/api/v1/import/templates/unknown")
                        .header("Authorization", leaderToken))
                .andExpect(status().isNotFound());
    }
}
