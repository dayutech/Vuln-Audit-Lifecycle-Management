package com.vulnaudit.service;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.Tool;
import com.vulnaudit.entity.ToolVersion;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.web.multipart.MultipartFile;

/**
 * 工具服务接口
 */
public interface ToolService {

    Tool createTool(Tool tool);

    Optional<Tool> findById(UUID id);

    Optional<Tool> findByName(String name);

    PageResponse<Tool> findAll(int page, int size, String keyword, String category);

    List<Tool> findByCategory(String category);

    List<Tool> findPopular(int limit);

    Tool updateTool(UUID id, Tool tool);

    void deleteTool(UUID id);

    boolean existsByName(String name);

    void recordDownload(UUID toolId, UUID userId);

    long getDownloadCount(UUID toolId);

    List<String> getTags(UUID toolId);

    void addTag(UUID toolId, String tag);

    void removeTag(UUID toolId, String tag);

    /**
     * 获取工具版本列表
     * 
     * @param toolId 工具ID
     * @return 版本列表
     */
    List<ToolVersion> getToolVersions(UUID toolId);

    /**
     * 上传新版本
     * 
     * @param toolId 工具ID
     * @param version 版本号
     * @param changelog 变更说明
     * @param file 文件
     * @return 版本信息
     */
    ToolVersion uploadVersion(UUID toolId, String version, String changelog, MultipartFile file);
}
