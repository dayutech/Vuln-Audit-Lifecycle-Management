package com.vulnaudit.service.impl;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.Tool;
import com.vulnaudit.entity.ToolDownload;
import com.vulnaudit.entity.ToolTag;
import com.vulnaudit.entity.ToolVersion;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.ToolDownloadRepository;
import com.vulnaudit.repository.ToolRepository;
import com.vulnaudit.repository.ToolTagRepository;
import com.vulnaudit.repository.ToolVersionRepository;
import com.vulnaudit.service.ToolService;
import com.vulnaudit.util.Paging;
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
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 工具服务实现类
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolServiceImpl implements ToolService {

    private final ToolRepository toolRepository;
    private final ToolDownloadRepository toolDownloadRepository;
    private final ToolTagRepository toolTagRepository;
    private final ToolVersionRepository toolVersionRepository;

    @Override
    @Transactional
    public Tool createTool(Tool tool) {
        log.info("创建工具: name={}", tool.getName());
        
        if (toolRepository.existsByName(tool.getName())) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        
        return toolRepository.save(tool);
    }

    @Override
    public Optional<Tool> findById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        return toolRepository.findById(id);
    }

    @Override
    public Optional<Tool> findByName(String name) {
        return toolRepository.findByName(name);
    }

    @Override
    public PageResponse<Tool> findAll(int page, int size, String keyword, String category) {
        Pageable pageable = PageRequest.of(Paging.page(page), Paging.size(size), Sort.by("createdAt").descending());
        
        Specification<Tool> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            
            if (StringUtils.hasText(keyword)) {
                jakarta.persistence.criteria.Predicate nameLike = cb.like(root.get("name"), "%" + keyword + "%");
                jakarta.persistence.criteria.Predicate descLike = cb.like(root.get("description"), "%" + keyword + "%");
                predicates.add(cb.or(nameLike, descLike));
            }
            
            if (StringUtils.hasText(category)) {
                predicates.add(cb.equal(root.get("category"), category));
            }
            
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        
        Page<Tool> toolPage = toolRepository.findAll(spec, pageable);
        
        return PageResponse.<Tool>builder()
                .content(toolPage.getContent())
                .totalElements(toolPage.getTotalElements())
                .totalPages(toolPage.getTotalPages())
                .currentPage(toolPage.getNumber())
                .pageSize(toolPage.getSize())
                .build();
    }

    @Override
    public List<Tool> findByCategory(String category) {
        return toolRepository.findByCategory(category);
    }

    @Override
    public List<Tool> findPopular(int limit) {
        Pageable pageable = PageRequest.of(0, limit, Sort.by("downloadCount").descending());
        return toolRepository.findAll(pageable).getContent();
    }

    @Override
    @Transactional
    public Tool updateTool(UUID id, Tool tool) {
        log.info("更新工具: id={}", id);

        if (id == null) {
            throw new BusinessException(ErrorCode.TOOL_NOT_FOUND);
        }
        Tool existingTool = toolRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.TOOL_NOT_FOUND));
        
        if (tool.getName() != null) {
            existingTool.setName(tool.getName());
        }
        if (tool.getDescription() != null) {
            existingTool.setDescription(tool.getDescription());
        }
        if (tool.getCategory() != null) {
            existingTool.setCategory(tool.getCategory());
        }
        if (tool.getRepositoryUrl() != null) {
            existingTool.setRepositoryUrl(tool.getRepositoryUrl());
        }
        
        return toolRepository.save(Objects.requireNonNull(existingTool));
    }

    @Override
    @Transactional
    public void deleteTool(UUID id) {
        log.info("删除工具: id={}", id);
        if (id != null) {
            toolRepository.deleteById(id);
        }
    }

    @Override
    public boolean existsByName(String name) {
        return toolRepository.existsByName(name);
    }

    @Override
    @Transactional
    public void recordDownload(UUID toolId, UUID userId) {
        log.info("记录工具下载: toolId={}, userId={}", toolId, userId);

        if (toolId == null) {
            throw new BusinessException(ErrorCode.TOOL_NOT_FOUND);
        }
        Tool tool = toolRepository.findById(toolId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TOOL_NOT_FOUND));

        ToolDownload download = ToolDownload.builder()
                .toolId(toolId)
                .userId(userId)
                .build();
        toolDownloadRepository.save(Objects.requireNonNull(download));

        tool.setDownloadCount(tool.getDownloadCount() + 1);
        toolRepository.save(tool);
    }

    @Override
    public long getDownloadCount(UUID toolId) {
        return toolDownloadRepository.countByToolId(toolId);
    }

    /**
     * 保存工具包文件。
     *
     * <p>文件落在 ./uploads/tools/{toolId}/ 下，文件名加随机前缀避免覆盖与路径穿越。
     */
    @Override
    @Transactional
    public Tool storeToolFile(UUID toolId, org.springframework.web.multipart.MultipartFile file, UUID operatorId) {
        if (toolId == null) {
            throw new BusinessException(ErrorCode.TOOL_NOT_FOUND);
        }
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "上传文件不能为空");
        }
        Tool tool = toolRepository.findById(toolId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TOOL_NOT_FOUND));

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) {
            originalName = "tool-package.bin";
        }
        // 只保留文件名部分，去掉任何目录成分，防止路径穿越
        originalName = originalName.replace("\\", "/");
        int slash = originalName.lastIndexOf('/');
        if (slash >= 0) {
            originalName = originalName.substring(slash + 1);
        }
        String storedName = UUID.randomUUID().toString().substring(0, 8) + "_" + originalName;

        try {
            java.nio.file.Path dir = java.nio.file.Paths.get("./uploads/tools/" + toolId);
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Path target = dir.resolve(storedName);
            java.nio.file.Files.copy(file.getInputStream(), target,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            tool.setFileName(originalName);
            tool.setFilePath(target.toString());
            tool.setFileSize(file.getSize());
            log.info("工具包已保存: toolId={}, path={}, size={}", toolId, target, file.getSize());
            return toolRepository.save(tool);
        } catch (java.io.IOException e) {
            log.error("保存工具包失败: toolId={}", toolId, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "保存工具包失败: " + e.getMessage());
        }
    }

    @Override
    public List<String> getTags(UUID toolId) {
        if (toolId == null) {
            return Collections.emptyList();
        }
        return toolTagRepository.findByToolId(toolId).stream()
                .map(ToolTag::getTag)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void addTag(UUID toolId, String tag) {
        log.info("添加工具标签: toolId={}, tag={}", toolId, tag);
        if (toolId == null) {
            return;
        }
        ToolTag toolTag = ToolTag.builder()
                .toolId(toolId)
                .tag(tag)
                .build();
        toolTagRepository.save(Objects.requireNonNull(toolTag));
    }

    @Override
    @Transactional
    public void removeTag(UUID toolId, String tag) {
        log.info("移除工具标签: toolId={}, tag={}", toolId, tag);
        if (toolId != null) {
            toolTagRepository.deleteByToolIdAndTag(toolId, tag);
        }
    }

    /**
     * 获取工具版本列表
     */
    @Override
    public List<ToolVersion> getToolVersions(UUID toolId) {
        log.debug("获取工具版本列表: toolId={}", toolId);
        if (toolId == null) {
            return Collections.emptyList();
        }
        return toolVersionRepository.findByToolIdOrderByCreatedAtDesc(toolId);
    }

    @Override
    @Transactional
    public ToolVersion uploadVersion(UUID toolId, String version, String changelog, MultipartFile file) {
        log.info("上传工具新版本: toolId={}, version={}", toolId, version);

        if (toolId == null) {
            throw new BusinessException(ErrorCode.TOOL_NOT_FOUND);
        }
        toolRepository.findById(toolId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TOOL_NOT_FOUND));

        String filePath = "uploads/tools/" + toolId + "/" + version;
        
        ToolVersion toolVersion = ToolVersion.builder()
                .toolId(toolId)
                .version(version)
                .filePath(filePath)
                .changelog(changelog)
                .createdBy(null)
                .createdAt(LocalDateTime.now())
                .build();
        
        return toolVersionRepository.save(Objects.requireNonNull(toolVersion));
    }
}
