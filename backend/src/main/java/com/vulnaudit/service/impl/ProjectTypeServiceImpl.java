package com.vulnaudit.service.impl;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.dto.ProjectTypeAdminVO;
import com.vulnaudit.dto.ProjectTypeOption;
import com.vulnaudit.dto.ProjectTypeUpsertRequest;
import com.vulnaudit.entity.ProjectType;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.ProjectRepository;
import com.vulnaudit.repository.ProjectTypeRepository;
import com.vulnaudit.service.AuditLogService;
import com.vulnaudit.service.ProjectTypeService;
import com.vulnaudit.util.Paging;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 项目类型实现（t130）
 *
 * <p>与行业类型（{@code IndustryTypeServiceImpl}）同一套管理规则：
 * 内置项只能改 sortOrder/enabled；删除为软删；被项目引用时 409。
 * code 全局唯一（含软删行），因此软删后不可复用同一编码。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectTypeServiceImpl implements ProjectTypeService {

    private static final String AUDIT_ACTION = "UPDATE_PROJECT_TYPE";

    private final ProjectTypeRepository repository;
    private final ProjectRepository projectRepository;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public boolean ensureBuiltin() {
        if (repository.count() > 0) {
            return false;
        }
        for (Object[] item : BUILTIN_PROJECT_TYPES) {
            repository.save(ProjectType.builder()
                    .code((String) item[0])
                    .name((String) item[1])
                    .builtin(true)
                    .sortOrder((Integer) item[2])
                    .enabled(true)
                    .build());
        }
        log.info("已初始化内置项目类型: count={}", BUILTIN_PROJECT_TYPES.size());
        return true;
    }

    @Override
    public List<ProjectTypeOption> options() {
        return repository.findByEnabledTrueAndDeletedAtIsNullOrderByBuiltinDescSortOrderAscCodeAsc().stream()
                .map(item -> new ProjectTypeOption(item.getCode(), item.getName(), item.isBuiltin()))
                .toList();
    }

    @Override
    public ProjectTypeOption requireEnabled(String code) {
        if (!StringUtils.hasText(code)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "项目类型编码不能为空");
        }
        ProjectType type = repository.findByCodeAndDeletedAtIsNull(code.trim())
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "项目类型不存在: " + code));
        if (!type.isEnabled()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "项目类型已停用: " + code);
        }
        return new ProjectTypeOption(type.getCode(), type.getName(), type.isBuiltin());
    }

    @Override
    public ProjectTypeOption matchByCode(String code) {
        if (!StringUtils.hasText(code)) {
            return null;
        }
        return repository.findByCodeAndDeletedAtIsNull(code.trim())
                .map(item -> new ProjectTypeOption(item.getCode(), item.getName(), item.isBuiltin()))
                .orElse(null);
    }

    // ------------------------------------------------------------------ 管理员

    @Override
    public PageResponse<ProjectTypeAdminVO> adminList(String keyword, boolean includeDisabled, int page, int size) {
        int safePage = Paging.page(page);
        int safeSize = Paging.size(size);
        String kw = keyword == null ? "" : keyword.trim();
        Page<ProjectType> result = repository.searchAdmin(kw, includeDisabled, PageRequest.of(safePage, safeSize));
        return PageResponse.of(result.getContent().stream().map(ProjectTypeAdminVO::from).toList(),
                safePage, safeSize, result.getTotalElements());
    }

    @Override
    @Transactional
    public ProjectTypeAdminVO create(ProjectTypeUpsertRequest request, UUID operatorId) {
        request.requireCreateFields();
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (repository.existsByCode(code)) {
            throw new BusinessException(ErrorCode.CONFLICT, "项目类型编码已存在: " + code);
        }
        ProjectType saved = repository.save(ProjectType.builder()
                .code(code)
                .name(request.name().trim())
                .builtin(false)
                .sortOrder(request.sortOrder() == null ? 0 : request.sortOrder())
                .enabled(request.enabled() == null || request.enabled())
                .build());
        audit(operatorId, saved, "新建项目类型: code=" + saved.getCode() + ", name=" + saved.getName());
        log.info("新建项目类型: code={}, id={}", saved.getCode(), saved.getId());
        return ProjectTypeAdminVO.from(saved);
    }

    @Override
    @Transactional
    public ProjectTypeAdminVO update(UUID id, ProjectTypeUpsertRequest request, UUID operatorId) {
        ProjectType type = requireActive(id);
        boolean nameChanged = StringUtils.hasText(request.name()) && !request.name().trim().equals(type.getName());
        boolean codeChanged = StringUtils.hasText(request.code())
                && !request.code().trim().toUpperCase(Locale.ROOT).equals(type.getCode());

        if (type.isBuiltin()) {
            if (nameChanged || codeChanged) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "内置项目类型不可修改");
            }
        } else if (nameChanged) {
            type.setName(request.name().trim());
        }
        if (request.sortOrder() != null) {
            type.setSortOrder(request.sortOrder());
        }
        if (request.enabled() != null) {
            type.setEnabled(request.enabled());
        }
        ProjectType saved = repository.save(type);
        audit(operatorId, saved, "更新项目类型: code=" + saved.getCode()
                + ", enabled=" + saved.isEnabled() + ", sortOrder=" + saved.getSortOrder());
        return ProjectTypeAdminVO.from(saved);
    }

    @Override
    @Transactional
    public void delete(UUID id, UUID operatorId) {
        ProjectType type = requireActive(id);
        if (type.isBuiltin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "内置项目类型不可修改");
        }
        long used = projectRepository.countByTypeCode(type.getCode());
        if (used > 0) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "已有 " + used + " 个项目使用该类型，请先调整项目类型");
        }
        type.setDeletedAt(LocalDateTime.now());
        type.setEnabled(false);
        repository.save(type);
        audit(operatorId, type, "删除项目类型（软删）: code=" + type.getCode());
        log.info("软删项目类型: code={}, id={}", type.getCode(), id);
    }

    private ProjectType requireActive(UUID id) {
        return repository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "项目类型不存在"));
    }

    private void audit(UUID operatorId, ProjectType type, String detail) {
        auditLogService.log(operatorId, AUDIT_ACTION, "project_type", type.getId(), detail);
    }
}
