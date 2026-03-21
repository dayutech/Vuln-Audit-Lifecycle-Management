package com.vulnaudit.service;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.Tool;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
}
