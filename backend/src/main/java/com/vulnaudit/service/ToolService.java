package com.vulnaudit.service;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.Tool;
import com.vulnaudit.entity.ToolVersion;

import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.web.multipart.MultipartFile;

/**
 * 工具服务接口
 */
public interface ToolService {

    @Nonnull
    Tool createTool(@Nonnull Tool tool);

    Optional<Tool> findById(@Nullable UUID id);

    Optional<Tool> findByName(@Nullable String name);

    @Nonnull
    PageResponse<Tool> findAll(int page, int size, @Nullable String keyword, @Nullable String category);

    @Nonnull
    List<Tool> findByCategory(@Nullable String category);

    @Nonnull
    List<Tool> findPopular(int limit);

    @Nonnull
    Tool updateTool(@Nullable UUID id, @Nonnull Tool tool);

    void deleteTool(@Nullable UUID id);

    boolean existsByName(@Nullable String name);

    void recordDownload(@Nullable UUID toolId, @Nullable UUID userId);

    /**
     * 保存工具包文件并更新工具的 fileName / filePath / fileSize。
     *
     * @param toolId    工具ID
     * @param file      上传的文件
     * @param operatorId 操作人
     * @return 更新后的工具
     */
    Tool storeToolFile(@Nullable UUID toolId,
                       org.springframework.web.multipart.MultipartFile file,
                       @Nullable UUID operatorId);

    long getDownloadCount(@Nullable UUID toolId);

    @Nonnull
    List<String> getTags(@Nullable UUID toolId);

    void addTag(@Nullable UUID toolId, @Nullable String tag);

    void removeTag(@Nullable UUID toolId, @Nullable String tag);

    @Nonnull
    List<ToolVersion> getToolVersions(@Nullable UUID toolId);

    @Nonnull
    ToolVersion uploadVersion(@Nullable UUID toolId, @Nullable String version, @Nullable String changelog, @Nullable MultipartFile file);

    /**
     * 按 工具ID + 版本ID 查询版本（下载前校验归属）
     *
     * @param toolId    工具ID
     * @param versionId 版本ID
     * @return 版本（不存在或不属于该工具时为空）
     */
    @Nonnull
    java.util.Optional<ToolVersion> findVersion(@Nullable UUID toolId, @Nullable UUID versionId);
}