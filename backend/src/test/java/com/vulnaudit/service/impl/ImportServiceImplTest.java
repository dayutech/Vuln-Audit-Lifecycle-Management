package com.vulnaudit.service.impl;

import com.vulnaudit.dto.ImportResult;
import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.AssetVersion;
import com.vulnaudit.entity.Team;
import com.vulnaudit.entity.Vulnerability;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.repository.AssetVersionRepository;
import com.vulnaudit.repository.TeamRepository;
import com.vulnaudit.service.AssetService;
import com.vulnaudit.service.AuditLogService;
import com.vulnaudit.service.VulnerabilityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 批量导入服务单元测试
 *
 * <p>覆盖三种统计口径：全部成功、部分失败（含服务层异常）、全部失败，
 * 并校验导入结束时写入审计日志。
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("批量导入服务测试")
class ImportServiceImplTest {

    @Mock
    private AssetService assetService;

    @Mock
    private VulnerabilityService vulnerabilityService;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private AssetVersionRepository assetVersionRepository;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private com.vulnaudit.service.UserService userService;

    @Mock
    private com.vulnaudit.service.RoleService roleService;

    private ImportServiceImpl importService;

    private final UUID operatorId = UUID.randomUUID();
    private final UUID teamId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        importService = new ImportServiceImpl(assetService, vulnerabilityService, teamRepository,
                assetVersionRepository, auditLogService, userService, roleService);

        Team team = Team.builder().id(teamId).name("红队一组").leaderId(operatorId).createdBy(operatorId).build();
        when(teamRepository.findByName("红队一组")).thenReturn(Optional.of(team));
        when(assetService.createAsset(any())).thenAnswer(invocation -> {
            Asset asset = invocation.getArgument(0);
            return Asset.builder().id(UUID.randomUUID())
                    .name(asset == null ? null : asset.getName())
                    .build();
        });
    }

    private byte[] csv(String... lines) {
        return (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("资产导入 - 两行全部成功")
    void importAssets_allSuccess() {
        byte[] content = csv(
                "name,targetType,targetSource,priority,status,teamCode,description",
                "资产A,Web应用,客户委托,P1,active,红队一组,描述A",
                "资产B,系统,自主发现,p2,under_test,红队一组,描述B");

        ImportResult result = importService.importAssets(content, "assets.csv", operatorId);

        assertEquals(2, result.totalRows());
        assertEquals(2, result.successRows());
        assertTrue(result.failedRows().isEmpty());

        ArgumentCaptor<Asset> captor = ArgumentCaptor.forClass(Asset.class);
        verify(assetService, times(2)).createAsset(captor.capture());
        List<Asset> assets = captor.getAllValues();
        assertEquals("资产A", assets.get(0).getName());
        assertEquals("P1", assets.get(0).getPriority());
        // 小写优先级被归一为大写后交给服务层
        assertEquals("P2", assets.get(1).getPriority());
        assertEquals(operatorId, assets.get(0).getOwnerId());
        assertEquals(teamId, assets.get(0).getTeamId());

        verify(auditLogService).log(any(), anyString(), anyString(), any(), anyString(), any());
    }

    @Test
    @DisplayName("资产导入 - 3 行 1 行非法，返回 2 成功 1 失败且带行号与原因")
    void importAssets_partialFailure() {
        byte[] content = csv(
                "name,targetType,targetSource,priority,status,teamCode,description",
                "资产A,Web应用,客户委托,P1,active,红队一组,描述A",
                "资产B,系统,自主发现,P9,active,红队一组,非法优先级",
                "资产C,服务,客户委托,P3,active,红队一组,描述C");

        ImportResult result = importService.importAssets(content, "assets.csv", operatorId);

        assertEquals(3, result.totalRows());
        assertEquals(2, result.successRows());
        assertEquals(1, result.failedRows().size());
        assertEquals(3, result.failedRows().get(0).line());
        assertTrue(result.failedRows().get(0).reason().contains("无效的优先级"));
        verify(assetService, times(2)).createAsset(any());
    }

    @Test
    @DisplayName("资产导入 - 服务层异常（如团队被并发删除）只影响当前行")
    void importAssets_serviceThrows_continuesNextRow() {
        org.mockito.Mockito.doThrow(new BusinessException(com.vulnaudit.exception.ErrorCode.TEAM_NOT_FOUND))
                .doAnswer(invocation -> Asset.builder().id(UUID.randomUUID()).build())
                .when(assetService).createAsset(any());
        byte[] content = csv(
                "name,targetType,targetSource,priority,status,teamCode,description",
                "资产A,Web应用,客户委托,P1,active,红队一组,描述A",
                "资产B,Web应用,客户委托,P1,active,红队一组,描述B");

        ImportResult result = importService.importAssets(content, "assets.csv", operatorId);

        assertEquals(2, result.totalRows());
        assertEquals(1, result.successRows());
        assertEquals(1, result.failedRows().size());
        assertEquals(2, result.failedRows().get(0).line());
        assertTrue(result.failedRows().get(0).reason().contains("团队不存在"));
    }

    @Test
    @DisplayName("资产导入 - 单行全部非法时 successRows 为 0")
    void importAssets_allFailed() {
        byte[] content = csv(
                "name,targetType,targetSource,priority,status,teamCode,description",
                "资产A,Web应用,客户委托,P1,active,不存在的团队,描述A");

        ImportResult result = importService.importAssets(content, "assets.csv", operatorId);

        assertEquals(1, result.totalRows());
        assertEquals(0, result.successRows());
        assertEquals(1, result.failedRows().size());
        assertEquals(2, result.failedRows().get(0).line());
        assertTrue(result.failedRows().get(0).reason().contains("团队不存在"));
        verify(assetService, times(0)).createAsset(any());
    }

    @Test
    @DisplayName("资产导入 - teamId 列同样生效（UUID 直填）")
    void importAssets_byTeamId() {
        when(teamRepository.existsById(teamId)).thenReturn(true);
        byte[] content = csv(
                "name,targetType,targetSource,priority,status,teamId,description",
                "资产A,Web应用,客户委托,P1,active," + teamId + ",描述A");

        ImportResult result = importService.importAssets(content, "assets.csv", operatorId);

        assertEquals(1, result.successRows());
        ArgumentCaptor<Asset> captor = ArgumentCaptor.forClass(Asset.class);
        verify(assetService).createAsset(captor.capture());
        assertEquals(teamId, captor.getValue().getTeamId());
    }

    @Test
    @DisplayName("资产导入 - 表头缺少必需列时直接报错")
    void importAssets_missingHeaderColumn() {
        byte[] content = csv("name,priority", "资产A,P1");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> importService.importAssets(content, "assets.csv", operatorId));

        assertTrue(ex.getMessage().contains("缺少必需列"));
        assertTrue(ex.getMessage().contains("targettype"));
    }

    @Test
    @DisplayName("资产导入 - 空文件报错")
    void importAssets_emptyFile() {
        assertThrows(BusinessException.class,
                () -> importService.importAssets(new byte[0], "assets.csv", operatorId));
    }

    // ------------------------------------------------------------------ 漏洞

    private Asset existingAsset() {
        Asset asset = Asset.builder()
                .id(UUID.randomUUID())
                .name("客户门户")
                .code("AST-20250101-ABC123")
                .teamId(teamId)
                .ownerId(operatorId)
                .build();
        when(assetService.findByCode(anyString())).thenAnswer(invocation ->
                "AST-20250101-ABC123".equals(invocation.getArgument(0))
                        ? Optional.of(asset)
                        : Optional.empty());
        when(assetVersionRepository.findByAssetIdAndIsDefaultTrue(asset.getId()))
                .thenReturn(Optional.of(AssetVersion.builder()
                        .id(UUID.randomUUID())
                        .assetId(asset.getId())
                        .versionNo(1)
                        .name("v1")
                        .isDefault(true)
                        .isLatest(true)
                        .build()));
        when(vulnerabilityService.createVulnerability(any())).thenAnswer(invocation -> invocation.getArgument(0));
        return asset;
    }

    @Test
    @DisplayName("漏洞导入 - severity 原样传给服务层（由服务归一为大写）")
    void importVulnerabilities_passesSeverityToService() {
        Asset asset = existingAsset();
        byte[] content = csv(
                "title,assetCode,type,severity,cvssScore,workflowStatus,description",
                "前台SQL注入,AST-20250101-ABC123,SQL注入,high,8.8,VERIFIED,登录接口可注入");

        ImportResult result = importService.importVulnerabilities(content, "vulns.csv", operatorId);

        assertEquals(1, result.successRows());
        assertTrue(result.failedRows().isEmpty());

        ArgumentCaptor<Vulnerability> captor = ArgumentCaptor.forClass(Vulnerability.class);
        verify(vulnerabilityService).createVulnerability(captor.capture());
        Vulnerability vulnerability = captor.getValue();
        assertEquals("high", vulnerability.getSeverity());
        assertEquals(asset.getId(), vulnerability.getAssetId());
        assertEquals(new BigDecimal("8.8"), vulnerability.getCvssScore());
        assertEquals("verified", vulnerability.getWorkflowStatus());
        assertEquals("登录接口可注入", vulnerability.getDetail());
        assertEquals(operatorId, vulnerability.getDiscovererId());
        assertNotNull(vulnerability.getDiscoveredVersionId());
    }

    @Test
    @DisplayName("漏洞导入 - 资产无版本时自动创建 v1 版本")
    void importVulnerabilities_createsInitialVersionWhenMissing() {
        Asset asset = Asset.builder()
                .id(UUID.randomUUID())
                .name("无版本资产")
                .code("AST-20250101-NOVER1")
                .build();
        when(assetService.findByCode("AST-20250101-NOVER1")).thenReturn(Optional.of(asset));
        when(assetVersionRepository.findByAssetIdAndIsDefaultTrue(asset.getId())).thenReturn(Optional.empty());
        when(assetVersionRepository.findFirstByAssetIdOrderByVersionNoDesc(asset.getId())).thenReturn(Optional.empty());
        UUID createdVersionId = UUID.randomUUID();
        when(assetVersionRepository.save(any())).thenReturn(AssetVersion.builder()
                .id(createdVersionId).assetId(asset.getId()).versionNo(1).name("v1").build());
        when(vulnerabilityService.createVulnerability(any())).thenAnswer(invocation -> invocation.getArgument(0));

        byte[] content = csv(
                "title,assetCode,type,severity,cvssScore,workflowStatus,description",
                "弱口令,AST-20250101-NOVER1,弱口令,LOW,3.1,,后台口令可猜测");

        ImportResult result = importService.importVulnerabilities(content, "vulns.csv", operatorId);

        assertEquals(1, result.successRows());
        ArgumentCaptor<Vulnerability> captor = ArgumentCaptor.forClass(Vulnerability.class);
        verify(vulnerabilityService).createVulnerability(captor.capture());
        assertEquals(createdVersionId, captor.getValue().getDiscoveredVersionId());
        // 未填 workflowStatus 时默认 discovered
        assertEquals("discovered", captor.getValue().getWorkflowStatus());
    }

    @Test
    @DisplayName("漏洞导入 - 3 行 1 行非法（严重程度拼写错误），2 成功 1 失败")
    void importVulnerabilities_partialFailure() {
        existingAsset();
        when(vulnerabilityService.createVulnerability(any()))
                .thenAnswer(invocation -> invocation.getArgument(0))
                .thenThrow(new BusinessException(com.vulnaudit.exception.ErrorCode.BAD_REQUEST, "无效的严重程度: superhigh"))
                .thenAnswer(invocation -> invocation.getArgument(0));
        byte[] content = csv(
                "title,assetCode,type,severity,cvssScore,workflowStatus,description",
                "漏洞A,AST-20250101-ABC123,XSS,HIGH,6.1,discovered,描述A",
                "漏洞B,AST-20250101-ABC123,XSS,superhigh,6.1,discovered,描述B",
                "漏洞C,AST-20250101-ABC123,RCE,MEDIUM,5.0,discovered,描述C");

        ImportResult result = importService.importVulnerabilities(content, "vulns.csv", operatorId);

        assertEquals(3, result.totalRows());
        assertEquals(2, result.successRows());
        assertEquals(1, result.failedRows().size());
        assertEquals(3, result.failedRows().get(0).line());
        assertTrue(result.failedRows().get(0).reason().contains("无效的严重程度"));
    }

    @Test
    @DisplayName("漏洞导入 - 资产编号不存在与 workflowStatus 非法均逐行报错")
    void importVulnerabilities_invalidForeignKeyAndEnum() {
        existingAsset();
        byte[] content = csv(
                "title,assetCode,type,severity,cvssScore,workflowStatus,description",
                "漏洞A,AST-99999999-NOTEXI,XSS,HIGH,6.1,discovered,资产不存在",
                "漏洞B,AST-20250101-ABC123,XSS,HIGH,6.1,unknown_status,状态非法");

        ImportResult result = importService.importVulnerabilities(content, "vulns.csv", operatorId);

        assertEquals(2, result.totalRows());
        assertEquals(0, result.successRows());
        assertEquals(2, result.failedRows().size());
        assertTrue(result.failedRows().get(0).reason().contains("资产编号不存在"));
        assertTrue(result.failedRows().get(1).reason().contains("workflowStatus"));

    }

    @Test
    @DisplayName("漏洞导入 - cvssScore 越界被拒绝")
    void importVulnerabilities_cvssOutOfRange() {
        existingAsset();
        byte[] content = csv(
                "title,assetCode,type,severity,cvssScore,workflowStatus,description",
                "漏洞A,AST-20250101-ABC123,XSS,HIGH,11.2,discovered,描述A");

        ImportResult result = importService.importVulnerabilities(content, "vulns.csv", operatorId);

        assertEquals(0, result.successRows());
        assertEquals(1, result.failedRows().size());
        assertTrue(result.failedRows().get(0).reason().contains("cvssScore"));
    }

    @Test
    @DisplayName("全失败场景同样写入审计日志")
    void importAssets_allFailed_stillAudits() {
        byte[] content = csv("name,targetType,targetSource,priority,teamCode", "资产A,Web应用,客户委托,P1,不存在的团队");

        ImportResult result = importService.importAssets(content, "assets.csv", operatorId);

        assertEquals(0, result.successRows());
        verify(auditLogService).log(any(), anyString(), anyString(), any(), anyString(), any());
    }
}
