package com.vulnaudit.controller;

import com.vulnaudit.dto.ApiResponse;
import com.vulnaudit.dto.PhaseRequest;
import com.vulnaudit.dto.PhaseResponse;
import com.vulnaudit.service.ProjectService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * 项目期次控制器（t68）—— 只承载「按期次ID」的操作；创建/列表挂在 {@link ProjectController} 下。
 *
 * <p>权限同项目写：{@code ADMIN / PROJECT_LEADER} 或该期次所属项目的经理
 * （由 {@code AccessGuard#canManagePhase} 从 phase → project → managerId 解析）。
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/phases")
@RequiredArgsConstructor
@Tag(name = "项目期次", description = "期次的更新与删除")
@SecurityRequirement(name = "bearerAuth")
public class ProjectPhaseController {

    private final ProjectService projectService;

    @PutMapping("/{phaseId}")
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_LEADER') or @accessGuard.canManagePhase(#phaseId, authentication)")
    @Operation(summary = "更新期次", description = "期次号重复 409；不存在 404")
    public ResponseEntity<ApiResponse<PhaseResponse>> updatePhase(
            @PathVariable UUID phaseId,
            @Valid @RequestBody PhaseRequest request) {

        log.info("更新项目期次: phaseId={}", phaseId);
        return ResponseEntity.ok(ApiResponse.success(projectService.updatePhase(phaseId, request)));
    }

    @DeleteMapping("/{phaseId}")
    @PreAuthorize("hasAnyRole('ADMIN','PROJECT_LEADER') or @accessGuard.canManagePhase(#phaseId, authentication)")
    @Operation(summary = "删除期次", description = "该期次下仍有资产挂靠时 409；不存在 404")
    public ResponseEntity<ApiResponse<Void>> deletePhase(@PathVariable UUID phaseId) {
        log.info("删除项目期次: phaseId={}", phaseId);
        projectService.deletePhase(phaseId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
