package com.vulnaudit.service.impl;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.Task;
import com.vulnaudit.entity.TaskAttachment;
import com.vulnaudit.entity.TaskCollaborator;
import com.vulnaudit.entity.TaskComment;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.TaskAttachmentRepository;
import com.vulnaudit.repository.TaskCollaboratorRepository;
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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    private final TaskCollaboratorRepository taskCollaboratorRepository;
    private static final Set<String> VALID_STATUSES = new HashSet<>(Arrays.asList("pending", "in_progress", "review_pending", "completed", "cancelled", "assigned"));
    private static final Set<String> VALID_WORK_TYPES = new HashSet<>(Arrays.asList(
            "analysis", "verify", "retest", "fix_review", "meeting", "other",
            "env_collect", "env_setup", "code_audit"
    ));
    private static final Map<String, Set<String>> STATUS_TRANSITIONS = Map.of(
            "pending", Set.of("assigned", "in_progress", "cancelled"),
            "assigned", Set.of("in_progress", "cancelled"),
            "in_progress", Set.of("review_pending", "cancelled"),
            "review_pending", Set.of("in_progress", "completed"),
            "completed", Set.of(),
            "cancelled", Set.of()
    );
    private static final DateTimeFormatter TASK_NAME_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    @Override
    @Transactional
    public Task createTask(Task task) {
        if (!StringUtils.hasText(task.getTitle())) {
            task.setTitle(generateTaskTitle(task.getReferenceId()));
        } else {
            task.setTitle(task.getTitle().trim());
        }
        task.setStatus("pending");
        task.setEstimatedHours(normalizeNonNegative(task.getEstimatedHours(), "预估工时不能为负数"));
        if (task.getActualHours() == null) {
            task.setActualHours(BigDecimal.ZERO);
        }
        log.info("创建任务: title={}", task.getTitle());
        return taskRepository.save(task);
    }

    @Override
    public Optional<Task> findById(@Nullable UUID id) {
        return id != null ? taskRepository.findById(id) : Optional.empty();
    }

    @Override
    public PageResponse<Task> findAll(int page, int size, @Nullable String keyword, @Nullable String status, @Nullable UUID assigneeId, @Nullable UUID referenceId, @Nullable UUID assetId, @Nullable UUID assetVersionId, @Nullable UUID operatorId, boolean managerAccess) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Set<UUID> collaboratorTaskIds = operatorId == null ? Set.of() : taskCollaboratorRepository.findByUserId(operatorId)
                .stream()
                .map(TaskCollaborator::getTaskId)
                .filter(Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        
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

            if (!managerAccess) {
                if (operatorId == null) {
                    predicates.add(cb.disjunction());
                } else {
                    List<jakarta.persistence.criteria.Predicate> accessPredicates = new ArrayList<>();
                    accessPredicates.add(cb.equal(root.get("assignedTo"), operatorId));
                    accessPredicates.add(cb.equal(root.get("assignedBy"), operatorId));
                    if (!collaboratorTaskIds.isEmpty()) {
                        accessPredicates.add(root.get("id").in(collaboratorTaskIds));
                    }
                    predicates.add(cb.or(accessPredicates.toArray(new jakarta.persistence.criteria.Predicate[0])));
                }
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
        if (task.getAuditConclusion() != null) {
            existingTask.setAuditConclusion(task.getAuditConclusion());
        }
        if (task.getEstimatedHours() != null) {
            existingTask.setEstimatedHours(normalizeNonNegative(task.getEstimatedHours(), "预估工时不能为负数"));
        }
        if (task.getEnvCollectDocId() != null) {
            existingTask.setEnvCollectDocId(task.getEnvCollectDocId());
        }
        if (task.getEnvSetupDocId() != null) {
            existingTask.setEnvSetupDocId(task.getEnvSetupDocId());
        }
        if (task.getCodeAuditDocId() != null) {
            existingTask.setCodeAuditDocId(task.getCodeAuditDocId());
        }

        return taskRepository.save(Objects.requireNonNull(existingTask));
    }

    @Override
    @Transactional
    public Task updateStatus(@Nullable UUID id, @Nullable String status, @Nullable UUID operatorId, boolean managerAccess) {
        log.info("更新任务状态: id={}, status={}", id, status);

        if (id == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }

        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));

        if (!hasTaskAccess(task, operatorId, managerAccess)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权限更新任务状态");
        }

        String targetStatus = normalizeStatus(status);
        validateStatusTransition(task.getStatus(), targetStatus);
        task.setStatus(targetStatus);
        if (!"completed".equals(targetStatus)) {
            task.setCompletedAt(null);
        }
        if ("completed".equals(targetStatus) && task.getCompletedAt() == null) {
            task.setCompletedAt(LocalDateTime.now());
        }
        return taskRepository.save(task);
    }

    @Override
    @Transactional
    public Task submitForReview(@Nullable UUID id, @Nullable UUID operatorId) {
        if (id == null || operatorId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
        if (!operatorId.equals(task.getAssignedTo())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "仅任务负责人可提交审核");
        }
        if (!StringUtils.hasText(task.getAuditConclusion())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请先填写审计结论");
        }
        String currentStatus = normalizeStatus(task.getStatus());
        if ("completed".equals(currentStatus) || "cancelled".equals(currentStatus)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "当前状态不可提交审核");
        }
        task.setStatus("review_pending");
        task.setApprovalComment(null);
        task.setCompletedAt(null);
        return taskRepository.save(task);
    }

    @Override
    @Transactional
    public Task approveReview(@Nullable UUID id, @Nullable UUID operatorId, @Nullable String signature, @Nullable String comment) {
        if (id == null || operatorId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
        if (!"review_pending".equals(normalizeStatus(task.getStatus()))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅待审核任务可通过");
        }
        task.setStatus("completed");
        task.setCompletedAt(LocalDateTime.now());
        task.setReviewedBy(operatorId);
        task.setReviewedAt(LocalDateTime.now());
        task.setReviewSignature(StringUtils.hasText(signature) ? signature.trim() : null);
        task.setApprovalComment(StringUtils.hasText(comment) ? comment.trim() : null);
        return taskRepository.save(task);
    }

    @Override
    @Transactional
    public Task rejectReview(@Nullable UUID id, @Nullable UUID operatorId, @Nullable String signature, @Nullable String comment) {
        if (id == null || operatorId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
        if (!"review_pending".equals(normalizeStatus(task.getStatus()))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "仅待审核任务可驳回");
        }
        task.setStatus("in_progress");
        task.setReviewedBy(operatorId);
        task.setReviewedAt(LocalDateTime.now());
        task.setReviewSignature(StringUtils.hasText(signature) ? signature.trim() : null);
        task.setApprovalComment(StringUtils.hasText(comment) ? comment.trim() : "审核驳回");
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
    public void assignTask(@Nullable UUID id, @Nullable UUID assigneeId, @Nullable UUID operatorId, boolean managerAccess) {
        log.info("分配任务: id={}, assigneeId={}", id, assigneeId);

        if (id == null) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }

        Task task = taskRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));

        if (!hasTaskAccess(task, operatorId, managerAccess)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权限分配任务");
        }

        task.setAssignedTo(assigneeId);
        validateStatusTransition(task.getStatus(), "assigned");
        task.setStatus("assigned");
        if (assigneeId != null) {
            taskCollaboratorRepository.deleteByTaskIdAndUserId(id, assigneeId);
        }
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
    public void addComment(@Nullable UUID taskId, @Nullable UUID userId, @Nullable String content, @Nullable BigDecimal spentHours, @Nullable String workType, @Nullable String evidencePath) {
        log.info("添加任务评论: taskId={}, userId={}", taskId, userId);

        if (taskId == null || userId == null || !StringUtils.hasText(content)) {
            return;
        }

        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));

        BigDecimal normalizedSpentHours = normalizeSpentHours(spentHours);
        TaskComment comment = TaskComment.builder()
                .taskId(taskId)
                .userId(userId)
                .content(content)
                .spentHours(normalizedSpentHours)
                .workType(normalizeWorkType(workType))
                .evidencePath(trimToNull(evidencePath))
                .build();
        taskCommentRepository.save(Objects.requireNonNull(comment));

        if (normalizedSpentHours != null && normalizedSpentHours.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal currentActual = task.getActualHours() == null ? BigDecimal.ZERO : task.getActualHours();
            task.setActualHours(currentActual.add(normalizedSpentHours));
            taskRepository.save(task);
        }
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

    @Override
    public List<TaskComment> findCommentsByTaskId(@Nullable UUID taskId) {
        if (taskId == null) {
            return List.of();
        }
        return taskCommentRepository.findByTaskIdOrderByCreatedAtDesc(taskId);
    }

    @Override
    public List<TaskAttachment> findAttachmentsByTaskId(@Nullable UUID taskId) {
        if (taskId == null) {
            return List.of();
        }
        return taskAttachmentRepository.findByTaskIdOrderByCreatedAtDesc(taskId);
    }

    @Override
    @Transactional
    public void addCollaborator(@Nullable UUID taskId, @Nullable UUID userId, @Nullable UUID operatorId) {
        if (taskId == null || userId == null || operatorId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
        if (userId.equals(task.getAssignedTo())) {
            return;
        }
        if (taskCollaboratorRepository.existsByTaskIdAndUserId(taskId, userId)) {
            return;
        }
        TaskCollaborator collaborator = TaskCollaborator.builder()
                .taskId(taskId)
                .userId(userId)
                .addedBy(operatorId)
                .build();
        taskCollaboratorRepository.save(collaborator);
    }

    @Override
    @Transactional
    public void removeCollaborator(@Nullable UUID taskId, @Nullable UUID userId) {
        if (taskId == null || userId == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST);
        }
        taskCollaboratorRepository.deleteByTaskIdAndUserId(taskId, userId);
    }

    @Override
    public boolean isCollaborator(@Nullable UUID taskId, @Nullable UUID userId) {
        if (taskId == null || userId == null) {
            return false;
        }
        return taskCollaboratorRepository.existsByTaskIdAndUserId(taskId, userId);
    }

    @Override
    public List<UUID> findCollaboratorUserIds(@Nullable UUID taskId) {
        if (taskId == null) {
            return List.of();
        }
        return taskCollaboratorRepository.findByTaskIdOrderByCreatedAtAsc(taskId)
                .stream()
                .map(TaskCollaborator::getUserId)
                .filter(Objects::nonNull)
                .toList();
    }

    private BigDecimal normalizeSpentHours(@Nullable BigDecimal spentHours) {
        if (spentHours == null) {
            return null;
        }
        return normalizeNonNegative(spentHours, "工时不能为负数");
    }

    private String normalizeWorkType(@Nullable String workType) {
        if (!StringUtils.hasText(workType)) {
            return null;
        }
        String normalized = workType.trim().toLowerCase();
        if (!VALID_WORK_TYPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的执行类型");
        }
        return normalized;
    }

    private String trimToNull(@Nullable String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private boolean hasTaskAccess(Task task, @Nullable UUID operatorId, boolean managerAccess) {
        if (managerAccess) {
            return true;
        }
        if (task == null || operatorId == null) {
            return false;
        }
        return operatorId.equals(task.getAssignedTo())
                || operatorId.equals(task.getAssignedBy())
                || taskCollaboratorRepository.existsByTaskIdAndUserId(task.getId(), operatorId);
    }

    private void validateStatusTransition(String fromStatus, String toStatus) {
        String normalizedFrom = normalizeStatus(fromStatus);
        String normalizedTo = normalizeStatus(toStatus);
        if (normalizedFrom.equals(normalizedTo)) {
            return;
        }
        Set<String> allowedTargets = STATUS_TRANSITIONS.getOrDefault(normalizedFrom, Set.of());
        if (!allowedTargets.contains(normalizedTo)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不允许的状态流转: " + normalizedFrom + " -> " + normalizedTo);
        }
    }

    private BigDecimal normalizeNonNegative(@Nullable BigDecimal value, String errorMessage) {
        if (value == null) {
            return null;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, errorMessage);
        }
        return value;
    }
}
