package com.vulnaudit.service.impl;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.Environment;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.entity.EnvironmentSnapshot;
import com.vulnaudit.repository.EnvironmentRepository;
import com.vulnaudit.repository.EnvironmentSnapshotRepository;
import com.vulnaudit.service.EnvironmentService;
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

    @Override
    @Transactional
    public Environment createEnvironment(Environment environment) {
        log.info("创建环境: name={}", environment.getName());
        return environmentRepository.save(environment);
    }

    @Override
    public Optional<Environment> findById(UUID id) {
        UUID requiredId = Objects.requireNonNull(id, "id must not be null");
        log.debug("查询环境: id={}", requiredId);
        return environmentRepository.findById(requiredId);
    }

    @Override
    public PageResponse<Environment> findAll(int page, int size, String keyword, UUID assetId) {
        log.debug("分页查询环境: page={}, size={}, keyword={}, assetId={}", page, size, keyword, assetId);
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Environment> environmentPage;
        
        if (assetId != null) {
            environmentPage = environmentRepository.findByAssetId(assetId, pageable);
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

        return environmentRepository.save(Objects.requireNonNull(existingEnvironment));
    }

    @Override
    @Transactional
    public void deleteEnvironment(UUID id) {
        UUID requiredId = Objects.requireNonNull(id, "id must not be null");
        log.info("删除环境: id={}", requiredId);
        environmentRepository.deleteById(requiredId);
    }

    @Override
    @Transactional
    public void startEnvironment(UUID id) {
        UUID requiredId = Objects.requireNonNull(id, "id must not be null");
        log.info("启动环境: id={}", requiredId);

        Environment environment = environmentRepository.findById(requiredId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        environment.setStatus("running");
        environment.setContainerId("container-" + requiredId.toString().substring(0, 8));
        environmentRepository.save(Objects.requireNonNull(environment));
    }

    @Override
    @Transactional
    public void stopEnvironment(UUID id) {
        UUID requiredId = Objects.requireNonNull(id, "id must not be null");
        log.info("停止环境: id={}", requiredId);

        Environment environment = environmentRepository.findById(requiredId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        environment.setStatus("stopped");
        environmentRepository.save(Objects.requireNonNull(environment));
    }

    @Override
    @Transactional
    public void createSnapshot(UUID id, String name) {
        UUID requiredId = Objects.requireNonNull(id, "id must not be null");
        String requiredName = Objects.requireNonNull(name, "name must not be null");
        log.info("创建快照: id={}, name={}", requiredId, requiredName);

        Environment environment = environmentRepository.findById(requiredId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        environment.setVersion(environment.getVersion() + 1);
        environmentRepository.save(Objects.requireNonNull(environment));
        
        EnvironmentSnapshot snapshot = new EnvironmentSnapshot();
        snapshot.setEnvironmentId(requiredId);
        snapshot.setVersion(environment.getVersion());
        snapshot.setDescription(requiredName);
        snapshot.setConfigSnapshot(environment.getConfig() != null ? environment.getConfig() : "{}");
        // Using a dummy user id for now as context is empty
        snapshot.setCreatedBy(UUID.randomUUID());
        environmentSnapshotRepository.save(snapshot);
    }

    @Override
    @Transactional
    public void deployEnvironment(UUID id) {
        UUID requiredId = Objects.requireNonNull(id, "id must not be null");
        log.info("部署环境: id={}", requiredId);
        Environment environment = environmentRepository.findById(requiredId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        
        try {
            // Update status to deploying
            environment.setStatus("deploying");
            environmentRepository.saveAndFlush(Objects.requireNonNull(environment));
            
            // Validate configuration before deployment
            if (environment.getConfig() == null || environment.getConfig().trim().isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "环境配置不能为空");
            }
            
            // Simulate deployment process logic
            log.info("开始部署环境过程, 环境ID: {}", requiredId);
            
            // Assign container id if not present
            if (environment.getContainerId() == null || environment.getContainerId().isEmpty()) {
                environment.setContainerId("container-" + UUID.randomUUID().toString().substring(0, 8));
            }
            
            // Update status to running upon successful deployment
            environment.setStatus("running");
            environmentRepository.save(Objects.requireNonNull(environment));
            log.info("环境部署成功: id={}", requiredId);
        } catch (Exception e) {
            log.error("环境部署失败: id={}, error={}", requiredId, e.getMessage());
            // Update status to failed on error
            environment.setStatus("failed");
            environmentRepository.save(Objects.requireNonNull(environment));
            
            if (e instanceof BusinessException) {
                throw e;
            }
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "环境部署失败: " + e.getMessage());
        }
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
