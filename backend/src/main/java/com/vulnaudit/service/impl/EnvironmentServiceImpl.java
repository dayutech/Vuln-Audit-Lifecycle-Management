package com.vulnaudit.service.impl;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.Environment;
import com.vulnaudit.repository.EnvironmentRepository;
import com.vulnaudit.service.EnvironmentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * 环境服务实现类
 * 
 * 注意：此模块为空实现，待后续需求明确后再实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnvironmentServiceImpl implements EnvironmentService {

    private final EnvironmentRepository environmentRepository;

    @Override
    public Environment createEnvironment(Environment environment) {
        log.info("创建环境（空实现）: name={}", environment.getName());
        throw new UnsupportedOperationException("环境管理功能暂未实现");
    }

    @Override
    public Optional<Environment> findById(UUID id) {
        log.debug("查询环境（空实现）: id={}", id);
        return environmentRepository.findById(id);
    }

    @Override
    public PageResponse<Environment> findAll(int page, int size, String keyword) {
        log.debug("分页查询环境（空实现）: page={}, size={}", page, size);
        throw new UnsupportedOperationException("环境管理功能暂未实现");
    }

    @Override
    public Environment updateEnvironment(UUID id, Environment environment) {
        log.info("更新环境（空实现）: id={}", id);
        throw new UnsupportedOperationException("环境管理功能暂未实现");
    }

    @Override
    public void deleteEnvironment(UUID id) {
        log.info("删除环境（空实现）: id={}", id);
        throw new UnsupportedOperationException("环境管理功能暂未实现");
    }

    @Override
    public void startEnvironment(UUID id) {
        log.info("启动环境（空实现）: id={}", id);
        throw new UnsupportedOperationException("环境管理功能暂未实现");
    }

    @Override
    public void stopEnvironment(UUID id) {
        log.info("停止环境（空实现）: id={}", id);
        throw new UnsupportedOperationException("环境管理功能暂未实现");
    }

    @Override
    public void createSnapshot(UUID id, String name) {
        log.info("创建快照（空实现）: id={}, name={}", id, name);
        throw new UnsupportedOperationException("环境管理功能暂未实现");
    }
}
