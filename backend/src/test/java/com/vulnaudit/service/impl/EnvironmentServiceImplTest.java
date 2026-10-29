package com.vulnaudit.service.impl;

import com.vulnaudit.entity.Environment;
import com.vulnaudit.entity.EnvironmentSnapshot;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.AssetVersionRepository;
import com.vulnaudit.repository.EnvironmentRepository;
import com.vulnaudit.repository.EnvironmentSnapshotRepository;
import com.vulnaudit.service.env.EnvironmentProvisioner;
import com.vulnaudit.service.env.EnvironmentProvisionerSelector;
import com.vulnaudit.service.env.EnvironmentStateService;
import com.vulnaudit.service.env.NoopProvisioner;
import com.vulnaudit.service.env.ProvisionResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 环境部署/启停服务测试（Noop 路径与成功路径）
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("环境部署服务测试")
class EnvironmentServiceImplTest {

    @Mock
    private EnvironmentRepository environmentRepository;

    @Mock
    private EnvironmentSnapshotRepository environmentSnapshotRepository;

    @Mock
    private AssetVersionRepository assetVersionRepository;

    @Mock
    private EnvironmentProvisionerSelector provisionerSelector;

    @Mock
    private EnvironmentStateService environmentStateService;

    @Mock
    private EnvironmentProvisioner dockerProvisioner;

    private EnvironmentServiceImpl environmentService;

    private UUID environmentId;
    private Environment environment;

    @BeforeEach
    void setUp() {
        environmentService = new EnvironmentServiceImpl(environmentRepository, environmentSnapshotRepository,
                assetVersionRepository, provisionerSelector, environmentStateService);

        environmentId = UUID.randomUUID();
        environment = Environment.builder()
                .id(environmentId)
                .name("测试环境")
                .assetId(UUID.randomUUID())
                .templateId(UUID.randomUUID())
                .config("{\"image\":\"nginx:alpine\"}")
                .status("created")
                .version(1)
                .createdBy(UUID.randomUUID())
                .build();

        when(environmentStateService.markProvisioning(environmentId)).thenReturn(environment);
        when(environmentStateService.find(environmentId)).thenReturn(Optional.of(environment));
        when(environmentStateService.markFailed(any(), anyString())).thenReturn(environment);
        when(environmentStateService.markRunning(any(), any())).thenReturn(environment);
        when(environmentStateService.markStopped(any())).thenReturn(environment);
    }

    // ------------------------------------------------------------------ 部署

    @Test
    @DisplayName("未启用容器编排：部署抛 503，状态写 failed 且记录失败原因，不写 running")
    void deploy_withoutOrchestration_failsInsteadOfPretending() {
        when(provisionerSelector.active()).thenReturn(new NoopProvisioner());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> environmentService.deployEnvironment(environmentId));

        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, exception.getErrorCode());
        assertEquals(EnvironmentProvisioner.UNAVAILABLE_MESSAGE, exception.getMessage());

        ArgumentCaptor<String> reason = ArgumentCaptor.forClass(String.class);
        verify(environmentStateService).markFailed(eq(environmentId), reason.capture());
        assertTrue(reason.getValue().startsWith(EnvironmentProvisioner.UNAVAILABLE_MESSAGE));
        verify(environmentStateService, never()).markRunning(any(), any());
    }

    @Test
    @DisplayName("部署前先标记 provisioning")
    void deploy_marksProvisioningFirst() {
        when(provisionerSelector.active()).thenReturn(new NoopProvisioner());

        assertThrows(BusinessException.class, () -> environmentService.deployEnvironment(environmentId));

        verify(environmentStateService).markProvisioning(environmentId);
    }

    @Test
    @DisplayName("配置为空：部署返回 400 并写 failed")
    void deploy_blankConfig_failsWithBadRequest() {
        Environment blank = Environment.builder().id(environmentId).name("空配置").config("  ").build();
        when(environmentStateService.markProvisioning(environmentId)).thenReturn(blank);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> environmentService.deployEnvironment(environmentId));

        assertEquals(ErrorCode.BAD_REQUEST, exception.getErrorCode());
        verify(environmentStateService).markFailed(environmentId, "环境配置不能为空");
        verify(environmentStateService, never()).markRunning(any(), any());
    }

    @Test
    @DisplayName("容器编排可用：部署成功写 running 与 containerId")
    void deploy_withDocker_setsRunning() {
        when(provisionerSelector.active()).thenReturn(dockerProvisioner);
        when(dockerProvisioner.type()).thenReturn("docker");
        when(dockerProvisioner.provision(environment)).thenReturn(ProvisionResult.ok("abc123container"));

        environmentService.deployEnvironment(environmentId);

        verify(environmentStateService).markRunning(environmentId, "abc123container");
        verify(environmentStateService, never()).markFailed(any(), anyString());
    }

    @Test
    @DisplayName("docker 执行失败：写 failed 并抛 500，原因透传")
    void deploy_dockerFailure_writesFailedReason() {
        when(provisionerSelector.active()).thenReturn(dockerProvisioner);
        when(dockerProvisioner.type()).thenReturn("docker");
        when(dockerProvisioner.provision(environment))
                .thenReturn(ProvisionResult.fail("docker run 失败: pull access denied"));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> environmentService.deployEnvironment(environmentId));

        assertEquals(ErrorCode.INTERNAL_ERROR, exception.getErrorCode());
        assertTrue(exception.getMessage().contains("pull access denied"));
        verify(environmentStateService).markFailed(environmentId, "docker run 失败: pull access denied");
    }

    @Test
    @DisplayName("环境不存在：抛 404")
    void deploy_missingEnvironment_returnsNotFound() {
        UUID missing = UUID.randomUUID();
        when(environmentStateService.markProvisioning(missing))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> environmentService.deployEnvironment(missing));
        assertEquals(ErrorCode.NOT_FOUND, exception.getErrorCode());
    }

    // ------------------------------------------------------------------ 启停

    @Test
    @DisplayName("未启用容器编排：启动抛 503 并写 failed")
    void start_withoutOrchestration_fails() {
        when(provisionerSelector.active()).thenReturn(new NoopProvisioner());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> environmentService.startEnvironment(environmentId));

        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, exception.getErrorCode());
        assertEquals(EnvironmentProvisioner.UNAVAILABLE_MESSAGE, exception.getMessage());
        verify(environmentStateService).markFailed(eq(environmentId), contains(EnvironmentProvisioner.UNAVAILABLE_MESSAGE));
        verify(environmentStateService, never()).markRunning(any(), any());
    }

    @Test
    @DisplayName("未启用容器编排：停止抛 503 并写 failed")
    void stop_withoutOrchestration_fails() {
        when(provisionerSelector.active()).thenReturn(new NoopProvisioner());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> environmentService.stopEnvironment(environmentId));

        assertEquals(ErrorCode.SERVICE_UNAVAILABLE, exception.getErrorCode());
        verify(environmentStateService).markFailed(eq(environmentId), contains(EnvironmentProvisioner.UNAVAILABLE_MESSAGE));
        verify(environmentStateService, never()).markStopped(any());
    }

    @Test
    @DisplayName("容器编排可用：启动/停止成功写 running/stopped")
    void startAndStop_withDocker_updatesStatus() {
        when(provisionerSelector.active()).thenReturn(dockerProvisioner);
        when(dockerProvisioner.start(environment)).thenReturn(ProvisionResult.ok("cid-1"));
        when(dockerProvisioner.stop(environment)).thenReturn(ProvisionResult.ok("cid-1"));

        environmentService.startEnvironment(environmentId);
        verify(environmentStateService).markRunning(environmentId, "cid-1");

        environmentService.stopEnvironment(environmentId);
        verify(environmentStateService).markStopped(environmentId);
    }

    // ------------------------------------------------------------------ 快照

    @Test
    @DisplayName("快照 createdBy 使用真实当前用户")
    void createSnapshot_usesOperatorId() {
        UUID operatorId = UUID.randomUUID();
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));

        environmentService.createSnapshot(environmentId, "快照A", operatorId);

        ArgumentCaptor<EnvironmentSnapshot> captor = ArgumentCaptor.forClass(EnvironmentSnapshot.class);
        verify(environmentSnapshotRepository).save(captor.capture());
        assertEquals(operatorId, captor.getValue().getCreatedBy());
        assertEquals("快照A", captor.getValue().getDescription());
        assertNotNull(captor.getValue().getConfigSnapshot());
    }

    @Test
    @DisplayName("快照未传操作者时回退到环境创建人，而不是随机 UUID")
    void createSnapshot_withoutOperator_fallsBackToEnvironmentCreator() {
        when(environmentRepository.findById(environmentId)).thenReturn(Optional.of(environment));

        environmentService.createSnapshot(environmentId, "快照B", null);

        ArgumentCaptor<EnvironmentSnapshot> captor = ArgumentCaptor.forClass(EnvironmentSnapshot.class);
        verify(environmentSnapshotRepository).save(captor.capture());
        assertEquals(environment.getCreatedBy(), captor.getValue().getCreatedBy());
    }

    @Test
    @DisplayName("部署器信息透传给接口层")
    void provisionerInfo_returnsSelectorInfo() {
        when(provisionerSelector.info())
                .thenReturn(new com.vulnaudit.service.env.EnvironmentProvisionerInfo("noop", false, "当前部署未启用容器编排（未检测到 Docker）"));

        var info = environmentService.provisionerInfo();

        assertEquals("noop", info.type());
        assertFalse(info.available());
        assertTrue(info.message().contains("当前部署未启用容器编排"));
    }
}
