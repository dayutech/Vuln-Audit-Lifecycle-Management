package com.vulnaudit.service;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.Task;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 任务服务接口
 */
public interface TaskService {

    Task createTask(Task task);

    Optional<Task> findById(UUID id);

    PageResponse<Task> findAll(int page, int size, String keyword, String status, UUID assigneeId, UUID referenceId);

    List<Task> findByAssigneeId(UUID assigneeId);

    List<Task> findByReferenceId(UUID referenceId);

    Task updateTask(UUID id, Task task);

    Task updateStatus(UUID id, String status, UUID operatorId);

    void deleteTask(UUID id);

    void assignTask(UUID id, UUID assigneeId, UUID operatorId);

    void addComment(UUID taskId, UUID userId, String content);

    void addAttachment(UUID taskId, String fileName, String filePath, UUID uploadedBy);
}
