package com.vulnaudit.controller;

import com.vulnaudit.dto.*;
import com.vulnaudit.entity.Task;
import com.vulnaudit.entity.TaskAttachment;
import com.vulnaudit.entity.TaskComment;
import com.vulnaudit.entity.User;
import com.vulnaudit.service.AuditLogService;
import com.vulnaudit.service.TaskService;
import com.vulnaudit.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 任务管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/tasks")
@RequiredArgsConstructor
@Tag(name = "任务管理", description = "任务CRUD、分配、评论等接口")
@SecurityRequirement(name = "bearerAuth")
public class TaskController {

    private final TaskService taskService;
    private final UserService userService;
    private final AuditLogService auditLogService;

    @PostMapping
    @Operation(summary = "创建任务", description = "创建新任务")
    public ResponseEntity<ApiResponse<Task>> createTask(@Valid @RequestBody TaskCreateRequest request) {
        log.info("创建任务: title={}", request.getTitle());
        UUID currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(401, "未授权"));
        }
        
        Task task = Task.builder()
                .title(request.getTitle())
                .assetId(request.getAssetId())
                .assetVersionId(request.getAssetVersionId())
                .referenceId(request.getReferenceId() != null ? request.getReferenceId() : request.getAssetId())
                .assignedTo(request.getAssigneeId() != null ? request.getAssigneeId() : currentUserId)
                .assignedBy(currentUserId)
                .dueDate(request.getDueDate())
                .description(request.getDescription())
                .estimatedHours(request.getEstimatedHours())
                .status("pending")
                .build();
        
        Task createdTask = enrichTaskUsers(taskService.createTask(task));
        auditLogService.log(currentUserId, "task_create", "task", createdTask.getId(), "创建审计任务");
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(createdTask));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取任务详情", description = "根据ID获取任务详细信息")
    public ResponseEntity<ApiResponse<Task>> getTask(@PathVariable UUID id) {
        log.info("获取任务详情: id={}", id);
        
        return taskService.findById(id)
                .map(task -> ResponseEntity.ok(ApiResponse.success(enrichTaskUsers(task))))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(404, "任务不存在")));
    }

    @GetMapping
    @Operation(summary = "分页查询任务列表", description = "支持按关键词、状态、指派人筛选")
    public ResponseEntity<ApiResponse<PageResponse<Task>>> listTasks(
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "搜索关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "状态") @RequestParam(required = false) String status,
            @Parameter(description = "指派人ID") @RequestParam(required = false) UUID assigneeId,
            @Parameter(description = "关联资源ID") @RequestParam(required = false) UUID referenceId,
            @Parameter(description = "关联资产ID") @RequestParam(required = false) UUID assetId,
            @Parameter(description = "关联资产版本ID") @RequestParam(required = false) UUID assetVersionId) {
        
        log.info("分页查询任务: page={}, size={}, keyword={}, status={}, assigneeId={}, referenceId={}, assetId={}, assetVersionId={}", page, size, keyword, status, assigneeId, referenceId, assetId, assetVersionId);
        
        UUID currentUserId = getCurrentUserId();
        PageResponse<Task> result = taskService.findAll(page, size, keyword, status, assigneeId, referenceId, assetId, assetVersionId, currentUserId, hasManagerAuthority());
        result.setContent(result.getContent().stream().map(this::enrichTaskUsers).toList());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/assignee/{assigneeId}")
    @Operation(summary = "获取指派人的任务", description = "获取指派给指定用户的所有任务")
    public ResponseEntity<ApiResponse<List<Task>>> listTasksByAssignee(@PathVariable UUID assigneeId) {
        log.info("获取指派人的任务: assigneeId={}", assigneeId);
        UUID currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(401, "未授权"));
        }
        if (!hasManagerAuthority() && !currentUserId.equals(assigneeId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(403, "无权限查看他人任务"));
        }
        List<Task> tasks = taskService.findByAssigneeId(assigneeId).stream().map(this::enrichTaskUsers).toList();
        return ResponseEntity.ok(ApiResponse.success(tasks));
    }

    @GetMapping("/reference/{referenceId}")
    @Operation(summary = "获取关联任务", description = "获取关联到指定资源的所有任务")
    public ResponseEntity<ApiResponse<List<Task>>> listTasksByReference(@PathVariable UUID referenceId) {
        log.info("获取关联任务: referenceId={}", referenceId);
        UUID currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(401, "未授权"));
        }
        PageResponse<Task> pageResult = taskService.findAll(0, 500, null, null, null, referenceId, null, null, currentUserId, hasManagerAuthority());
        List<Task> tasks = pageResult.getContent().stream().map(this::enrichTaskUsers).toList();
        return ResponseEntity.ok(ApiResponse.success(tasks));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新任务", description = "更新任务信息")
    public ResponseEntity<ApiResponse<Task>> updateTask(
            @PathVariable UUID id,
            @Valid @RequestBody TaskUpdateRequest request) {
        
        log.info("更新任务: id={}", id);
        
        Task task = Task.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .dueDate(parseDueDate(request.getDueDate()))
                .auditConclusion(request.getAuditConclusion())
                .estimatedHours(request.getEstimatedHours())
                .build();
        
        Task updatedTask = enrichTaskUsers(taskService.updateTask(id, task));
        UUID currentUserId = getCurrentUserId();
        if (currentUserId != null) {
            auditLogService.log(currentUserId, "task_update", "task", id, "更新任务信息");
        }
        return ResponseEntity.ok(ApiResponse.success(updatedTask));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "更新任务状态", description = "更新任务状态")
    public ResponseEntity<ApiResponse<Task>> updateStatus(
            @PathVariable UUID id,
            @Parameter(description = "新状态") @RequestParam String status) {
        
        log.info("更新任务状态: id={}, status={}", id, status);
        
        UUID currentUserId = getCurrentUserId();
        Task updatedTask = enrichTaskUsers(taskService.updateStatus(id, status, currentUserId, hasManagerAuthority()));
        if (currentUserId != null) {
            auditLogService.log(currentUserId, "task_status_update", "task", id, "更新任务状态为: " + status);
        }
        return ResponseEntity.ok(ApiResponse.success(updatedTask));
    }

    @PostMapping("/{id}/submit-review")
    @Operation(summary = "提交任务审核", description = "任务负责人提交审计任务进入待审核")
    public ResponseEntity<ApiResponse<Task>> submitReview(@PathVariable UUID id) {
        UUID currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(401, "未授权"));
        }
        Task updatedTask = enrichTaskUsers(taskService.submitForReview(id, currentUserId));
        auditLogService.log(currentUserId, "task_submit_review", "task", id, "提交任务审核");
        return ResponseEntity.ok(ApiResponse.success(updatedTask));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyAuthority('admin','project_leader','team_leader','ROLE_ADMIN','ROLE_LEADER')")
    @Operation(summary = "通过任务审核", description = "负责人或管理员审核通过任务")
    public ResponseEntity<ApiResponse<Task>> approveReview(
            @PathVariable UUID id,
            @RequestBody(required = false) TaskReviewActionRequest request) {
        UUID currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(401, "未授权"));
        }
        String signature = request != null ? request.getSignature() : null;
        if (signature == null || signature.trim().isEmpty()) {
            signature = getCurrentUser().map(User::getFullName).orElse(null);
        }
        String comment = request != null ? request.getComment() : null;
        Task updatedTask = enrichTaskUsers(taskService.approveReview(id, currentUserId, signature, comment));
        auditLogService.log(currentUserId, "task_approve", "task", id, "审核通过任务");
        return ResponseEntity.ok(ApiResponse.success(updatedTask));
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyAuthority('admin','project_leader','team_leader','ROLE_ADMIN','ROLE_LEADER')")
    @Operation(summary = "驳回任务审核", description = "负责人或管理员驳回任务")
    public ResponseEntity<ApiResponse<Task>> rejectReview(
            @PathVariable UUID id,
            @RequestBody(required = false) TaskReviewActionRequest request) {
        UUID currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(401, "未授权"));
        }
        String signature = request != null ? request.getSignature() : null;
        if (signature == null || signature.trim().isEmpty()) {
            signature = getCurrentUser().map(User::getFullName).orElse(null);
        }
        String comment = request != null ? request.getComment() : null;
        Task updatedTask = enrichTaskUsers(taskService.rejectReview(id, currentUserId, signature, comment));
        auditLogService.log(currentUserId, "task_reject", "task", id, "驳回任务审核");
        return ResponseEntity.ok(ApiResponse.success(updatedTask));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除任务", description = "删除任务")
    public ResponseEntity<ApiResponse<Void>> deleteTask(@PathVariable UUID id) {
        log.info("删除任务: id={}", id);
        UUID currentUserId = getCurrentUserId();
        taskService.deleteTask(id);
        if (currentUserId != null) {
            auditLogService.log(currentUserId, "task_delete", "task", id, "删除任务");
        }
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{id}/assign")
    @PreAuthorize("hasAnyAuthority('admin','project_leader','team_leader','ROLE_ADMIN','ROLE_LEADER')")
    @Operation(summary = "分配任务", description = "将任务分配给用户")
    public ResponseEntity<ApiResponse<Void>> assignTask(
            @PathVariable UUID id,
            @Parameter(description = "指派人ID") @RequestParam UUID assigneeId) {
        
        log.info("分配任务: taskId={}, assigneeId={}", id, assigneeId);
        UUID currentUserId = getCurrentUserId();
        taskService.assignTask(id, assigneeId, currentUserId, hasManagerAuthority());
        if (currentUserId != null) {
            auditLogService.log(currentUserId, "task_assign", "task", id, "分配任务给用户: " + assigneeId);
        }
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{id}/comments")
    @Operation(summary = "添加任务评论", description = "为任务添加评论")
    public ResponseEntity<ApiResponse<Void>> addComment(
            @PathVariable UUID id,
            @Parameter(description = "评论内容") @RequestParam String content,
            @Parameter(description = "本次实际工时") @RequestParam(required = false) BigDecimal spentHours,
            @Parameter(description = "执行类型") @RequestParam(required = false) String workType,
            @Parameter(description = "证据路径") @RequestParam(required = false) String evidencePath) {
        
        log.info("添加任务评论: taskId={}", id);
        UUID currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(401, "未授权"));
        }
        Task task = taskService.findById(id)
                .orElse(null);
        if (task == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(404, "任务不存在"));
        }
        if (!canRecordExecution(task, currentUserId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(ApiResponse.error(403, "仅负责人或协作者可记录执行项"));
        }
        taskService.addComment(id, currentUserId, content, spentHours, workType, evidencePath);
        auditLogService.log(currentUserId, "task_comment_add", "task", id, "新增执行记录");
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{id}/collaborators")
    @PreAuthorize("hasAnyAuthority('admin','project_leader','team_leader','ROLE_ADMIN','ROLE_LEADER')")
    @Operation(summary = "添加任务协作者", description = "为任务添加协作者，协作者可记录执行项")
    public ResponseEntity<ApiResponse<Void>> addCollaborator(
            @PathVariable UUID id,
            @Parameter(description = "协作者用户ID") @RequestParam UUID userId) {
        UUID currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(401, "未授权"));
        }
        if (userService.findById(userId).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(404, "用户不存在"));
        }
        taskService.addCollaborator(id, userId, currentUserId);
        auditLogService.log(currentUserId, "task_collaborator_add", "task", id, "添加协作者: " + userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/{id}/collaborators/{userId}")
    @PreAuthorize("hasAnyAuthority('admin','project_leader','team_leader','ROLE_ADMIN','ROLE_LEADER')")
    @Operation(summary = "移除任务协作者", description = "移除任务协作者")
    public ResponseEntity<ApiResponse<Void>> removeCollaborator(
            @PathVariable UUID id,
            @PathVariable UUID userId) {
        UUID currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(401, "未授权"));
        }
        taskService.removeCollaborator(id, userId);
        auditLogService.log(currentUserId, "task_collaborator_remove", "task", id, "移除协作者: " + userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{id}/comments")
    @Operation(summary = "查询任务执行记录", description = "查询任务评论/执行记录列表")
    public ResponseEntity<ApiResponse<List<TaskComment>>> getComments(@PathVariable UUID id) {
        log.info("查询任务执行记录: taskId={}", id);
        return ResponseEntity.ok(ApiResponse.success(taskService.findCommentsByTaskId(id)));
    }

    @PostMapping("/{id}/attachments")
    @Operation(summary = "添加任务附件", description = "为任务添加附件")
    public ResponseEntity<ApiResponse<Void>> addAttachment(
            @PathVariable UUID id,
            @Parameter(description = "文件名") @RequestParam String fileName,
            @Parameter(description = "文件路径") @RequestParam String filePath) {
        
        log.info("添加任务附件: taskId={}, fileName={}", id, fileName);
        UUID currentUserId = getCurrentUserId();
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(401, "未授权"));
        }
        taskService.addAttachment(id, fileName, filePath, currentUserId);
        auditLogService.log(currentUserId, "task_attachment_add", "task", id, "新增任务附件: " + fileName);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{id}/attachments")
    @Operation(summary = "查询任务附件", description = "查询任务附件列表")
    public ResponseEntity<ApiResponse<List<TaskAttachment>>> getAttachments(@PathVariable UUID id) {
        log.info("查询任务附件: taskId={}", id);
        return ResponseEntity.ok(ApiResponse.success(taskService.findAttachmentsByTaskId(id)));
    }

    private UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || "anonymousUser".equals(authentication.getName())) {
            return null;
        }
        return userService.findByUsername(authentication.getName())
                .map(user -> user.getId())
                .orElse(null);
    }

    private java.util.Optional<User> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || "anonymousUser".equals(authentication.getName())) {
            return java.util.Optional.empty();
        }
        return userService.findByUsername(authentication.getName());
    }

    private Task enrichTaskUsers(Task task) {
        if (task == null) {
            return null;
        }
        task.setAssignee(toTaskUserInfo(task.getAssignedTo()));
        task.setCreator(toTaskUserInfo(task.getAssignedBy()));
        task.setCollaborators(taskService.findCollaboratorUserIds(task.getId())
                .stream()
                .map(this::toTaskUserInfo)
                .filter(java.util.Objects::nonNull)
                .toList());
        return task;
    }

    private boolean canRecordExecution(Task task, UUID userId) {
        return userId.equals(task.getAssignedTo()) || taskService.isCollaborator(task.getId(), userId) || hasManagerAuthority();
    }

    private boolean hasManagerAuthority() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream().anyMatch(a ->
                "admin".equals(a.getAuthority()) ||
                        "project_leader".equals(a.getAuthority()) ||
                        "team_leader".equals(a.getAuthority()) ||
                        "ROLE_ADMIN".equals(a.getAuthority()) ||
                        "ROLE_LEADER".equals(a.getAuthority())
        );
    }

    private TaskUserInfo toTaskUserInfo(UUID userId) {
        if (userId == null) {
            return null;
        }
        return userService.findById(userId)
                .map(this::toTaskUserInfo)
                .orElse(null);
    }

    private TaskUserInfo toTaskUserInfo(User user) {
        return TaskUserInfo.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .build();
    }

    private LocalDateTime parseDueDate(String dueDate) {
        if (dueDate == null || dueDate.trim().isEmpty()) {
            return null;
        }
        return LocalDateTime.parse(dueDate.trim());
    }
}
