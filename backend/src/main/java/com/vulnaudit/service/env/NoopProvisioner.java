package com.vulnaudit.service.env;

import com.vulnaudit.entity.Environment;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 空实现部署器：宿主机没有容器运行时时使用
 *
 * <p>行为约定：一切部署/启停操作都返回失败，状态由上层写入 {@code failed}，
 * 绝不返回成功，避免前端误以为环境已就绪。
 */
@Slf4j
@Component
public class NoopProvisioner implements EnvironmentProvisioner {

    @Override
    public String type() {
        return "noop";
    }

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public String availabilityMessage() {
        return EnvironmentProvisioner.UNAVAILABLE_MESSAGE + "（未检测到可用的 Docker 守护进程或 docker CLI）";
    }

    @Override
    public ProvisionResult provision(Environment environment) {
        log.warn("部署器不可用，拒绝部署环境: environmentId={}",
                environment == null ? null : environment.getId());
        return ProvisionResult.fail(availabilityMessage());
    }

    @Override
    public ProvisionResult start(Environment environment) {
        log.warn("部署器不可用，拒绝启动环境: environmentId={}",
                environment == null ? null : environment.getId());
        return ProvisionResult.fail(availabilityMessage());
    }

    @Override
    public ProvisionResult stop(Environment environment) {
        log.warn("部署器不可用，拒绝停止环境: environmentId={}",
                environment == null ? null : environment.getId());
        return ProvisionResult.fail(availabilityMessage());
    }

    @Override
    public ProvisionResult remove(Environment environment) {
        return ProvisionResult.fail(availabilityMessage());
    }
}
