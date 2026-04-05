package com.vulnaudit.service;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.Task;
import com.vulnaudit.entity.TaskAttachment;
import com.vulnaudit.entity.TaskComment;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.math.BigDecimal;
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
    PageResponse<Task> findAll(int page, int size, @Nullable String keyword, @Nullable String status, @Nullable UUID assigneeId, @Nullable UUID referenceId, @Nullable UUID assetId, @Nullable UUID assetVersionId, @Nullable UUID operatorId, boolean managerAccess);

    @Nonnull
    List<Task> findByAssigneeId(@Nullable UUID assigneeId);

    @Nonnull
    List<Task> findByReferenceId(@Nullable UUID referenceId);

    @Nonnull
    Task updateTask(@Nullable UUID id, @Nonnull Task task);

    @Nonnull
    Task updateStatus(@Nullable UUID id, @Nullable String status, @Nullable UUID operatorId, boolean managerAccess);

    @Nonnull
    Task submitForReview(@Nullable UUID id, @Nullable UUID operatorId);

    @Nonnull
    Task approveReview(@Nullable UUID id, @Nullable UUID operatorId, @Nullable String signature, @Nullable String comment);

    @Nonnull
    Task rejectReview(@Nullable UUID id, @Nullable UUID operatorId, @Nullable String signature, @Nullable String comment);

    void deleteTask(@Nullable UUID id);

    void assignTask(@Nullable UUID id, @Nullable UUID assigneeId, @Nullable UUID operatorId, boolean managerAccess);

    void addComment(@Nullable UUID taskId, @Nullable UUID userId, @Nullable String content, @Nullable BigDecimal spentHours, @Nullable String workType, @Nullable String evidencePath);

    void addAttachment(@Nullable UUID taskId, @Nullable String fileName, @Nullable String filePath, @Nullable UUID uploadedBy);

    void addCollaborator(@Nullable UUID taskId, @Nullable UUID userId, @Nullable UUID operatorId);

    void removeCollaborator(@Nullable UUID taskId, @Nullable UUID userId);

    boolean isCollaborator(@Nullable UUID taskId, @Nullable UUID userId);

    @Nonnull
    List<UUID> findCollaboratorUserIds(@Nullable UUID taskId);

    @Nonnull
    List<TaskComment> findCommentsByTaskId(@Nullable UUID taskId);

    @Nonnull
    List<TaskAttachment> findAttachmentsByTaskId(@Nullable UUID taskId);
}
