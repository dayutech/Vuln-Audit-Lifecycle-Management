package com.vulnaudit.service;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.Environment;
import com.vulnaudit.entity.EnvironmentSnapshot;
import com.vulnaudit.service.env.EnvironmentProvisionerInfo;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 环境服务接口
 *
 * <p>部署/启动/停止会调用可插拔的 {@code EnvironmentProvisioner}：
 * 容器编排不可用时状态写为 {@code failed} 并抛出业务异常（503），不再伪装成功。
 */
public interface EnvironmentService {

    @Nonnull
    Environment createEnvironment(@Nonnull Environment environment);

    Optional<Environment> findById(@Nullable UUID id);

    @Nonnull
    PageResponse<Environment> findAll(int page, int size, @Nullable String keyword, @Nullable UUID assetId, @Nullable UUID assetVersionId);

    @Nonnull
    Environment updateEnvironment(@Nullable UUID id, @Nonnull Environment environment);

    void deleteEnvironment(@Nullable UUID id);

    void startEnvironment(@Nullable UUID id);

    void stopEnvironment(@Nullable UUID id);

    /**
     * 创建快照
     *
     * @param id         环境ID
     * @param name       快照名称
     * @param operatorId 操作者ID（真实当前用户，用于 created_by）
     */
    void createSnapshot(@Nullable UUID id, @Nullable String name, @Nullable UUID operatorId);

    void deployEnvironment(@Nullable UUID id);

    @Nonnull
    List<EnvironmentSnapshot> getSnapshots(@Nullable UUID id);

    void rollbackSnapshot(@Nullable UUID id, @Nullable UUID snapshotId);

    /**
     * 当前生效的部署器类型与可用性，供详情接口返回给前端提示
     */
    @Nonnull
    EnvironmentProvisionerInfo provisionerInfo();
}
