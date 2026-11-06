package com.vulnaudit.service.impl;

import com.vulnaudit.dto.IndustryTypeOption;
import com.vulnaudit.entity.IndustryType;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.IndustryTypeRepository;
import com.vulnaudit.service.IndustryTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/** 行业类型实现（t129） */
@Slf4j
@Service
@RequiredArgsConstructor
public class IndustryTypeServiceImpl implements IndustryTypeService {

    private static final int MAX_LIMIT = 100;

    private final IndustryTypeRepository repository;

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
                .filter(IndustryType::isEnabled)
                .map(this::toOption)
                .orElse(null);
    }

    private IndustryTypeOption toOption(IndustryType type) {
        return new IndustryTypeOption(type.getCode(), type.getName(), type.getParentCode(), type.isBuiltin());
    }
}
