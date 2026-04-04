package com.vulnaudit.service;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.Task;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 任务服务接口
 */
public interface TaskService {

    @Nonnull
    Task createTask(@Nonnull Task task);

    Optional<Task> findById(@Nullable UUID id);

    @Nonnull
    PageResponse<Task> findAll(int page, int size, @Nullable String keyword, @Nullable String status, @Nullable UUID assigneeId, @Nullable UUID referenceId, @Nullable UUID assetId, @Nullable UUID assetVersionId);

    @Nonnull
    List<Task> findByAssigneeId(@Nullable UUID assigneeId);

    @Nonnull
    List<Task> findByReferenceId(@Nullable UUID referenceId);

    @Nonnull
    Task updateTask(@Nullable UUID id, @Nonnull Task task);

    @Nonnull
    Task updateStatus(@Nullable UUID id, @Nullable String status, @Nullable UUID operatorId);

    void deleteTask(@Nullable UUID id);

    void assignTask(@Nullable UUID id, @Nullable UUID assigneeId, @Nullable UUID operatorId);

    void addComment(@Nullable UUID taskId, @Nullable UUID userId, @Nullable String content);

    void addAttachment(@Nullable UUID taskId, @Nullable String fileName, @Nullable String filePath, @Nullable UUID uploadedBy);
}
