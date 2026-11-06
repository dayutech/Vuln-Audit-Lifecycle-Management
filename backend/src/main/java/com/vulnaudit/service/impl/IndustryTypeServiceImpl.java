package com.vulnaudit.service.impl;

import com.vulnaudit.dto.IndustryTypeAdminVO;
import com.vulnaudit.dto.IndustryTypeOption;
import com.vulnaudit.dto.IndustryTypeUpsertRequest;
import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.IndustryType;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.CustomerRepository;
import com.vulnaudit.repository.IndustryTypeRepository;
import com.vulnaudit.service.AuditLogService;
import com.vulnaudit.service.IndustryTypeService;
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
import java.util.UUID;

/**
 * 行业类型实现（t129 建立，t130 增加管理员 CRUD）
 *
 * <p>删除策略：**软删**（写 {@code deleted_at}）。客户表仍保留 {@code industry_code}，
 * 物理删除会让历史客户解析不出行业名；软删后「检索 / 下拉 / 管理员列表」都不再返回，
 * {@code requireEnabled} 也会按「不存在」处理（400）。
 *
 * <p>权限：内置项（{@code builtin=true}）只允许调整 {@code sortOrder}/{@code enabled}，
 * 改 code/name 或删除一律 403「内置行业不可修改」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IndustryTypeServiceImpl implements IndustryTypeService {

    private static final int MAX_LIMIT = 100;
    /** 审计动作名（与前端/报告口径一致） */
    private static final String AUDIT_ACTION = "UPDATE_INDUSTRY_TYPE";

    private final IndustryTypeRepository repository;
    private final CustomerRepository customerRepository;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public boolean ensureBuiltin() {
        if (repository.count() > 0) {
            return false;
        }
        int order = 0;
        for (String[] item : BUILTIN_INDUSTRIES) {
            repository.save(IndustryType.builder()
                    .code(item[0])
                    .name(item[1])
                    .parentCode(item[2])
                    .builtin(true)
                    .sortOrder(order++)
                    .enabled(true)
                    .build());
        }
        log.info("已初始化内置行业类型: count={}", BUILTIN_INDUSTRIES.size());
        return true;
    }

    @Override
    public List<IndustryTypeOption> search(String keyword, int limit) {
        int effective = limit <= 0 ? 20 : Math.min(limit, MAX_LIMIT);
        String kw = keyword == null ? "" : keyword.trim();
        return repository.search(kw, PageRequest.of(0, effective)).stream()
                .map(this::toOption)
                .toList();
    }

    @Override
    public IndustryTypeOption requireEnabled(String code) {
        if (!StringUtils.hasText(code)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "行业编码不能为空");
        }
        IndustryType type = repository.findByCode(code.trim())
                .filter(item -> !item.isDeleted())
                .orElseThrow(() -> new BusinessException(ErrorCode.BAD_REQUEST, "行业编码不存在: " + code));
        if (!type.isEnabled()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "行业已停用: " + code);
        }
        return toOption(type);
    }

    @Override
    public IndustryTypeOption matchByName(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        return repository.findFirstByName(name.trim())
                .filter(item -> !item.isDeleted())
                .filter(IndustryType::isEnabled)
                .map(this::toOption)
                .orElse(null);
    }

    // ------------------------------------------------------------------ 管理员（t130）

    @Override
    public PageResponse<IndustryTypeAdminVO> adminList(String keyword, boolean includeDisabled, int page, int size) {
        int safePage = Paging.page(page);
        int safeSize = Paging.size(size);
        String kw = keyword == null ? "" : keyword.trim();
        Page<IndustryType> result = repository.searchAdmin(kw, includeDisabled, PageRequest.of(safePage, safeSize));
        return PageResponse.of(result.getContent().stream().map(IndustryTypeAdminVO::from).toList(),
                safePage, safeSize, result.getTotalElements());
    }

    @Override
    @Transactional
    public IndustryTypeAdminVO create(IndustryTypeUpsertRequest request, UUID operatorId) {
        request.requireCreateFields();
        String code = request.code().trim();
        // code 全局唯一（含软删行）：软删过的编码不可复用，避免历史客户引用歧义
        if (repository.existsByCode(code)) {
            throw new BusinessException(ErrorCode.CONFLICT, "行业编码已存在: " + code);
        }
        IndustryType saved = repository.save(IndustryType.builder()
                .code(code)
                .name(request.name().trim())
                .parentCode(StringUtils.hasText(request.parentCode()) ? request.parentCode().trim() : null)
                .builtin(false)
                .sortOrder(request.sortOrder() == null ? 0 : request.sortOrder())
                .enabled(request.enabled() == null || request.enabled())
                .build());
        audit(operatorId, saved, "新建行业: code=" + saved.getCode() + ", name=" + saved.getName());
        log.info("新建行业类型: code={}, id={}", saved.getCode(), saved.getId());
        return IndustryTypeAdminVO.from(saved);
    }

    @Override
    @Transactional
    public IndustryTypeAdminVO update(UUID id, IndustryTypeUpsertRequest request, UUID operatorId) {
        IndustryType type = requireActive(id);
        boolean nameChanged = StringUtils.hasText(request.name()) && !request.name().trim().equals(type.getName());
        boolean codeChanged = StringUtils.hasText(request.code()) && !request.code().trim().equals(type.getCode());

        if (type.isBuiltin()) {
            // 内置项：code/name 不可改（403），sortOrder/enabled 允许调整
            if (nameChanged || codeChanged) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "内置行业不可修改");
            }
        } else {
            if (nameChanged) {
                type.setName(request.name().trim());
            }
            if (request.parentCode() != null) {
                type.setParentCode(StringUtils.hasText(request.parentCode()) ? request.parentCode().trim() : null);
            }
        }
        if (request.sortOrder() != null) {
            type.setSortOrder(request.sortOrder());
        }
        if (request.enabled() != null) {
            type.setEnabled(request.enabled());
        }
        IndustryType saved = repository.save(type);
        audit(operatorId, saved, "更新行业: code=" + saved.getCode()
                + ", enabled=" + saved.isEnabled() + ", sortOrder=" + saved.getSortOrder());
        return IndustryTypeAdminVO.from(saved);
    }

    @Override
    @Transactional
    public void delete(UUID id, UUID operatorId) {
        IndustryType type = requireActive(id);
        if (type.isBuiltin()) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "内置行业不可修改");
        }
        long used = customerRepository.countByIndustryCode(type.getCode());
        if (used > 0) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "已有 " + used + " 个客户使用该行业，请先调整客户行业");
        }
        type.setDeletedAt(LocalDateTime.now());
        type.setEnabled(false);
        repository.save(type);
        audit(operatorId, type, "删除行业（软删）: code=" + type.getCode());
        log.info("软删行业类型: code={}, id={}", type.getCode(), id);
    }

    private IndustryType requireActive(UUID id) {
        return repository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "行业类型不存在"));
    }

    /** 审计（t130）：一次写操作恰好 1 条，details 含动作 + code */
    private void audit(UUID operatorId, IndustryType type, String detail) {
        auditLogService.log(operatorId, AUDIT_ACTION, "industry_type", type.getId(), detail);
    }

    private IndustryTypeOption toOption(IndustryType type) {
        return new IndustryTypeOption(type.getCode(), type.getName(), type.getParentCode(), type.isBuiltin());
    }
}
