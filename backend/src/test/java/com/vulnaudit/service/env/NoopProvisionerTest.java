package com.vulnaudit.service.env;

import com.vulnaudit.entity.Environment;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Noop 部署器测试：容器编排不可用时的行为必须是「明确失败」，绝不允许伪装成功
 */
@DisplayName("Noop 部署器测试")
class NoopProvisionerTest {

    private final NoopProvisioner provisioner = new NoopProvisioner();

    private Environment environment() {
        return Environment.builder()
                .id(UUID.randomUUID())
                .name("测试环境")
                .config("{\"image\":\"nginx:alpine\"}")
                .build();
    }

    @Test
    @DisplayName("始终不可用，类型为 noop")
    void typeAndAvailability() {
        assertFalse(provisioner.isAvailable());
        assertEquals("noop", provisioner.type());
        assertTrue(provisioner.availabilityMessage().startsWith(EnvironmentProvisioner.UNAVAILABLE_MESSAGE));
    }

    @Test
    @DisplayName("部署返回失败：不产生 containerId，消息带统一提示语")
    void provisionAlwaysFails() {
        ProvisionResult result = provisioner.provision(environment());

        assertFalse(result.success());
        assertTrue(result.unavailable());
        assertNull(result.containerId());
        assertTrue(result.message().startsWith(EnvironmentProvisioner.UNAVAILABLE_MESSAGE));
    }

    @Test
    @DisplayName("启动返回失败")
    void startAlwaysFails() {
        ProvisionResult result = provisioner.start(environment());

        assertFalse(result.success());
        assertTrue(result.unavailable());
        assertNull(result.containerId());
    }

    @Test
    @DisplayName("停止返回失败")
    void stopAlwaysFails() {
        ProvisionResult result = provisioner.stop(environment());

        assertFalse(result.success());
        assertTrue(result.unavailable());
    }

    @Test
    @DisplayName("删除容器返回失败（无容器可删）")
    void removeAlwaysFails() {
        ProvisionResult result = provisioner.remove(environment());

        assertFalse(result.success());
        assertTrue(result.unavailable());
    }
}
