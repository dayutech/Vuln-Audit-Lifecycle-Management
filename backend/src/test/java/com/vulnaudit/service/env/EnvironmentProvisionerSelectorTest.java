package com.vulnaudit.service.env;

import com.vulnaudit.entity.Environment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 环境部署器选择器测试（t5 服务层补充覆盖）。
 *
 * <p>{@code EnvironmentServiceImplTest} 把选择器本身 mock 掉了，选择器的「按可用性切换」
 * 与「不做缓存、每次重新探测」这两条逻辑没有独立用例，这里补齐：
 * <ol>
 *   <li>Docker 不可用 → 选 Noop，且运行信息明确告知不可用原因；</li>
 *   <li>Docker 可用 → 选 Docker，运行信息透传类型与说明；</li>
 *   <li>不缓存可用性：同一选择器实例在同一进程内可随探测结果切换；</li>
 *   <li>Noop 的部署动作必须失败，不能伪装成功。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("环境部署器选择器测试")
class EnvironmentProvisionerSelectorTest {

    @Mock
    private DockerProvisioner dockerProvisioner;

    private NoopProvisioner noopProvisioner;

    private EnvironmentProvisionerSelector selector;

    private Environment environment;

    @BeforeEach
    void setUp() {
        noopProvisioner = new NoopProvisioner();
        selector = new EnvironmentProvisionerSelector(dockerProvisioner, noopProvisioner);
        environment = Environment.builder()
                .id(UUID.randomUUID())
                .name("选择器测试环境")
                .status("stopped")
                .build();
    }

    @Test
    @DisplayName("Docker 不可用：回落到 Noop，运行信息带不可用原因")
    void dockerUnavailable_selectsNoop() {
        when(dockerProvisioner.isAvailable()).thenReturn(false);

        EnvironmentProvisioner active = selector.active();
        assertSame(noopProvisioner, active, "Docker 不可用时应选 Noop 部署器");
        assertFalse(active.isAvailable(), "Noop 必须报告不可用");

        EnvironmentProvisionerInfo info = selector.info();
        assertEquals("noop", info.type());
        assertFalse(info.available());
        assertTrue(info.message() != null
                        && info.message().startsWith(EnvironmentProvisioner.UNAVAILABLE_MESSAGE),
                "不可用说明应以统一提示语开头，实际: " + info.message());
    }

    @Test
    @DisplayName("Docker 可用：选择 Docker 部署器并透传运行信息")
    void dockerAvailable_selectsDocker() {
        when(dockerProvisioner.isAvailable()).thenReturn(true);
        when(dockerProvisioner.type()).thenReturn("docker");
        when(dockerProvisioner.availabilityMessage()).thenReturn("Docker 守护进程可用");

        assertSame(dockerProvisioner, selector.active(), "Docker 可用时应选 Docker 部署器");

        EnvironmentProvisionerInfo info = selector.info();
        assertEquals("docker", info.type());
        assertTrue(info.available());
        assertEquals("Docker 守护进程可用", info.message());
    }

    @Test
    @DisplayName("可用性不缓存：同一实例上探测结果变化后立即切换部署器")
    void availabilityIsNotCached_switchesBetweenCalls() {
        when(dockerProvisioner.isAvailable()).thenReturn(false);
        assertSame(noopProvisioner, selector.active());

        when(dockerProvisioner.isAvailable()).thenReturn(true);
        assertSame(dockerProvisioner, selector.active(), "守护进程起来后应即时切换到 Docker");

        when(dockerProvisioner.isAvailable()).thenReturn(false);
        assertSame(noopProvisioner, selector.active(), "守护进程挂掉后应即时回落 Noop");

        verify(dockerProvisioner, times(3)).isAvailable();
    }

    @Test
    @DisplayName("Noop 的部署/启动/停止/删除全部失败且不改环境状态")
    void noopProvisioner_neverPretendsSuccess() {
        EnvironmentProvisioner noop = selector.active();

        for (ProvisionResult result : new ProvisionResult[]{
                noop.provision(environment),
                noop.start(environment),
                noop.stop(environment),
                noop.remove(environment)}) {
            assertFalse(result.success(), "Noop 不应返回成功");
            assertTrue(result.unavailable(), "Noop 结果应被判定为「未启用编排」");
            assertTrue(result.message() != null
                            && result.message().startsWith(EnvironmentProvisioner.UNAVAILABLE_MESSAGE),
                    "失败原因应以统一提示语开头，实际: " + result.message());
        }
        assertEquals("stopped", environment.getStatus(), "Noop 不应改写环境状态");
    }
}
