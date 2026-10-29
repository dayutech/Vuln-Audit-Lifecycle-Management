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
import com.vulnaudit.security.CurrentUserProvider;
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
    private final CurrentUserProvider currentUserProvider;
    private final org.springframework.transaction.PlatformTransactionManager transactionManager;

    /**
     * 版本号冲突的最大尝试次数（含首次）
     */
    private static final int MAX_VERSION_ATTEMPTS = 3;

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

        // 只保留文件名部分，去掉任何目录成分，防止路径穿越
        String originalName = sanitizeFileName(file.getOriginalFilename());
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
    public ToolVersion uploadVersion(UUID toolId, String version, String changelog, MultipartFile file) {
        log.info("上传工具新版本: toolId={}, version={}, hasFile={}", toolId, version, file != null && !file.isEmpty());

        if (toolId == null) {
            throw new BusinessException(ErrorCode.TOOL_NOT_FOUND);
        }
        toolRepository.findById(toolId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TOOL_NOT_FOUND));

        // tool_versions.created_by 为 NOT NULL：统一从 SecurityContext 解析，缺失直接 401（而不是落库 500）
        UUID createdBy = currentUserProvider.requireCurrentUserId();

        String requestedVersion = version == null ? null : version.trim();
        if (requestedVersion != null && requestedVersion.length() > 50) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "版本号长度不能超过 50 个字符");
        }

        // MultipartFile 的流只能安全读一次，重试复用同一份字节
        byte[] fileBytes = readFileBytes(file);
        String originalFileName = file == null ? null : file.getOriginalFilename();

        // 乐观重试：唯一键冲突只在 flush/commit 时暴露，因此每次尝试使用独立事务重算 versionNo
        for (int attempt = 1; attempt <= MAX_VERSION_ATTEMPTS; attempt++) {
            final int currentAttempt = attempt;
            try {
                ToolVersion created = transactionTemplate().execute(status ->
                        createVersionOnce(toolId, requestedVersion, changelog, fileBytes, originalFileName,
                                createdBy, currentAttempt));
                if (created != null) {
                    log.info("工具版本创建成功: toolId={}, versionId={}, versionNo={}, attempt={}",
                            toolId, created.getId(), created.getVersionNo(), currentAttempt);
                    return created;
                }
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                log.warn("工具版本号冲突，准备重试: toolId={}, attempt={}/{}, cause={}",
                        toolId, attempt, MAX_VERSION_ATTEMPTS,
                        e.getMostSpecificCause() == null ? e.getMessage() : e.getMostSpecificCause().getMessage());
                // 退避 + 抖动：给并发赢家一点提交时间，避免同一时刻再次撞号
                if (attempt < MAX_VERSION_ATTEMPTS) {
                    backoffQuietly(attempt);
                }
            }
        }

        // 重试耗尽：明确返回 409，交由前端重试（而不是 500）
        log.warn("工具版本号冲突重试耗尽: toolId={}, attempts={}", toolId, MAX_VERSION_ATTEMPTS);
        throw new BusinessException(ErrorCode.CONFLICT, "版本号冲突，请重试");
    }

    /**
     * 单次尝试（独立事务）
     *
     * <p>使用 {@code saveAndFlush} 让唯一键冲突在本方法内抛出，从而能被外层重试捕获。
     */
    private ToolVersion createVersionOnce(UUID toolId, String requestedVersion, String changelog,
                                          byte[] fileBytes, String originalFileName,
                                          UUID createdBy, int attempt) {
        // 行级锁串行化同一工具的版本号分配，避免并发线程算出相同 versionNo
        toolRepository.findByIdForUpdate(toolId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TOOL_NOT_FOUND));

        int nextVersionNo = toolVersionRepository.findFirstByToolIdOrderByVersionNoDesc(toolId)
                .map(existing -> existing.getVersionNo() == null ? 1 : existing.getVersionNo() + 1)
                .orElse(1);
        String versionLabel = requestedVersion == null ? "v" + nextVersionNo : requestedVersion;

        ToolVersion toolVersion = ToolVersion.builder()
                .toolId(toolId)
                .version(versionLabel)
                .versionNo(nextVersionNo)
                .changelog(changelog)
                // 仅元数据版本没有文件，列 NOT NULL 用空串占位；hasFile 派生属性据此返回 false
                .filePath("")
                .createdBy(createdBy)
                .createdAt(LocalDateTime.now())
                .build();

        log.debug("尝试创建工具版本: toolId={}, versionNo={}, attempt={}", toolId, nextVersionNo, attempt);

        // 冲突在此抛出（(tool_id, version_no) 唯一约束）
        ToolVersion saved = toolVersionRepository.saveAndFlush(Objects.requireNonNull(toolVersion));

        if (fileBytes != null) {
            StoredFile stored = storeVersionFile(toolId, saved.getId(), originalFileName, fileBytes);
            saved.setFileName(stored.fileName());
            saved.setFilePath(stored.filePath());
            saved.setFileSize(stored.fileSize());
            saved = toolVersionRepository.saveAndFlush(saved);
            log.info("工具版本文件已保存: toolId={}, versionId={}, path={}, size={}",
                    toolId, saved.getId(), stored.filePath(), stored.fileSize());
        }

        return saved;
    }

    /**
     * 读取上传文件字节；空文件返回 null
     */
    private byte[] readFileBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        try {
            return file.getBytes();
        } catch (java.io.IOException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "读取上传文件失败: " + e.getMessage());
        }
    }

    /**
     * 每次尝试使用新的 TransactionTemplate（一次 execute = 一个独立事务）
     */
    private org.springframework.transaction.support.TransactionTemplate transactionTemplate() {
        return new org.springframework.transaction.support.TransactionTemplate(transactionManager);
    }

    /**
     * 重试退避：第 n 次失败后等待 n*15ms + 0~20ms 抖动
     */
    private void backoffQuietly(int attempt) {
        long sleepMs = attempt * 15L + java.util.concurrent.ThreadLocalRandom.current().nextLong(20);
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public java.util.Optional<ToolVersion> findVersion(UUID toolId, UUID versionId) {
        if (toolId == null || versionId == null) {
            return java.util.Optional.empty();
        }
        return toolVersionRepository.findByIdAndToolId(versionId, toolId);
    }

    /**
     * 保存版本文件到 ./uploads/tools/{toolId}/versions/{versionId}/，文件名剥离路径穿越字符
     */
    private StoredFile storeVersionFile(UUID toolId, UUID versionId, String originalFileName, byte[] fileBytes) {
        String originalName = sanitizeFileName(originalFileName);
        String storedName = UUID.randomUUID().toString().substring(0, 8) + "_" + originalName;

        try {
            java.nio.file.Path dir = java.nio.file.Paths.get("./uploads/tools/" + toolId + "/versions/" + versionId);
            java.nio.file.Files.createDirectories(dir);
            java.nio.file.Path target = dir.resolve(storedName);
            java.nio.file.Files.write(target, fileBytes);
            return new StoredFile(originalName, target.toString(), fileBytes.length);
        } catch (java.io.IOException e) {
            log.error("保存工具版本文件失败: toolId={}, versionId={}", toolId, versionId, e);
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "保存版本文件失败: " + e.getMessage());
        }
    }

    /**
     * 文件名清洗：只保留最后一段，去掉路径分隔符与控制字符，防止路径穿越
     */
    private String sanitizeFileName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "tool-package.bin";
        }
        String name = originalName.replace("\\", "/");
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = name.replaceAll("[\\p{Cntrl}]", "").replace("..", "_").trim();
        return name.isEmpty() ? "tool-package.bin" : name;
    }

    /**
     * 落盘结果
     */
    private record StoredFile(String fileName, String filePath, long fileSize) {
    }
}
