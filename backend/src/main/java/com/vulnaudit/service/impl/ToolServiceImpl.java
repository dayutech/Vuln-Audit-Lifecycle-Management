package com.vulnaudit.service.impl;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.Tool;
import com.vulnaudit.entity.ToolDownload;
import com.vulnaudit.entity.ToolTag;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.ToolDownloadRepository;
import com.vulnaudit.repository.ToolRepository;
import com.vulnaudit.repository.ToolTagRepository;
import com.vulnaudit.service.ToolService;
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

import java.util.ArrayList;
import java.util.List;
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
        return toolRepository.findById(id);
    }

    @Override
    public Optional<Tool> findByName(String name) {
        return toolRepository.findByName(name);
    }

    @Override
    public PageResponse<Tool> findAll(int page, int size, String keyword, String category) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        
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
        
        return toolRepository.save(existingTool);
    }

    @Override
    @Transactional
    public void deleteTool(UUID id) {
        log.info("删除工具: id={}", id);
        toolRepository.deleteById(id);
    }

    @Override
    public boolean existsByName(String name) {
        return toolRepository.existsByName(name);
    }

    @Override
    @Transactional
    public void recordDownload(UUID toolId, UUID userId) {
        log.info("记录工具下载: toolId={}, userId={}", toolId, userId);
        
        ToolDownload download = ToolDownload.builder()
                .toolId(toolId)
                .userId(userId)
                .build();
        toolDownloadRepository.save(download);
    }

    @Override
    public long getDownloadCount(UUID toolId) {
        return toolDownloadRepository.countByToolId(toolId);
    }

    @Override
    public List<String> getTags(UUID toolId) {
        return toolTagRepository.findByToolId(toolId).stream()
                .map(ToolTag::getTag)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void addTag(UUID toolId, String tag) {
        log.info("添加工具标签: toolId={}, tag={}", toolId, tag);
        
        ToolTag toolTag = ToolTag.builder()
                .toolId(toolId)
                .tag(tag)
                .build();
        toolTagRepository.save(toolTag);
    }

    @Override
    @Transactional
    public void removeTag(UUID toolId, String tag) {
        log.info("移除工具标签: toolId={}, tag={}", toolId, tag);
        toolTagRepository.deleteByToolIdAndTag(toolId, tag);
    }
}
