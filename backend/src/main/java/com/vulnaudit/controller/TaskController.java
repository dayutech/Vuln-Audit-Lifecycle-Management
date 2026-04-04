package com.vulnaudit.controller;

import com.vulnaudit.dto.*;
import com.vulnaudit.entity.Task;
import com.vulnaudit.entity.User;
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
                .status("pending")
                .build();
        
        Task createdTask = enrichTaskUsers(taskService.createTask(task));
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
        
        PageResponse<Task> result = taskService.findAll(page, size, keyword, status, assigneeId, referenceId, assetId, assetVersionId);
        result.setContent(result.getContent().stream().map(this::enrichTaskUsers).toList());
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/assignee/{assigneeId}")
    @Operation(summary = "获取指派人的任务", description = "获取指派给指定用户的所有任务")
    public ResponseEntity<ApiResponse<List<Task>>> listTasksByAssignee(@PathVariable UUID assigneeId) {
        log.info("获取指派人的任务: assigneeId={}", assigneeId);
        
        List<Task> tasks = taskService.findByAssigneeId(assigneeId).stream().map(this::enrichTaskUsers).toList();
        return ResponseEntity.ok(ApiResponse.success(tasks));
    }

    @GetMapping("/reference/{referenceId}")
    @Operation(summary = "获取关联任务", description = "获取关联到指定资源的所有任务")
    public ResponseEntity<ApiResponse<List<Task>>> listTasksByReference(@PathVariable UUID referenceId) {
        log.info("获取关联任务: referenceId={}", referenceId);
        
        List<Task> tasks = taskService.findByReferenceId(referenceId).stream().map(this::enrichTaskUsers).toList();
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
                .build();
        
        Task updatedTask = enrichTaskUsers(taskService.updateTask(id, task));
        return ResponseEntity.ok(ApiResponse.success(updatedTask));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "更新任务状态", description = "更新任务状态")
    public ResponseEntity<ApiResponse<Task>> updateStatus(
            @PathVariable UUID id,
            @Parameter(description = "新状态") @RequestParam String status) {
        
        log.info("更新任务状态: id={}, status={}", id, status);
        
        Task updatedTask = enrichTaskUsers(taskService.updateStatus(id, status, null));
        return ResponseEntity.ok(ApiResponse.success(updatedTask));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除任务", description = "删除任务")
    public ResponseEntity<ApiResponse<Void>> deleteTask(@PathVariable UUID id) {
        log.info("删除任务: id={}", id);
        
        taskService.deleteTask(id);
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
        taskService.assignTask(id, assigneeId, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{id}/comments")
    @Operation(summary = "添加任务评论", description = "为任务添加评论")
    public ResponseEntity<ApiResponse<Void>> addComment(
            @PathVariable UUID id,
            @Parameter(description = "评论内容") @RequestParam String content) {
        
        log.info("添加任务评论: taskId={}", id);
        
        taskService.addComment(id, null, content);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{id}/attachments")
    @Operation(summary = "添加任务附件", description = "为任务添加附件")
    public ResponseEntity<ApiResponse<Void>> addAttachment(
            @PathVariable UUID id,
            @Parameter(description = "文件名") @RequestParam String fileName,
            @Parameter(description = "文件路径") @RequestParam String filePath) {
        
        log.info("添加任务附件: taskId={}, fileName={}", id, fileName);
        
        taskService.addAttachment(id, fileName, filePath, null);
        return ResponseEntity.ok(ApiResponse.success(null));
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

    private Task enrichTaskUsers(Task task) {
        if (task == null) {
            return null;
        }
        task.setAssignee(toTaskUserInfo(task.getAssignedTo()));
        task.setCreator(toTaskUserInfo(task.getAssignedBy()));
        return task;
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
}
