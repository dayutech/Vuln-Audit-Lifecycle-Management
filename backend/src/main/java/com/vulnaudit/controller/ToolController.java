package com.vulnaudit.controller;

import com.vulnaudit.dto.*;
import com.vulnaudit.entity.Tool;
import com.vulnaudit.entity.ToolVersion;
import com.vulnaudit.service.ToolService;
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
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * 工具资源库控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/tools")
@RequiredArgsConstructor
@Tag(name = "工具资源库", description = "工具CRUD、下载、版本管理等接口")
@SecurityRequirement(name = "bearerAuth")
public class ToolController {

    private final ToolService toolService;
    private final UserService userService;

    @PostMapping
    @Operation(summary = "创建工具", description = "创建新工具")
    public ResponseEntity<ApiResponse<Tool>> createTool(@Valid @RequestBody Tool tool) {
        log.info("创建工具: name={}", tool.getName());

        // tools.created_by 为 NOT NULL，此前未赋值导致创建接口稳定 500
        UUID currentUserId = getCurrentUserId(SecurityContextHolder.getContext().getAuthentication());
        if (currentUserId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error(401, "未授权"));
        }
        if (tool.getCreatedBy() == null) {
            tool.setCreatedBy(currentUserId);
        }

        Tool createdTool = toolService.createTool(tool);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(createdTool));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取工具详情", description = "根据ID获取工具详细信息")
    public ResponseEntity<ApiResponse<Tool>> getTool(@PathVariable UUID id) {
        log.info("获取工具详情: id={}", id);
        
        return toolService.findById(id)
                .map(tool -> ResponseEntity.ok(ApiResponse.success(tool)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.error(404, "工具不存在")));
    }

    @GetMapping
    @Operation(summary = "分页查询工具列表", description = "支持按关键词、分类筛选")
    public ResponseEntity<ApiResponse<PageResponse<Tool>>> listTools(
            @Parameter(description = "页码") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "每页大小") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "搜索关键词") @RequestParam(required = false) String keyword,
            @Parameter(description = "分类") @RequestParam(required = false) String category) {
        
        log.info("分页查询工具: page={}, size={}, keyword={}, category={}", page, size, keyword, category);
        
        PageResponse<Tool> result = toolService.findAll(page, size, keyword, category);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/category/{category}")
    @Operation(summary = "按分类获取工具", description = "获取指定分类的所有工具")
    public ResponseEntity<ApiResponse<List<Tool>>> listToolsByCategory(@PathVariable String category) {
        log.info("按分类获取工具: category={}", category);
        
        List<Tool> tools = toolService.findByCategory(category);
        return ResponseEntity.ok(ApiResponse.success(tools));
    }

    @GetMapping("/popular")
    @Operation(summary = "获取热门工具", description = "获取下载量最高的工具")
    public ResponseEntity<ApiResponse<List<Tool>>> getPopularTools(
            @Parameter(description = "数量限制") @RequestParam(defaultValue = "10") int limit) {
        
        log.info("获取热门工具: limit={}", limit);
        
        List<Tool> tools = toolService.findPopular(limit);
        return ResponseEntity.ok(ApiResponse.success(tools));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新工具", description = "更新工具信息")
    public ResponseEntity<ApiResponse<Tool>> updateTool(
            @PathVariable UUID id,
            @Valid @RequestBody Tool tool) {
        
        log.info("更新工具: id={}", id);
        
        Tool updatedTool = toolService.updateTool(id, tool);
        return ResponseEntity.ok(ApiResponse.success(updatedTool));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除工具", description = "删除工具")
    public ResponseEntity<ApiResponse<Void>> deleteTool(@PathVariable UUID id) {
        log.info("删除工具: id={}", id);
        
        toolService.deleteTool(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{id}/download")
    @Operation(summary = "记录下载", description = "记录工具下载")
    public ResponseEntity<ApiResponse<Void>> recordDownload(@PathVariable UUID id) {
        log.info("记录工具下载: toolId={}", id);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UUID currentUserId = getCurrentUserId(authentication);
        toolService.recordDownload(id, currentUserId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 上传工具包。
     *
     * <p>SRS 3.5.3 的 Tool 模型包含 file_path，但实体此前缺失该字段，
     * 导致「工具资源库」只能存外链、无法真正分发工具包。
     */
    @PostMapping("/{id}/file")
    @Operation(summary = "上传工具包", description = "为工具上传可执行包/压缩包")
    public ResponseEntity<ApiResponse<Tool>> uploadToolFile(@PathVariable UUID id,
                                                           @RequestParam("file") MultipartFile file) {
        log.info("上传工具包: toolId={}, fileName={}, size={}", id,
                file.getOriginalFilename(), file.getSize());
        Tool updated = toolService.storeToolFile(id, file, getCurrentUserId(SecurityContextHolder.getContext().getAuthentication()));
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    /**
     * 下载工具包：真实文件流 + 下载计数。
     */
    @GetMapping("/{id}/download")
    @Operation(summary = "下载工具包", description = "下载工具包文件，并累加下载次数")
    public ResponseEntity<org.springframework.core.io.Resource> downloadToolFile(@PathVariable UUID id) {
        UUID currentUserId = getCurrentUserId(SecurityContextHolder.getContext().getAuthentication());
        Tool tool = toolService.findById(id)
                .orElseThrow(() -> new com.vulnaudit.exception.BusinessException(
                        com.vulnaudit.exception.ErrorCode.TOOL_NOT_FOUND));
        if (tool.getFilePath() == null || tool.getFilePath().isBlank()) {
            throw new com.vulnaudit.exception.BusinessException(
                    com.vulnaudit.exception.ErrorCode.NOT_FOUND, "该工具未上传工具包，请查看仓库地址或安装命令");
        }
        java.nio.file.Path path = java.nio.file.Paths.get(tool.getFilePath());
        org.springframework.core.io.Resource resource;
        try {
            resource = new org.springframework.core.io.UrlResource(path.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new com.vulnaudit.exception.BusinessException(
                        com.vulnaudit.exception.ErrorCode.NOT_FOUND, "工具包文件不存在或不可读");
            }
        } catch (java.net.MalformedURLException e) {
            throw new com.vulnaudit.exception.BusinessException(
                    com.vulnaudit.exception.ErrorCode.INTERNAL_ERROR, "工具包路径非法");
        }
        toolService.recordDownload(id, currentUserId);
        String fileName = tool.getFileName() == null ? ("tool-" + id) : tool.getFileName();
        return ResponseEntity.ok()
                .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName + "\"")
                .body(resource);
    }

    @GetMapping("/{id}/download-count")
    @Operation(summary = "获取下载次数", description = "获取工具下载次数")
    public ResponseEntity<ApiResponse<Long>> getDownloadCount(@PathVariable UUID id) {
        log.info("获取工具下载次数: toolId={}", id);
        
        long count = toolService.getDownloadCount(id);
        return ResponseEntity.ok(ApiResponse.success(count));
    }

    @GetMapping("/{id}/tags")
    @Operation(summary = "获取工具标签", description = "获取工具的所有标签")
    public ResponseEntity<ApiResponse<List<String>>> getTags(@PathVariable UUID id) {
        log.info("获取工具标签: toolId={}", id);
        
        List<String> tags = toolService.getTags(id);
        return ResponseEntity.ok(ApiResponse.success(tags));
    }

    @PostMapping("/{id}/tags")
    @Operation(summary = "添加工具标签", description = "为工具添加标签")
    public ResponseEntity<ApiResponse<Void>> addTag(
            @PathVariable UUID id,
            @Parameter(description = "标签") @RequestParam String tag) {
        
        log.info("添加工具标签: toolId={}, tag={}", id, tag);
        
        toolService.addTag(id, tag);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/{id}/tags")
    @Operation(summary = "移除工具标签", description = "移除工具标签")
    public ResponseEntity<ApiResponse<Void>> removeTag(
            @PathVariable UUID id,
            @Parameter(description = "标签") @RequestParam String tag) {
        
        log.info("移除工具标签: toolId={}, tag={}", id, tag);
        
        toolService.removeTag(id, tag);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ==================== 版本管理 ====================

    /**
     * 获取工具版本列表
     */
    @GetMapping("/{id}/versions")
    @Operation(summary = "获取工具版本列表", description = "获取工具的所有版本")
    public ResponseEntity<ApiResponse<List<ToolVersion>>> getToolVersions(@PathVariable UUID id) {
        log.info("获取工具版本列表: toolId={}", id);
        
        List<ToolVersion> versions = toolService.getToolVersions(id);
        return ResponseEntity.ok(ApiResponse.success(versions));
    }

    /**
     * 上传新版本
     */
    @PostMapping("/{id}/versions")
    @Operation(summary = "上传新版本", description = "为工具上传新版本")
    public ResponseEntity<ApiResponse<ToolVersion>> uploadVersion(
            @PathVariable UUID id,
            @Parameter(description = "版本号") @RequestParam String version,
            @Parameter(description = "变更说明") @RequestParam(required = false) String changelog,
            @Parameter(description = "文件") @RequestParam(value = "file", required = false) MultipartFile file) {
        
        log.info("上传工具新版本: toolId={}, version={}", id, version);
        
        ToolVersion toolVersion = toolService.uploadVersion(id, version, changelog, file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(toolVersion));
    }

    private UUID getCurrentUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || "anonymousUser".equals(authentication.getName())) {
            return null;
        }
        return userService.findByUsername(authentication.getName())
                .map(user -> user.getId())
                .orElse(null);
    }
}
