package com.vulnaudit.service.env;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vulnaudit.config.EnvironmentProvisionProperties;
import com.vulnaudit.entity.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Docker 部署器可用性探测测试
 *
 * <p>测试环境没有真实的 Docker 守护进程，因此断言「不可用时明确失败」的路径；
 * 通过把命令行指向不存在的可执行文件、socket 指向不存在的路径保证结果与宿主机无关。
 */
@DisplayName("Docker 部署器探测测试")
class DockerProvisionerTest {

    private EnvironmentProvisionProperties properties;
    private DockerProvisioner provisioner;

    @BeforeEach
    void setUp() {
        properties = new EnvironmentProvisionProperties();
        properties.getDocker().setCommand("definitely-not-a-real-docker-cli");
        properties.getDocker().setSocketPath("/nonexistent/docker.sock");
        properties.getDocker().setAvailabilityCacheMs(0);
        provisioner = new DockerProvisioner(properties, new ObjectMapper());
    }

    private Environment environment() {
        return Environment.builder()
                .id(UUID.randomUUID())
                .name("测试环境")
                .config("{\"image\":\"nginx:alpine\"}")
                .build();
    }

    @Test
    @DisplayName("关闭开关时不可用，原因说明配置项")
    void disabled_isNotAvailable() {
        properties.getDocker().setEnabled(false);

        assertFalse(provisioner.isAvailable());
        assertTrue(provisioner.availabilityMessage().contains("app.env.docker.enabled=false"));
    }

    @Test
    @DisplayName("无 Docker socket 时不可用")
    void missingSocket_isNotAvailable() {
        assertFalse(provisioner.isAvailable());
        assertTrue(provisioner.availabilityMessage().startsWith(EnvironmentProvisioner.UNAVAILABLE_MESSAGE));
        assertTrue(provisioner.availabilityMessage().contains("（"));
    }

    @Test
    @DisplayName("不可用时部署直接失败，不执行 docker 命令")
    void provisionWhenUnavailable_returnsFailure() {
        ProvisionResult result = provisioner.provision(environment());

        assertFalse(result.success());
        assertTrue(result.unavailable());
        assertTrue(result.message().startsWith(EnvironmentProvisioner.UNAVAILABLE_MESSAGE));
    }

    @Test
    @DisplayName("不可用时启停同样失败")
    void startAndStopWhenUnavailable_returnFailure() {
        assertFalse(provisioner.start(environment()).success());
        assertFalse(provisioner.stop(environment()).success());
        assertFalse(provisioner.remove(environment()).success());
    }

    @Test
    @DisplayName("环境为空时参数校验失败")
    void provisionWithNullEnvironment_fails() {
        ProvisionResult result = provisioner.provision(null);

        assertFalse(result.success());
        assertEquals("环境信息不完整，无法部署", result.message());
    }
}
