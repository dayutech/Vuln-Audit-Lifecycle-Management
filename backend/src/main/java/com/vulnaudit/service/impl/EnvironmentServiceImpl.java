package com.vulnaudit.service.impl;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.Environment;
import com.vulnaudit.entity.AssetVersion;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.entity.EnvironmentSnapshot;
import com.vulnaudit.repository.AssetVersionRepository;
import com.vulnaudit.repository.EnvironmentRepository;
import com.vulnaudit.repository.EnvironmentSnapshotRepository;
import com.vulnaudit.service.EnvironmentService;
import com.vulnaudit.service.env.EnvironmentProvisioner;
import com.vulnaudit.service.env.EnvironmentProvisionerInfo;
import com.vulnaudit.service.env.EnvironmentProvisionerSelector;
import com.vulnaudit.service.env.EnvironmentStateService;
import com.vulnaudit.service.env.ProvisionResult;
import com.vulnaudit.util.Paging;
import jakarta.annotation.Nonnull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 环境服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnvironmentServiceImpl implements EnvironmentService {

    private final EnvironmentRepository environmentRepository;
    private final EnvironmentSnapshotRepository environmentSnapshotRepository;
    private final AssetVersionRepository assetVersionRepository;
    private final EnvironmentProvisionerSelector provisionerSelector;
    private final EnvironmentStateService environmentStateService;

    @Override
    @Transactional
    public Environment createEnvironment(Environment environment) {
        Environment requiredEnvironment = Objects.requireNonNull(environment, "environment must not be null");
        log.info("创建环境: name={}", requiredEnvironment.getName());
        requiredEnvironment.setAssetVersionId(resolveAssetVersionId(requiredEnvironment.getAssetId(), requiredEnvironment.getAssetVersionId()));
        return environmentRepository.save(requiredEnvironment);
    }

    @Override
    public Optional<Environment> findById(UUID id) {
        UUID requiredId = Objects.requireNonNull(id, "id must not be null");
        log.debug("查询环境: id={}", requiredId);
        return environmentRepository.findById(requiredId);
    }

    @Override
    public PageResponse<Environment> findAll(int page, int size, String keyword, UUID assetId, UUID assetVersionId) {
        log.debug("分页查询环境: page={}, size={}, keyword={}, assetId={}, assetVersionId={}", page, size, keyword, assetId, assetVersionId);
        Pageable pageable = PageRequest.of(Paging.page(page), Paging.size(size), Sort.by("createdAt").descending());
        Page<Environment> environmentPage;
        
        if (assetId != null) {
            if (assetVersionId != null) {
                environmentPage = environmentRepository.findByAssetIdAndAssetVersionId(assetId, assetVersionId, pageable);
                if (environmentPage.isEmpty()) {
                    environmentPage = environmentRepository.findByAssetIdAndAssetVersionId(assetId, null, pageable);
                }
            } else {
                environmentPage = environmentRepository.findByAssetIdAndAssetVersionId(assetId, null, pageable);
            }
        } else {
            environmentPage = environmentRepository.findAll(pageable);
        }

        return PageResponse.<Environment>builder()
                .content(environmentPage.getContent())
                .totalElements(environmentPage.getTotalElements())
                .totalPages(environmentPage.getTotalPages())
                .currentPage(environmentPage.getNumber())
                .pageSize(environmentPage.getSize())
                .build();
    }

    @Override
    @Transactional
    public Environment updateEnvironment(UUID id, Environment environment) {
        UUID requiredId = Objects.requireNonNull(id, "id must not be null");
        Environment requiredEnvironment = Objects.requireNonNull(environment, "environment must not be null");
        log.info("更新环境: id={}", requiredId);

        Environment existingEnvironment = environmentRepository.findById(requiredId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (requiredEnvironment.getName() != null) {
            existingEnvironment.setName(requiredEnvironment.getName());
        }
        if (requiredEnvironment.getConfig() != null) {
            existingEnvironment.setConfig(requiredEnvironment.getConfig());
        }
        if (requiredEnvironment.getStatus() != null) {
            existingEnvironment.setStatus(requiredEnvironment.getStatus());
        }
        if (requiredEnvironment.getAssetId() != null) {
            existingEnvironment.setAssetId(requiredEnvironment.getAssetId());
        }
        if (requiredEnvironment.getTemplateId() != null) {
            existingEnvironment.setTemplateId(requiredEnvironment.getTemplateId());
        }
        if (requiredEnvironment.getAssetVersionId() != null || requiredEnvironment.getAssetId() != null) {
            UUID targetAssetId = existingEnvironment.getAssetId();
            existingEnvironment.setAssetVersionId(resolveAssetVersionId(targetAssetId, requiredEnvironment.getAssetVersionId()));
        }

        return environmentRepository.save(Objects.requireNonNull(existingEnvironment));
    }

    private UUID resolveAssetVersionId(UUID assetId, UUID assetVersionId) {
        if (assetId == null) {
            return null;
        }
        if (assetVersionId != null) {
            boolean exists = assetVersionRepository.findByIdAndAssetId(assetVersionId, assetId).isPresent();
            if (!exists) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "资产版本不存在或不属于当前资产");
            }
            return assetVersionId;
        }
        return assetVersionRepository.findByAssetIdAndIsDefaultTrue(assetId)
                .map(AssetVersion::getId)
                .orElse(null);
    }

    @Override
    @Transactional
    public void deleteEnvironment(UUID id) {
        UUID requiredId = Objects.requireNonNull(id, "id must not be null");
        log.info("删除环境: id={}", requiredId);

        // 尽力清理容器，失败不影响环境记录删除
        environmentRepository.findById(requiredId)
                .filter(environment -> environment.getContainerId() != null && !environment.getContainerId().isBlank())
                .ifPresent(environment -> {
                    try {
                        ProvisionResult result = provisionerSelector.active().remove(environment);
                        if (!result.success()) {
                            log.warn("删除环境时清理容器未成功: id={}, reason={}", requiredId, result.message());
                        }
                    } catch (RuntimeException e) {
                        log.warn("删除环境时清理容器异常: id={}, error={}", requiredId, e.getMessage());
                    }
                });

        environmentRepository.deleteById(requiredId);
    }

    @Override
    public void startEnvironment(UUID id) {
        UUID requiredId = Objects.requireNonNull(id, "id must not be null");
        log.info("启动环境: id={}", requiredId);

        Environment environment = environmentStateService.find(requiredId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        EnvironmentProvisioner provisioner = provisionerSelector.active();
        ProvisionResult result = provisioner.start(environment);
        if (!result.success()) {
            String reason = result.message() == null ? "环境启动失败" : result.message();
            environmentStateService.markFailed(requiredId, reason);
            log.warn("环境启动失败: id={}, provisioner={}, reason={}", requiredId, provisioner.type(), reason);
            ErrorCode code = result.unavailable() ? ErrorCode.SERVICE_UNAVAILABLE : ErrorCode.BAD_REQUEST;
            throw new BusinessException(code, result.unavailable() ? EnvironmentProvisioner.UNAVAILABLE_MESSAGE : reason);
        }

        String containerId = result.containerId() != null ? result.containerId() : environment.getContainerId();
        environmentStateService.markRunning(requiredId, containerId);
        log.info("环境启动成功: id={}, containerId={}", requiredId, containerId);
    }

    @Override
    public void stopEnvironment(UUID id) {
        UUID requiredId = Objects.requireNonNull(id, "id must not be null");
        log.info("停止环境: id={}", requiredId);

        Environment environment = environmentStateService.find(requiredId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        EnvironmentProvisioner provisioner = provisionerSelector.active();
        ProvisionResult result = provisioner.stop(environment);
        if (!result.success()) {
            String reason = result.message() == null ? "环境停止失败" : result.message();
            environmentStateService.markFailed(requiredId, reason);
            log.warn("环境停止失败: id={}, provisioner={}, reason={}", requiredId, provisioner.type(), reason);
            ErrorCode code = result.unavailable() ? ErrorCode.SERVICE_UNAVAILABLE : ErrorCode.BAD_REQUEST;
            throw new BusinessException(code, result.unavailable() ? EnvironmentProvisioner.UNAVAILABLE_MESSAGE : reason);
        }

        environmentStateService.markStopped(requiredId);
        log.info("环境停止成功: id={}", requiredId);
    }

    @Override
    @Transactional
    public void createSnapshot(UUID id, String name, UUID operatorId) {
        UUID requiredId = Objects.requireNonNull(id, "id must not be null");
        String requiredName = Objects.requireNonNull(name, "name must not be null");
        log.info("创建快照: id={}, name={}, operatorId={}", requiredId, requiredName, operatorId);

        Environment environment = environmentRepository.findById(requiredId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        UUID createdBy = operatorId != null ? operatorId : environment.getCreatedBy();
        if (createdBy == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无法确定快照创建人，请重新登录后重试");
        }

        environment.setVersion(environment.getVersion() + 1);
        environmentRepository.save(Objects.requireNonNull(environment));

        EnvironmentSnapshot snapshot = new EnvironmentSnapshot();
        snapshot.setEnvironmentId(requiredId);
        snapshot.setVersion(environment.getVersion());
        snapshot.setDescription(requiredName);
        snapshot.setConfigSnapshot(environment.getConfig() != null ? environment.getConfig() : "{}");
        // 使用真实当前用户，修复此前 UUID.randomUUID() 产生的脏数据
        snapshot.setCreatedBy(createdBy);
        environmentSnapshotRepository.save(snapshot);
    }

    @Override
    public void deployEnvironment(UUID id) {
        UUID requiredId = Objects.requireNonNull(id, "id must not be null");
        log.info("部署环境: id={}", requiredId);

        Environment environment = environmentStateService.markProvisioning(requiredId);

        if (environment.getConfig() == null || environment.getConfig().trim().isEmpty()) {
            environmentStateService.markFailed(requiredId, "环境配置不能为空");
            throw new BusinessException(ErrorCode.BAD_REQUEST, "环境配置不能为空");
        }

        EnvironmentProvisioner provisioner = provisionerSelector.active();
        ProvisionResult result = provisioner.provision(environment);
        if (!result.success()) {
            String reason = result.message() == null ? "环境部署失败" : result.message();
            environmentStateService.markFailed(requiredId, reason);
            log.warn("环境部署失败: id={}, provisioner={}, reason={}", requiredId, provisioner.type(), reason);
            if (result.unavailable()) {
                // 明确告知前端未启用容器编排，绝不返回成功
                throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE, EnvironmentProvisioner.UNAVAILABLE_MESSAGE);
            }
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, reason);
        }

        environmentStateService.markRunning(requiredId, result.containerId());
        log.info("环境部署成功: id={}, provisioner={}, containerId={}",
                requiredId, provisioner.type(), result.containerId());
    }

    @Override
    @Nonnull
    public EnvironmentProvisionerInfo provisionerInfo() {
        return provisionerSelector.info();
    }

    @Override
    public java.util.List<EnvironmentSnapshot> getSnapshots(UUID id) {
        UUID requiredId = Objects.requireNonNull(id, "id must not be null");
        log.debug("获取环境快照列表: id={}", requiredId);
        return environmentSnapshotRepository.findByEnvironmentIdOrderByVersionDesc(requiredId);
    }

    @Override
    @Transactional
    public void rollbackSnapshot(UUID id, UUID snapshotId) {
        UUID requiredId = Objects.requireNonNull(id, "id must not be null");
        UUID requiredSnapshotId = Objects.requireNonNull(snapshotId, "snapshotId must not be null");
        log.info("回滚快照: environmentId={}, snapshotId={}", requiredId, requiredSnapshotId);
        Environment environment = environmentRepository.findById(requiredId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        EnvironmentSnapshot snapshot = environmentSnapshotRepository.findById(requiredSnapshotId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        
        if (!snapshot.getEnvironmentId().equals(requiredId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "快照不属于该环境");
        }
        
        environment.setConfig(snapshot.getConfigSnapshot());
        environment.setVersion(environment.getVersion() + 1);
        environmentRepository.save(Objects.requireNonNull(environment));
    }
}

