package com.vulnaudit.service.impl;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.Task;
import com.vulnaudit.entity.TaskAttachment;
import com.vulnaudit.entity.TaskComment;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.TaskAttachmentRepository;
import com.vulnaudit.repository.TaskCommentRepository;
import com.vulnaudit.repository.TaskRepository;
import com.vulnaudit.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import jakarta.annotation.Nullable;

/**
 * 任务服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;
    private final TaskCommentRepository taskCommentRepository;
    private final TaskAttachmentRepository taskAttachmentRepository;
    private static final Set<String> VALID_STATUSES = new HashSet<>(Arrays.asList("pending", "in_progress", "completed", "cancelled", "assigned"));
    private static final DateTimeFormatter TASK_NAME_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    @Override
    @Transactional
    public Task createTask(Task task) {
        task.setTitle(generateTaskTitle(task.getReferenceId()));
        task.setStatus("pending");
        log.info("创建任务: title={}", task.getTitle());
        return taskRepository.save(task);
    }

    @Override
    public Optional<Task> findById(@Nullable UUID id) {
        return id != null ? taskRepository.findById(id) : Optional.empty();
    }

    @Override
    public PageResponse<Task> findAll(int page, int size, @Nullable String keyword, @Nullable String status, @Nullable UUID assigneeId, @Nullable UUID referenceId, @Nullable UUID assetId, @Nullable UUID assetVersionId) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        
        Specification<Task> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            
            if (StringUtils.hasText(keyword)) {
                predicates.add(cb.like(root.get("title"), "%" + keyword + "%"));
            }
            
            if (StringUtils.hasText(status)) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            
            if (assigneeId != null) {
                predicates.add(cb.equal(root.get("assignedTo"), assigneeId));
            }
            
            if (referenceId != null) {
                predicates.add(cb.equal(root.get("referenceId"), referenceId));
            }

            if (assetId != null) {
                predicates.add(cb.equal(root.get("assetId"), assetId));
            }

            if (assetVersionId != null) {
                predicates.add(cb.equal(root.get("assetVersionId"), assetVersionId));
            }
            
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        
        Page<Task> taskPage = taskRepository.findAll(spec, pageable);
        
        return PageResponse.<Task>builder()
                .content(taskPage.getContent())
                .totalElements(taskPage.getTotalElements())
                .totalPages(taskPage.getTotalPages())
                .currentPage(taskPage.getNumber())
                .pageSize(taskPage.getSize())
                .build();
    }

    @Override
    public List<Task> findByAssigneeId(@Nullable UUID assigneeId) {
        return assigneeId != null ? taskRepository.findByAssignedTo(assigneeId) : List.of();
    }

    @Override
    public List<Task> findByReferenceId(@Nullable UUID referenceId) {
        return referenceId != null ? taskRepository.findByReferenceId(referenceId) : List.of();
    }

    @Override
    @Transactional
    public Task updateTask(@Nullable UUID id, Task task) {
        log.info("更新任务: id={}", id);

        if (id == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }

        Task existingTask = taskRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));

        if (task.getTitle() != null) {
            existingTask.setTitle(task.getTitle());
        }
        if (task.getDescription() != null) {
            existingTask.setDescription(task.getDescription());
        }
        if (task.getDueDate() != null) {
            existingTask.setDueDate(task.getDueDate());
        }

        return taskRepository.save(Objects.requireNonNull(existingTask));
    }

    @Override
    @Transactional
    public Task updateStatus(@Nullable UUID id, @Nullable String status, @Nullable UUID operatorId) {
        log.info("更新任务状态: id={}, status={}", id, status);

        if (id == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }

        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));

        task.setStatus(normalizeStatus(status));
        return taskRepository.save(task);
    }

    @Override
    @Transactional
    public void deleteTask(@Nullable UUID id) {
        log.info("删除任务: id={}", id);
        if (id != null) {
            taskRepository.deleteById(id);
        }
    }

    @Override
    @Transactional
    public void assignTask(@Nullable UUID id, @Nullable UUID assigneeId, @Nullable UUID operatorId) {
        log.info("分配任务: id={}, assigneeId={}", id, assigneeId);

        if (id == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }

        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));

        task.setAssignedTo(assigneeId);
        task.setStatus("assigned");
        taskRepository.save(task);
    }

    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return "pending";
        }
        String normalized = status.trim().toLowerCase();
        return VALID_STATUSES.contains(normalized) ? normalized : "pending";
    }

    private String generateTaskTitle(UUID referenceId) {
        String timestamp = LocalDateTime.now().format(TASK_NAME_TIME_FORMATTER);
        String referenceSuffix = referenceId == null ? "general" : referenceId.toString().replace("-", "").substring(0, 8);
        return "审计任务-" + timestamp + "-" + referenceSuffix;
    }

    @Override
    @Transactional
    public void addComment(@Nullable UUID taskId, @Nullable UUID userId, @Nullable String content) {
        log.info("添加任务评论: taskId={}, userId={}", taskId, userId);

        if (taskId == null || userId == null) {
            return;
        }

        TaskComment comment = TaskComment.builder()
                .taskId(taskId)
                .userId(userId)
                .content(content)
                .build();
        taskCommentRepository.save(Objects.requireNonNull(comment));
    }

    @Override
    @Transactional
    public void addAttachment(@Nullable UUID taskId, @Nullable String fileName, @Nullable String filePath, @Nullable UUID uploadedBy) {
        log.info("添加任务附件: taskId={}, fileName={}", taskId, fileName);

        if (taskId == null) {
            return;
        }

        TaskAttachment attachment = TaskAttachment.builder()
                .taskId(taskId)
                .fileName(fileName)
                .filePath(filePath)
                .uploadedBy(uploadedBy)
                .build();
        taskAttachmentRepository.save(Objects.requireNonNull(attachment));
    }
}
