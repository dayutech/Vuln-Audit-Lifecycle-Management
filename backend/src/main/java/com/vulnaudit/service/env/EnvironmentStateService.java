package com.vulnaudit.service.env;

import com.vulnaudit.entity.Environment;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.EnvironmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * 环境状态写入服务
 *
 * <p>部署/启停过程中会调用外部容器编排（耗时不可控），因此这些状态更新必须落在
 * 独立短事务里，避免长事务持锁；同时保证「失败状态」能够在外层抛错回滚后仍然保留。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnvironmentStateService {

    /**
     * 失败原因字段最大长度（与 environments.fail_reason 列一致）
     */
    private static final int FAIL_REASON_MAX_LENGTH = 500;

    private final EnvironmentRepository environmentRepository;

    @Transactional(readOnly = true)
    public Optional<Environment> find(UUID id) {
        return id == null ? Optional.empty() : environmentRepository.findById(id);
    }

    /**
     * 标记为 provisioning（部署中），并清除历史失败原因
     */
    @Transactional
    public Environment markProvisioning(UUID id) {
        Environment environment = load(id);
        environment.setStatus("provisioning");
        environment.setFailReason(null);
        log.info("环境状态更新: id={}, status=provisioning", id);
        return environmentRepository.save(environment);
    }

    /**
     * 标记为 running，并记录容器ID
     */
    @Transactional
    public Environment markRunning(UUID id, String containerId) {
        Environment environment = load(id);
        environment.setStatus("running");
        environment.setContainerId(containerId);
        environment.setFailReason(null);
        log.info("环境状态更新: id={}, status=running, containerId={}", id, containerId);
        return environmentRepository.save(environment);
    }

    /**
     * 标记为 failed，并记录失败原因
     */
    @Transactional
    public Environment markFailed(UUID id, String reason) {
        Environment environment = load(id);
        String normalized = reason == null || reason.isBlank() ? "未知失败原因" : reason.trim();
        environment.setStatus("failed");
        environment.setFailReason(normalized.length() > FAIL_REASON_MAX_LENGTH
                ? normalized.substring(0, FAIL_REASON_MAX_LENGTH) : normalized);
        log.warn("环境状态更新: id={}, status=failed, reason={}", id, environment.getFailReason());
        return environmentRepository.save(environment);
    }

    /**
     * 标记为 stopped
     */
    @Transactional
    public Environment markStopped(UUID id) {
        Environment environment = load(id);
        environment.setStatus("stopped");
        environment.setFailReason(null);
        log.info("环境状态更新: id={}, status=stopped", id);
        return environmentRepository.save(environment);
    }

    private Environment load(UUID id) {
        return environmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
    }
}
