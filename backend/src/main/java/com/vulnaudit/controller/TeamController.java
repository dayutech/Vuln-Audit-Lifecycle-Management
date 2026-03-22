package com.vulnaudit.controller;

import com.vulnaudit.dto.*;
import com.vulnaudit.entity.Team;
import com.vulnaudit.service.TeamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * 团队管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/teams")
@RequiredArgsConstructor
@Tag(name = "团队管理", description = "团队CRUD、成员管理等接口")
@SecurityRequirement(name = "bearerAuth")
public class TeamController {

    private final TeamService teamService;

    @PostMapping
    @Operation(summary = "创建团队", description = "创建新的团队")
    public ResponseEntity<ApiResponse<Team>> createTeam(@Valid @RequestBody Team team) {
        log.info("创建团队: name={}", team.getName());
        
        Team createdTeam = teamService.createTeam(team);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(createdTeam));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取团队详情", description = "根据ID获取团队详细信息")
    public ResponseEntity<ApiResponse<Team>> getTeam(@PathVariable UUID id) {
        log.info("获取团队详情: id={}", id);
        
        return teamService.findById(id)
                .map(team -> ResponseEntity.ok(ApiResponse.success(team)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(404, "团队不存在")));
    }

    @GetMapping
    @Operation(summary = "分页查询团队列表", description = "支持按关键词筛选")
    public ResponseEntity<ApiResponse<PageResponse<Team>>> listTeams(
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "搜索关键词") @RequestParam(required = false) String keyword) {
        
        log.info("分页查询团队: page={}, size={}, keyword={}", page, size, keyword);
        
        PageResponse<Team> result = teamService.findAll(page, size, keyword);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/all")
    @Operation(summary = "获取所有团队", description = "获取所有团队列表（不分页）")
    public ResponseEntity<ApiResponse<List<Team>>> listAllTeams() {
        log.info("获取所有团队");
        
        List<Team> teams = teamService.findAll();
        return ResponseEntity.ok(ApiResponse.success(teams));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新团队", description = "更新团队信息")
    public ResponseEntity<ApiResponse<Team>> updateTeam(
            @PathVariable UUID id,
            @Valid @RequestBody Team team) {
        
        log.info("更新团队: id={}", id);
        
        Team updatedTeam = teamService.updateTeam(id, team);
        return ResponseEntity.ok(ApiResponse.success(updatedTeam));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除团队", description = "删除团队")
    public ResponseEntity<ApiResponse<Void>> deleteTeam(@PathVariable UUID id) {
        log.info("删除团队: id={}", id);
        
        teamService.deleteTeam(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{id}/children")
    @Operation(summary = "获取子团队", description = "获取指定团队的子团队列表")
    public ResponseEntity<ApiResponse<List<Team>>> getChildTeams(@PathVariable UUID id) {
        log.info("获取子团队: parentId={}", id);
        
        List<Team> children = teamService.findByParentId(id);
        return ResponseEntity.ok(ApiResponse.success(children));
    }

    @GetMapping("/roots")
    @Operation(summary = "获取根团队", description = "获取所有根团队（无父团队的团队）")
    public ResponseEntity<ApiResponse<List<Team>>> getRootTeams() {
        log.info("获取根团队");
        
        List<Team> roots = teamService.findRootTeams();
        return ResponseEntity.ok(ApiResponse.success(roots));
    }

    @GetMapping("/{id}/tree")
    @Operation(summary = "获取团队树", description = "获取指定团队及其所有子团队")
    public ResponseEntity<ApiResponse<List<Team>>> getTeamTree(@PathVariable UUID id) {
        log.info("获取团队树: teamId={}", id);
        
        List<Team> tree = teamService.findTeamTree(id);
        return ResponseEntity.ok(ApiResponse.success(tree));
    }

    // ==================== 成员管理 ====================

    @PostMapping("/{id}/members")
    @Operation(summary = "添加团队成员", description = "向团队添加成员")
    public ResponseEntity<ApiResponse<Void>> addMember(
            @PathVariable UUID id,
            @Parameter(description = "用户ID") @RequestParam UUID userId,
            @Parameter(description = "角色") @RequestParam String role) {
        
        log.info("添加团队成员: teamId={}, userId={}, role={}", id, userId, role);
        
        teamService.addMember(id, userId, role);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/{id}/members/{userId}")
    @Operation(summary = "移除团队成员", description = "从团队移除成员")
    public ResponseEntity<ApiResponse<Void>> removeMember(
            @PathVariable UUID id,
            @PathVariable UUID userId) {
        
        log.info("移除团队成员: teamId={}, userId={}", id, userId);
        
        teamService.removeMember(id, userId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{id}/members")
    @Operation(summary = "获取团队成员", description = "获取团队所有成员ID列表")
    public ResponseEntity<ApiResponse<List<UUID>>> getMembers(@PathVariable UUID id) {
        log.info("获取团队成员: teamId={}", id);
        
        List<UUID> memberIds = teamService.getMemberIds(id);
        return ResponseEntity.ok(ApiResponse.success(memberIds));
    }

    @GetMapping("/{id}/users")
    @Operation(summary = "获取团队所有用户", description = "获取团队及其子团队的所有用户ID")
    public ResponseEntity<ApiResponse<List<UUID>>> getTeamUsers(@PathVariable UUID id) {
        log.info("获取团队所有用户: teamId={}", id);
        
        List<UUID> userIds = teamService.getTeamUserIds(id);
        return ResponseEntity.ok(ApiResponse.success(userIds));
    }
}
