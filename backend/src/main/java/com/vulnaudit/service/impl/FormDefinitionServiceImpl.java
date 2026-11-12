package com.vulnaudit.service.impl;

import com.vulnaudit.dto.FormDefinitionRequests;
import com.vulnaudit.dto.FormDefinitionVO;
import com.vulnaudit.dto.FormFieldVO;
import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.FormDefinition;
import com.vulnaudit.entity.FormField;
import com.vulnaudit.entity.enums.FormFieldSource;
import com.vulnaudit.entity.enums.FormFieldType;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.FormDefinitionRepository;
import com.vulnaudit.repository.FormFieldRepository;
import com.vulnaudit.repository.FormSubmissionRepository;
import com.vulnaudit.repository.FormValueRepository;
import com.vulnaudit.service.AuditLogService;
import com.vulnaudit.service.FormDefinitionService;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * 表模板/字段 CRUD 实现（t178）
 *
 * <p>「字段已有数据」判定用 `form_value` 计数：一旦生成过实例，字段类型就不能改、字段也不能删——
 * 否则历史表单的列含义会变，导出会错位。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FormDefinitionServiceImpl implements FormDefinitionService {

    private static final String AUDIT_DEFINITION = "UPDATE_FORM_DEFINITION";
    private static final String AUDIT_FIELD = "UPDATE_FORM_FIELD";

    private final FormDefinitionRepository definitionRepository;
    private final FormFieldRepository fieldRepository;
    private final FormSubmissionRepository submissionRepository;
    private final FormValueRepository formValueRepository;
    private final AuditLogService auditLogService;

    // ------------------------------------------------------------------ 模板

    @Override
    public PageResponse<FormDefinitionVO> list(@Nullable String keyword, @Nullable Boolean enabled, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : Math.min(size, 100);
        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        String like = StringUtils.hasText(keyword)
                ? "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%" : null;
        Page<FormDefinition> result = definitionRepository.search(like, enabled, pageable);
        return PageResponse.<FormDefinitionVO>builder()
                .content(result.getContent().stream().map(definition -> toVO(definition, null)).toList())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .currentPage(safePage)
                .pageSize(safeSize)
                .build();
    }

    @Override
    public FormDefinitionVO detail(UUID id) {
        FormDefinition definition = requireDefinition(id);
        return toVO(definition, listFieldVOs(definition.getId()));
    }

    @Override
    @Transactional
    public FormDefinitionVO create(FormDefinitionRequests.CreateDefinition request, @Nullable UUID operatorId) {
        if (request == null || !StringUtils.hasText(request.code()) || !StringUtils.hasText(request.name())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "code 与 name 必填");
        }
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        // 唯一性含软删行：编码复用会让历史表单指向混乱
        if (definitionRepository.findByCode(code).isPresent()) {
            throw new BusinessException(ErrorCode.CONFLICT, "模板编码已存在: " + code);
        }
        FormDefinition saved = definitionRepository.save(Objects.requireNonNull(FormDefinition.builder()
                .code(code)
                .name(request.name().trim())
                .description(request.description())
                .enabled(request.enabled() == null || request.enabled())
                .createdBy(operatorId)
                .build()));
        audit(operatorId, AUDIT_DEFINITION, "form_definition", saved.getId(), "新建模板: code=" + code + ", name=" + saved.getName());
        return toVO(saved, List.of());
    }

    @Override
    @Transactional
    public FormDefinitionVO update(UUID id, FormDefinitionRequests.UpdateDefinition request, @Nullable UUID operatorId) {
        FormDefinition definition = requireDefinition(id);
        if (StringUtils.hasText(request.code())
                && !request.code().trim().toUpperCase(Locale.ROOT).equals(definition.getCode())) {
            // code 不可改：它是历史表单的稳定标识
            throw new BusinessException(ErrorCode.FORBIDDEN, "模板编码（code）创建后不可修改");
        }
        if (StringUtils.hasText(request.name())) {
            definition.setName(request.name().trim());
        }
        if (request.description() != null) {
            definition.setDescription(request.description());
        }
        if (request.enabled() != null) {
            definition.setEnabled(request.enabled());
        }
        FormDefinition saved = definitionRepository.save(definition);
        audit(operatorId, AUDIT_DEFINITION, "form_definition", saved.getId(),
                "更新模板: code=" + saved.getCode() + ", enabled=" + saved.isEnabled());
        return toVO(saved, listFieldVOs(saved.getId()));
    }

    @Override
    @Transactional
    public void delete(UUID id, @Nullable UUID operatorId) {
        FormDefinition definition = requireDefinition(id);
        long referenced = submissionRepository.countByDefinitionId(definition.getId());
        if (referenced > 0) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "该模板已被 " + referenced + " 个表单实例引用，不能删除（可改为停用）");
        }
        definition.setDeletedAt(LocalDateTime.now());
        definitionRepository.save(definition);
        audit(operatorId, AUDIT_DEFINITION, "form_definition", definition.getId(), "删除模板（软删）: code=" + definition.getCode());
        log.info("软删表单模板: code={}, id={}", definition.getCode(), id);
    }

    // ------------------------------------------------------------------ 字段

    @Override
    @Transactional
    public FormFieldVO addField(UUID definitionId, FormDefinitionRequests.FieldUpsert request,
                                @Nullable UUID operatorId) {
        FormDefinition definition = requireDefinition(definitionId);
        FormField draft = buildField(definition.getId(), request, null);
        if (fieldRepository.findByDefinitionIdAndFieldKey(definition.getId(), draft.getFieldKey()).isPresent()) {
            throw new BusinessException(ErrorCode.CONFLICT, "字段 key 在该模板内已存在: " + draft.getFieldKey());
        }
        if (request.sortOrder() == null) {
            // 未指定则追加到末尾
            draft.setSortOrder((int) fieldRepository.countByDefinitionIdAndDeletedAtIsNull(definition.getId()));
        }
        FormField saved = fieldRepository.save(draft);
        audit(operatorId, AUDIT_FIELD, "form_field", saved.getId(),
                "新增字段: definition=" + definition.getCode() + ", key=" + saved.getFieldKey());
        return toFieldVO(saved);
    }

    @Override
    @Transactional
    public FormFieldVO updateField(UUID definitionId, UUID fieldId, FormDefinitionRequests.FieldUpsert request,
                                   @Nullable UUID operatorId) {
        FormDefinition definition = requireDefinition(definitionId);
        FormField field = requireField(definition.getId(), fieldId);
        boolean hasData = formValueRepository.countByFieldId(field.getId()) > 0;

        // 有数据：禁止改 field_type（历史值含义会变）；field_key 也不许改（导出列名）
        if (hasData && StringUtils.hasText(request.fieldType())
                && !request.fieldType().trim().toUpperCase(Locale.ROOT).equals(field.getFieldType())) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "该字段已产生表单数据，不能修改字段类型（可改为停用模板或新建字段）");
        }
        if (hasData && StringUtils.hasText(request.fieldKey())
                && !request.fieldKey().trim().equals(field.getFieldKey())) {
            throw new BusinessException(ErrorCode.CONFLICT, "该字段已产生表单数据，不能修改字段 key");
        }

        if (StringUtils.hasText(request.fieldKey())) {
            String key = request.fieldKey().trim();
            fieldRepository.findByDefinitionIdAndFieldKey(definition.getId(), key)
                    .filter(existing -> !existing.getId().equals(field.getId()))
                    .ifPresent(existing -> {
                        throw new BusinessException(ErrorCode.CONFLICT, "字段 key 在该模板内已存在: " + key);
                    });
            field.setFieldKey(key);
        }
        if (StringUtils.hasText(request.label())) {
            field.setLabel(request.label().trim());
        }
        if (StringUtils.hasText(request.fieldType())) {
            field.setFieldType(FormFieldType.require(request.fieldType()).name());
        }
        if (request.required() != null) {
            field.setRequired(request.required());
        }
        if (request.sortOrder() != null) {
            field.setSortOrder(request.sortOrder());
        }
        applySourceAndOptions(field, request);
        FormField saved = fieldRepository.save(field);
        audit(operatorId, AUDIT_FIELD, "form_field", saved.getId(),
                "更新字段: definition=" + definition.getCode() + ", key=" + saved.getFieldKey());
        return toFieldVO(saved);
    }

    @Override
    @Transactional
    public void deleteField(UUID definitionId, UUID fieldId, @Nullable UUID operatorId) {
        FormDefinition definition = requireDefinition(definitionId);
        FormField field = requireField(definition.getId(), fieldId);
        long values = formValueRepository.countByFieldId(field.getId());
        if (values > 0) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "该字段已有 " + values + " 条表单数据，不能删除（可改为停用模板）");
        }
        field.setDeletedAt(LocalDateTime.now());
        fieldRepository.save(field);
        audit(operatorId, AUDIT_FIELD, "form_field", field.getId(),
                "删除字段（软删）: definition=" + definition.getCode() + ", key=" + field.getFieldKey());
    }

    @Override
    @Transactional
    public FormDefinitionVO reorderFields(UUID definitionId, List<UUID> fieldIds, @Nullable UUID operatorId) {
        FormDefinition definition = requireDefinition(definitionId);
        if (fieldIds == null || fieldIds.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "fieldIds 不能为空");
        }
        List<FormField> existing = fieldRepository.findByDefinitionIdAndDeletedAtIsNull(definition.getId());
        if (existing.size() != fieldIds.size()
                || !existing.stream().map(FormField::getId).collect(java.util.stream.Collectors.toSet())
                .containsAll(fieldIds)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "fieldIds 必须正好覆盖该模板的全部字段");
        }
        for (int i = 0; i < fieldIds.size(); i++) {
            UUID fieldId = fieldIds.get(i);
            FormField field = existing.stream().filter(item -> item.getId().equals(fieldId)).findFirst().orElseThrow();
            field.setSortOrder(i);
            fieldRepository.save(field);
        }
        audit(operatorId, AUDIT_FIELD, "form_field", definition.getId(), "字段重排序: definition=" + definition.getCode()
                + ", order=" + fieldIds.size());
        return detail(definition.getId());
    }

    // ------------------------------------------------------------------ 内部

    private FormDefinition requireDefinition(UUID id) {
        return definitionRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "表单模板不存在"));
    }

    private FormField requireField(UUID definitionId, UUID fieldId) {
        return fieldRepository.findByIdAndDefinitionIdAndDeletedAtIsNull(fieldId, definitionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "表单字段不存在"));
    }

    private FormField buildField(UUID definitionId, FormDefinitionRequests.FieldUpsert request,
                                 @Nullable FormField existing) {
        if (request == null || !StringUtils.hasText(request.fieldKey()) || !StringUtils.hasText(request.label())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "fieldKey 与 label 必填");
        }
        FormField field = existing == null ? new FormField() : existing;
        field.setDefinitionId(definitionId);
        field.setFieldKey(request.fieldKey().trim());
        field.setLabel(request.label().trim());
        field.setFieldType(resolveFieldType(request.fieldType()));
        field.setRequired(request.required() != null && request.required());
        field.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        applySourceAndOptions(field, request);
        return field;
    }

    /** field_type 校验（非法 → 400，带可选值） */
    private String resolveFieldType(@Nullable String raw) {
        try {
            return FormFieldType.require(raw).name();
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, ex.getMessage());
        }
    }

    /** source_type 白名单 + SELECT options 规则 */
    private void applySourceAndOptions(FormField field, FormDefinitionRequests.FieldUpsert request) {
        // source_type：空 = 手填；非空必须在白名单内
        if (request.sourceType() != null) {
            if (!StringUtils.hasText(request.sourceType())) {
                field.setSourceType(null);
            } else {
                try {
                    field.setSourceType(FormFieldSource.require(request.sourceType()).getCode());
                } catch (IllegalArgumentException ex) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST, ex.getMessage());
                }
            }
        }
        // options：仅 SELECT 有意义，其余类型忽略（置空）
        if (FormFieldType.SELECT.name().equals(field.getFieldType())) {
            String options = request.options() != null ? request.options()
                    : field.getOptions();
            if (!StringUtils.hasText(options)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "SELECT 类型必须提供非空的 options（JSON 数组字符串）");
            }
            field.setOptions(options.trim());
        } else if (request.options() != null || field.getOptions() != null) {
            field.setOptions(null);
        }
    }

    private void audit(@Nullable UUID operatorId, String action, String resourceType, UUID id, String detail) {
        auditLogService.log(operatorId, action, resourceType, id, detail);
    }

    private List<FormFieldVO> listFieldVOs(UUID definitionId) {
        return fieldRepository.findByDefinitionIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(definitionId)
                .stream().map(this::toFieldVO).toList();
    }

    private FormDefinitionVO toVO(FormDefinition definition, @Nullable List<FormFieldVO> fields) {
        long fieldCount = fields != null ? fields.size()
                : fieldRepository.countByDefinitionIdAndDeletedAtIsNull(definition.getId());
        return new FormDefinitionVO(definition.getId(), definition.getCode(), definition.getName(),
                definition.getDescription(), definition.isEnabled(), fieldCount,
                definition.getCreatedAt(), definition.getUpdatedAt(), fields);
    }

    private FormFieldVO toFieldVO(FormField field) {
        String sourceLabel = FormFieldSource.fromCode(field.getSourceType())
                .map(FormFieldSource::getLabel).orElse(null);
        return new FormFieldVO(field.getId(), field.getFieldKey(), field.getLabel(), field.getFieldType(),
                field.isRequired(), field.getSortOrder(), field.getSourceType(), sourceLabel, field.getOptions());
    }
}
