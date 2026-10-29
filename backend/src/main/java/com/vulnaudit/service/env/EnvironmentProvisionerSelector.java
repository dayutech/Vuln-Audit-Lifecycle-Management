package com.vulnaudit.service.env;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 部署器选择器
 *
 * <p>每次调用时根据 {@link DockerProvisioner#isAvailable()} 选择实际部署器：
 * Docker 可用则走 {@code DockerProvisioner}，否则退化为 {@link NoopProvisioner}
 * （所有部署操作明确失败，不再伪装成功）。
 */
@Slf4j
@Component
public class EnvironmentProvisionerSelector {

    private final DockerProvisioner dockerProvisioner;
    private final NoopProvisioner noopProvisioner;

    public EnvironmentProvisionerSelector(DockerProvisioner dockerProvisioner, NoopProvisioner noopProvisioner) {
        this.dockerProvisioner = dockerProvisioner;
        this.noopProvisioner = noopProvisioner;
    }

    /**
     * 当前生效的部署器
     */
    public EnvironmentProvisioner active() {
        if (dockerProvisioner.isAvailable()) {
            return dockerProvisioner;
        }
        return noopProvisioner;
    }

    /**
     * 运行信息，供接口返回给前端提示
     */
    public EnvironmentProvisionerInfo info() {
        EnvironmentProvisioner active = active();
        boolean available = active.isAvailable();
        log.debug("环境部署器: type={}, available={}, message={}", active.type(), available, active.availabilityMessage());
        return new EnvironmentProvisionerInfo(active.type(), available, active.availabilityMessage());
    }
}
