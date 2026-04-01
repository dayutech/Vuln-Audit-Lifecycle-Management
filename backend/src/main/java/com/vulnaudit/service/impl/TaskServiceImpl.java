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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

    @Override
    @Transactional
    public Task createTask(Task task) {
        log.info("创建任务: title={}", task.getTitle());
        return taskRepository.save(task);
    }

    @Override
    public Optional<Task> findById(UUID id) {
        return taskRepository.findById(id);
    }

    @Override
    public PageResponse<Task> findAll(int page, int size, String keyword, String status, UUID assigneeId, UUID referenceId) {
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
    public List<Task> findByAssigneeId(UUID assigneeId) {
        return taskRepository.findByAssignedTo(assigneeId);
    }

    @Override
    public List<Task> findByReferenceId(UUID referenceId) {
        return taskRepository.findByReferenceId(referenceId);
    }

    @Override
    @Transactional
    public Task updateTask(UUID id, Task task) {
        log.info("更新任务: id={}", id);
        
        Task existingTask = taskRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
        
        if (task.getTitle() != null) {
            existingTask.setTitle(task.getTitle());
        }
        if (task.getDescription() != null) {
            existingTask.setDescription(task.getDescription());
        }
        if (task.getPriority() != null) {
            existingTask.setPriority(task.getPriority());
        }
        if (task.getDueDate() != null) {
            existingTask.setDueDate(task.getDueDate());
        }
        
        return taskRepository.save(existingTask);
    }

    @Override
    @Transactional
    public Task updateStatus(UUID id, String status, UUID operatorId) {
        log.info("更新任务状态: id={}, status={}", id, status);
        
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
        
        task.setStatus(status);
        return taskRepository.save(task);
    }

    @Override
    @Transactional
    public void deleteTask(UUID id) {
        log.info("删除任务: id={}", id);
        taskRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void assignTask(UUID id, UUID assigneeId, UUID operatorId) {
        log.info("分配任务: id={}, assigneeId={}", id, assigneeId);
        
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
        
        task.setAssignedTo(assigneeId);
        task.setStatus("assigned");
        taskRepository.save(task);
    }

    @Override
    @Transactional
    public void addComment(UUID taskId, UUID userId, String content) {
        log.info("添加任务评论: taskId={}, userId={}", taskId, userId);
        
        TaskComment comment = TaskComment.builder()
                .taskId(taskId)
                .userId(userId)
                .content(content)
                .build();
        taskCommentRepository.save(comment);
    }

    @Override
    @Transactional
    public void addAttachment(UUID taskId, String fileName, String filePath, UUID uploadedBy) {
        log.info("添加任务附件: taskId={}, fileName={}", taskId, fileName);
        
        TaskAttachment attachment = TaskAttachment.builder()
                .taskId(taskId)
                .fileName(fileName)
                .filePath(filePath)
                .uploadedBy(uploadedBy)
                .build();
        taskAttachmentRepository.save(attachment);
    }
}
