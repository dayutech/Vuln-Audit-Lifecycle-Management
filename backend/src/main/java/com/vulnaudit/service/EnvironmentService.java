package com.vulnaudit.service;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.Environment;

import java.util.Optional;
import java.util.UUID;

/**
 * 环境服务接口
 * 
 * 注意：此模块为空实现，待后续需求明确后再实现
 */
public interface EnvironmentService {

    /**
     * 创建环境（空实现）
     */
    Environment createEnvironment(Environment environment);

    /**
     * 根据ID查询环境（空实现）
     */
    Optional<Environment> findById(UUID id);

    /**
     * 分页查询环境列表（空实现）
     */
    PageResponse<Environment> findAll(int page, int size, String keyword);

    /**
     * 更新环境（空实现）
     */
    Environment updateEnvironment(UUID id, Environment environment);

    /**
     * 删除环境（空实现）
     */
    void deleteEnvironment(UUID id);

    /**
     * 启动环境（空实现）
     */
    void startEnvironment(UUID id);

    /**
     * 停止环境（空实现）
     */
    void stopEnvironment(UUID id);

    /**
     * 创建快照（空实现）
     */
    void createSnapshot(UUID id, String name);
}
