package com.vulnaudit.service;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.Environment;
import com.vulnaudit.entity.EnvironmentSnapshot;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 环境服务接口
 *
 * 注意：此模块为空实现，待后续需求明确后再实现
 */
public interface EnvironmentService {

    @Nonnull
    Environment createEnvironment(@Nonnull Environment environment);

    Optional<Environment> findById(@Nullable UUID id);

    @Nonnull
    PageResponse<Environment> findAll(int page, int size, @Nullable String keyword, @Nullable UUID assetId);

    @Nonnull
    Environment updateEnvironment(@Nullable UUID id, @Nonnull Environment environment);

    void deleteEnvironment(@Nullable UUID id);

    void startEnvironment(@Nullable UUID id);

    void stopEnvironment(@Nullable UUID id);

    void createSnapshot(@Nullable UUID id, @Nullable String name);

    void deployEnvironment(@Nullable UUID id);

    @Nonnull
    List<EnvironmentSnapshot> getSnapshots(@Nullable UUID id);

    void rollbackSnapshot(@Nullable UUID id, @Nullable UUID snapshotId);
}