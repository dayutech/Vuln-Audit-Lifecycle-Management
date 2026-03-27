package com.vulnaudit.service.impl;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.Environment;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.EnvironmentRepository;
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
    public PageResponse<Environment> findAll(int page, int size, String keyword) {
        log.debug("分页查询环境: page={}, size={}, keyword={}", page, size, keyword);
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Environment> environmentPage = environmentRepository.findAll(pageable);

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
    }
}