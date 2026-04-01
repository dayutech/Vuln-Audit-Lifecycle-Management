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
        log.debug("查询环境: id={}", id);
        return environmentRepository.findById(id);
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
        log.info("更新环境: id={}", id);

        Environment existingEnvironment = environmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        if (environment.getName() != null) {
            existingEnvironment.setName(environment.getName());
        }
        if (environment.getConfig() != null) {
            existingEnvironment.setConfig(environment.getConfig());
        }
        if (environment.getStatus() != null) {
            existingEnvironment.setStatus(environment.getStatus());
        }

        return environmentRepository.save(existingEnvironment);
    }

    @Override
    @Transactional
    public void deleteEnvironment(UUID id) {
        log.info("删除环境: id={}", id);
        environmentRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void startEnvironment(UUID id) {
        log.info("启动环境: id={}", id);

        Environment environment = environmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        environment.setStatus("running");
        environment.setContainerId("container-" + id.toString().substring(0, 8));
        environmentRepository.save(environment);
    }

    @Override
    @Transactional
    public void stopEnvironment(UUID id) {
        log.info("停止环境: id={}", id);

        Environment environment = environmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        environment.setStatus("stopped");
        environmentRepository.save(environment);
    }

    @Override
    @Transactional
    public void createSnapshot(UUID id, String name) {
        log.info("创建快照: id={}, name={}", id, name);

        Environment environment = environmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        environment.setVersion(environment.getVersion() + 1);
        environmentRepository.save(environment);
        
        EnvironmentSnapshot snapshot = new EnvironmentSnapshot();
        snapshot.setEnvironmentId(id);
        snapshot.setVersion(environment.getVersion());
        snapshot.setDescription(name);
        snapshot.setConfigSnapshot(environment.getConfig() != null ? environment.getConfig() : "{}");
        // Using a dummy user id for now as context is empty
        snapshot.setCreatedBy(UUID.randomUUID());
        environmentSnapshotRepository.save(snapshot);
    }

    @Override
    @Transactional
    public void deployEnvironment(UUID id) {
        log.info("部署环境: id={}", id);
        Environment environment = environmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        
        try {
            // Update status to deploying
            environment.setStatus("deploying");
            environmentRepository.saveAndFlush(environment);
            
            // Validate configuration before deployment
            if (environment.getConfig() == null || environment.getConfig().trim().isEmpty()) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "环境配置不能为空");
            }
            
            // Simulate deployment process logic
            log.info("开始部署环境过程, 环境ID: {}", id);
            
            // Assign container id if not present
            if (environment.getContainerId() == null || environment.getContainerId().isEmpty()) {
                environment.setContainerId("container-" + UUID.randomUUID().toString().substring(0, 8));
            }
            
            // Update status to running upon successful deployment
            environment.setStatus("running");
            environmentRepository.save(environment);
            log.info("环境部署成功: id={}", id);
        } catch (Exception e) {
            log.error("环境部署失败: id={}, error={}", id, e.getMessage());
            // Update status to failed on error
            environment.setStatus("failed");
            environmentRepository.save(environment);
            
            if (e instanceof BusinessException) {
                throw e;
            }
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "环境部署失败: " + e.getMessage());
        }
    }

    @Override
    public java.util.List<EnvironmentSnapshot> getSnapshots(UUID id) {
        log.debug("获取环境快照列表: id={}", id);
        return environmentSnapshotRepository.findByEnvironmentIdOrderByVersionDesc(id);
    }

    @Override
    @Transactional
    public void rollbackSnapshot(UUID id, UUID snapshotId) {
        log.info("回滚快照: environmentId={}, snapshotId={}", id, snapshotId);
        Environment environment = environmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        EnvironmentSnapshot snapshot = environmentSnapshotRepository.findById(snapshotId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        
        if (!snapshot.getEnvironmentId().equals(id)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "快照不属于该环境");
        }
        
        environment.setConfig(snapshot.getConfigSnapshot());
        environment.setVersion(environment.getVersion() + 1);
        environmentRepository.save(environment);
    }
}