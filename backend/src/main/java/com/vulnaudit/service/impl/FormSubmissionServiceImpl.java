package com.vulnaudit.service.impl;

import com.vulnaudit.dto.FormFillRequests;
import com.vulnaudit.dto.FormFillResultVO;
import com.vulnaudit.dto.FormSubmissionCreatedVO;
import com.vulnaudit.dto.FormSubmissionRequests;
import com.vulnaudit.dto.FormSubmissionSummaryVO;
import com.vulnaudit.dto.FormSubmissionVO;
import com.vulnaudit.dto.FormValueVO;
import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.Customer;
import com.vulnaudit.entity.FormDefinition;
import com.vulnaudit.entity.FormField;
import com.vulnaudit.entity.FormSubmission;
import com.vulnaudit.entity.FormValue;
import com.vulnaudit.entity.Project;
import com.vulnaudit.entity.Team;
import com.vulnaudit.entity.User;
import com.vulnaudit.entity.enums.FormFieldSource;
import com.vulnaudit.entity.enums.FormFieldType;
import com.vulnaudit.entity.enums.FormSubmissionStatus;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.CustomerRepository;
import com.vulnaudit.repository.FormDefinitionRepository;
import com.vulnaudit.repository.FormFieldRepository;
import com.vulnaudit.repository.FormSubmissionRepository;
import com.vulnaudit.repository.FormValueRepository;
import com.vulnaudit.repository.ProjectRepository;
import com.vulnaudit.repository.TeamRepository;
import com.vulnaudit.repository.UserRepository;
import com.vulnaudit.security.CurrentUserProvider;
import com.vulnaudit.security.RoleAuthority;
import com.vulnaudit.service.AccessGuard;
import com.vulnaudit.service.AuditLogService;
import com.vulnaudit.service.FormSubmissionService;
import com.vulnaudit.service.FormValues;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 表单实例服务实现（t179，第 2/5 块）
 *
 * <p><b>「修改不回写业务库」是由模型保证的</b>：本类只写 `form_submission` / `form_field` / `form_value` 三张表，
 * 对 `customer/project/asset` 只有读（取快照、取名字），没有任何 setter 调用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FormSubmissionServiceImpl implements FormSubmissionService {

    private static final String SUBJECT_CUSTOMER = "CUSTOMER";
    private static final String SUBJECT_PROJECT = "PROJECT";
    private static final String SUBJECT_ASSET = "ASSET";
    private static final Set<String> SUBJECT_TYPES = Set.of(SUBJECT_CUSTOMER, SUBJECT_PROJECT, SUBJECT_ASSET);

    private final FormDefinitionRepository definitionRepository;
    private final FormFieldRepository fieldRepository;
    private final FormSubmissionRepository submissionRepository;
    private final FormValueRepository formValueRepository;
    private final CustomerRepository customerRepository;
    private final ProjectRepository projectRepository;
    private final AssetRepository assetRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final AccessGuard accessGuard;
    private final CurrentUserProvider currentUserProvider;
    private final AuditLogService auditLogService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    // ------------------------------------------------------------------ 生成实例

    @Override
    @Transactional
    public FormSubmissionCreatedVO create(FormSubmissionRequests.Create request, Authentication authentication) {
        UUID operatorId = requireOperator(authentication);
        String subjectType = request.subjectType() == null ? ""
                : request.subjectType().trim().toUpperCase(Locale.ROOT);
        if (!SUBJECT_TYPES.contains(subjectType)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "subjectType 不合法: " + request.subjectType() + "（可选：CUSTOMER/PROJECT/ASSET）");
        }
        FormDefinition definition = definitionRepository.findByIdAndDeletedAtIsNull(request.definitionId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "表单模板不存在"));
        if (!definition.isEnabled()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "表单模板已停用，不能生成表单");
        }
        Object subject = loadVisibleSubject(subjectType, request.subjectId(), authentication);
        String subjectName = subjectName(subjectType, subject);

        // 指派对象（可选）
        User assignee = null;
        if (request.assignedTo() != null) {
            assignee = requireActiveUser(request.assignedTo());
        }

        List<FormField> templateFields = fieldRepository
                .findByDefinitionIdAndSubmissionIdIsNullAndDeletedAtIsNullOrderBySortOrderAscCreatedAtAsc(
                        definition.getId());
        if (templateFields.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "该模板还没有字段，先在模板里加字段再生成表单");
        }

        String title = StringUtils.hasText(request.title()) ? request.title().trim()
                : definition.getName() + " - " + subjectName;
        FormSubmission submission = submissionRepository.save(Objects.requireNonNull(FormSubmission.builder()
                .definitionId(definition.getId())
                .title(title)
                .subjectType(subjectType)
                .subjectId(request.subjectId())
                .status(assignee == null ? FormSubmissionStatus.DRAFT.name() : FormSubmissionStatus.ASSIGNED.name())
                .createdBy(operatorId)
                .assignedTo(assignee == null ? null : assignee.getId())
                .assignedAt(assignee == null ? null : LocalDateTime.now())
                .build()));

        int filled = 0;
        int empty = 0;
        List<String> skipped = new ArrayList<>();
        for (FormField field : templateFields) {
            String sourceValue = null;
            if (StringUtils.hasText(field.getSourceType())) {
                var source = FormFieldSource.fromCode(field.getSourceType());
                if (source.isEmpty()) {
                    // 未实现的来源：跳过并 WARN，**绝不 500**
                    log.warn("表单生成跳过未实现的 sourceType: template={}, field={}, source={}",
                            definition.getCode(), field.getFieldKey(), field.getSourceType());
                    skipped.add(field.getSourceType());
                    empty++;
                } else {
                    try {
                        sourceValue = source.get().resolve(subject);
                    } catch (RuntimeException ex) {
                        log.warn("表单快照取值异常（按空处理）: source={}, err={}",
                                field.getSourceType(), ex.getMessage());
                    }
                    if (StringUtils.hasText(sourceValue)) {
                        filled++;
                    } else {
                        empty++;
                    }
                }
            }
            formValueRepository.save(Objects.requireNonNull(FormValue.builder()
                    .submissionId(submission.getId())
                    .fieldId(field.getId())
                    .sourceValue(sourceValue)
                    .updatedBy(operatorId)
                    .build()));
        }
        log.info("表单生成: id={}, template={}, subject={}:{}, 快照={}, 空={}",
                submission.getId(), definition.getCode(), subjectType, subjectName, filled, empty);
        return new FormSubmissionCreatedVO(submission.getId(), definition.getId(), submission.getStatus(),
                templateFields.size(), filled, empty, skipped);
    }

    // ------------------------------------------------------------------ 指派

    @Override
    @Transactional
    public FormSubmissionVO assign(UUID id, FormSubmissionRequests.Assign request, Authentication authentication) {
        UUID operatorId = requireOperator(authentication);
        FormSubmission submission = requireSubmission(id);
        if (!operatorId.equals(submission.getCreatedBy()) && !isAdmin(authentication)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有发起人或管理员可以指派");
        }
        String status = submission.getStatus();
        if (FormSubmissionStatus.SUBMITTED.name().equals(status)
                || FormSubmissionStatus.CONFIRMED.name().equals(status)) {
            throw new BusinessException(ErrorCode.CONFLICT, "表单已提交/已确认，不能重新指派（当前状态：" + status + "）");
        }
        User assignee = requireActiveUser(request.assigneeId());
        submission.setAssignedTo(assignee.getId());
        submission.setAssignedAt(LocalDateTime.now());
        submission.setStatus(FormSubmissionStatus.ASSIGNED.name());
        if (StringUtils.hasText(request.note())) {
            submission.setNote(request.note().trim());
        }
        submissionRepository.save(submission);
        auditLogService.log(operatorId, "ASSIGN_FORM_SUBMISSION", "form_submission", submission.getId(),
                "指派表单给 " + assignee.getUsername() + "（title=" + submission.getTitle() + "）");
        return detail(id, authentication);
    }

    // ------------------------------------------------------------------ 实例级字段

    @Override
    @Transactional
    public FormValueVO addInstanceField(UUID submissionId, FormSubmissionRequests.InstanceField request,
                                        Authentication authentication) {
        UUID operatorId = requireOperator(authentication);
        FormSubmission submission = requireSubmission(submissionId);
        if (!operatorId.equals(submission.getCreatedBy())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有发起人可以在本表单里添加字段");
        }
        String status = submission.getStatus();
        if (!FormSubmissionStatus.DRAFT.name().equals(status)
                && !FormSubmissionStatus.ASSIGNED.name().equals(status)
                && !FormSubmissionStatus.RETURNED.name().equals(status)) {
            throw new BusinessException(ErrorCode.CONFLICT, "表单已提交/已确认，不能再加字段（当前状态：" + status + "）");
        }
        FormFieldType type;
        try {
            type = FormFieldType.require(request.fieldType());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, ex.getMessage());
        }
        // 实例字段只允许手填类型：不允许指定 sourceType（请求里也没有这个字段，接口即约束）
        String options = null;
        if (type == FormFieldType.SELECT) {
            if (!StringUtils.hasText(request.options())) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "SELECT 类型必须提供非空的 options（JSON 数组字符串）");
            }
            options = request.options().trim();
        }
        int sortOrder = templateFieldCount(submission.getDefinitionId())
                + (int) fieldRepository.countBySubmissionIdAndDeletedAtIsNull(submissionId);
        long seq = fieldRepository.countBySubmissionId(submissionId) + 1;
        // 注意：uk_form_field_key 是 (definition_id, field_key) 唯一 —— 同一模板的多个实例不能都用 instance_1，
        // 所以 key 带上实例 id 短码（可读 + 不撞唯一键）
        String instanceKey = "instance_" + seq + "_" + submissionId.toString().substring(0, 8);
        FormField field = fieldRepository.save(Objects.requireNonNull(FormField.builder()
                .definitionId(submission.getDefinitionId())
                .submissionId(submissionId)
                .fieldKey(instanceKey)
                .label(request.label().trim())
                .fieldType(type.name())
                .required(request.required() != null && request.required())
                .sortOrder(sortOrder)
                .sourceType(null)
                .options(options)
                .build()));
        formValueRepository.save(Objects.requireNonNull(FormValue.builder()
                .submissionId(submissionId).fieldId(field.getId()).updatedBy(operatorId).build()));
        log.info("实例级字段新增: submission={}, key={}, type={}", submissionId, field.getFieldKey(), type);
        return toValueVO(field, formValueRepository.findBySubmissionId(submissionId).stream()
                .filter(value -> value.getFieldId().equals(field.getId())).findFirst().orElse(null));
    }

    @Override
    @Transactional
    public void deleteInstanceField(UUID submissionId, UUID fieldId, Authentication authentication) {
        UUID operatorId = requireOperator(authentication);
        FormSubmission submission = requireSubmission(submissionId);
        if (!operatorId.equals(submission.getCreatedBy())) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有发起人可以删除本表单的字段");
        }
        FormField field = fieldRepository.findByIdAndSubmissionIdAndDeletedAtIsNull(fieldId, submissionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "该字段不是本表单的实例级字段"));
        long values = formValueRepository.countByFieldId(field.getId());
        boolean filled = formValueRepository.findBySubmissionId(submissionId).stream()
                .filter(value -> value.getFieldId().equals(field.getId()))
                .anyMatch(value -> StringUtils.hasText(value.getFilledValue()));
        if (filled) {
            throw new BusinessException(ErrorCode.CONFLICT, "该字段已填写，不能删除");
        }
        if (values > 1) {
            // 防御：同一字段被多个实例引用（理论上不会，实例级字段只属于一个实例）
            throw new BusinessException(ErrorCode.CONFLICT, "该字段已有表单数据，不能删除");
        }
        field.setDeletedAt(LocalDateTime.now());
        fieldRepository.save(field);
        log.info("实例级字段删除: submission={}, key={}", submissionId, field.getFieldKey());
    }

    // ------------------------------------------------------------------ 查询

    @Override
    public PageResponse<FormSubmissionSummaryVO> findMine(Authentication authentication, @Nullable String status,
                                                          int page, int size) {
        UUID operatorId = requireOperator(authentication);
        String normalizedStatus = normalizeStatus(status);
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : Math.min(size, 100);
        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<FormSubmission> result = submissionRepository.findMine(operatorId, normalizedStatus, pageable);

        return PageResponse.<FormSubmissionSummaryVO>builder()
                .content(summaries(result.getContent()))
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .currentPage(safePage)
                .pageSize(safeSize)
                .build();
    }

    /** 列表项批量装配（mine 与 assigned 共用）：模板名/主体名/指派对象/进度，全部批量取，无 N+1 */
    private List<FormSubmissionSummaryVO> summaries(List<FormSubmission> rows) {
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<UUID, String> names = new HashMap<>();
        Map<UUID, String> definitionNames = new HashMap<>();
        Map<UUID, Integer> templateFieldCounts = new HashMap<>();
        Map<UUID, Integer> instanceFieldCounts = new HashMap<>();
        Map<UUID, Integer> filledCounts = new HashMap<>();
        List<UUID> ids = rows.stream().map(FormSubmission::getId).toList();
        List<UUID> definitionIds = rows.stream().map(FormSubmission::getDefinitionId).distinct().toList();
        List<UUID> userIds = rows.stream().map(FormSubmission::getAssignedTo)
                .filter(Objects::nonNull).distinct().toList();
        if (!userIds.isEmpty()) {
            userRepository.findAllById(userIds).forEach(user -> names.put(user.getId(), displayName(user)));
        }
        definitionRepository.findAllById(definitionIds)
                .forEach(definition -> definitionNames.put(definition.getId(), definition.getName()));
        fieldRepository.countTemplateFieldsByDefinitionIds(definitionIds)
                .forEach(row -> templateFieldCounts.put((UUID) row[0], ((Number) row[1]).intValue()));
        fieldRepository.countInstanceFieldsBySubmissionIds(ids)
                .forEach(row -> instanceFieldCounts.put((UUID) row[0], ((Number) row[1]).intValue()));
        formValueRepository.countFilledBySubmissionIds(ids)
                .forEach(row -> filledCounts.put((UUID) row[0], ((Number) row[1]).intValue()));
        Map<String, String> subjectNames = subjectNames(rows);

        return rows.stream()
                .map(row -> new FormSubmissionSummaryVO(
                        row.getId(),
                        definitionNames.get(row.getDefinitionId()),
                        row.getTitle(),
                        subjectTypeOf(row),
                        subjectIdOf(row),
                        subjectNames.get(row.getId().toString()),
                        row.getStatus(),
                        row.getAssignedTo(),
                        row.getAssignedTo() == null ? null : names.get(row.getAssignedTo()),
                        filledCounts.getOrDefault(row.getId(), 0),
                        // 模板字段数按 definitionId，实例字段数按 submissionId —— 两张表分别统计再相加
                        templateFieldCounts.getOrDefault(row.getDefinitionId(), 0)
                                + instanceFieldCounts.getOrDefault(row.getId(), 0),
                        row.getNote(),
                        row.getCreatedAt()))
                .toList();
    }
    @Override
    public FormSubmissionVO detail(UUID id, Authentication authentication) {
        FormSubmission submission = requireReadable(id, authentication);
        List<FormField> fields = fieldRepository.findFieldsForSubmission(id, submission.getDefinitionId());
        List<FormValue> values = formValueRepository.findBySubmissionId(id);
        Map<UUID, FormValue> byField = new HashMap<>();
        values.forEach(value -> byField.put(value.getFieldId(), value));
        List<FormValueVO> fieldVOs = fields.stream()
                .map(field -> toValueVO(field, byField.get(field.getId())))
                .toList();
        int filled = (int) fieldVOs.stream().filter(vo -> StringUtils.hasText(vo.filledValue())).count();

        FormDefinition definition = definitionRepository.findById(submission.getDefinitionId()).orElse(null);
        User creator = submission.getCreatedBy() == null ? null
                : userRepository.findById(submission.getCreatedBy()).orElse(null);
        User assignee = submission.getAssignedTo() == null ? null
                : userRepository.findById(submission.getAssignedTo()).orElse(null);
        return new FormSubmissionVO(
                submission.getId(),
                submission.getDefinitionId(),
                definition == null ? null : definition.getCode(),
                definition == null ? null : definition.getName(),
                submission.getTitle(),
                subjectTypeOf(submission),
                subjectIdOf(submission),
                subjectNames(List.of(submission)).get(submission.getId().toString()),
                submission.getStatus(),
                submission.getCreatedBy(),
                creator == null ? null : displayName(creator),
                submission.getAssignedTo(),
                assignee == null ? null : displayName(assignee),
                submission.getConfirmedBy(),
                submission.getAssignedAt(),
                submission.getSubmittedAt(),
                submission.getConfirmedAt(),
                submission.getNote(),
                submission.getCreatedAt(),
                submission.getUpdatedAt(),
                filled,
                fieldVOs.size(),
                fieldVOs);
    }

    // ------------------------------------------------------------------ 内部：主体

    /** 载入业务对象并做可见性校验（ASSET 走 canReadAsset；CUSTOMER/PROJECT 沿用列表读口径） */
    private Object loadVisibleSubject(String subjectType, UUID subjectId, Authentication authentication) {
        return switch (subjectType) {
            case SUBJECT_CUSTOMER -> customerRepository.findById(subjectId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "客户不存在或已删除"));
            case SUBJECT_PROJECT -> projectRepository.findById(subjectId)
                    .filter(project -> project.getDeletedAt() == null)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "项目不存在或已删除"));
            case SUBJECT_ASSET -> {
                Asset asset = assetRepository.findById(subjectId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "资产不存在或已删除"));
                if (!accessGuard.canReadAsset(asset.getId(), authentication)) {
                    throw new BusinessException(ErrorCode.FORBIDDEN, "无权对该资产生成表单");
                }
                yield asset;
            }
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST, "subjectType 不合法");
        };
    }

    private String subjectName(String subjectType, Object subject) {
        return switch (subjectType) {
            case SUBJECT_CUSTOMER -> ((Customer) subject).getName();
            case SUBJECT_PROJECT -> ((Project) subject).getName();
            case SUBJECT_ASSET -> ((Asset) subject).getName();
            default -> null;
        };
    }

    @Nullable
    private String subjectTypeOf(FormSubmission submission) {
        return submission.getSubjectType();
    }

    @Nullable
    private UUID subjectIdOf(FormSubmission submission) {
        return submission.getSubjectId();
    }

    /**
     * 列表页/详情的主体名：按 `(subjectType, subjectId)` 分组批量查（每组一次 `findAllById`），
     * 不做逐条查询。主体被删/已软删时名字为 null（表单本身仍可看）。
     */
    private Map<String, String> subjectNames(List<FormSubmission> rows) {
        Map<String, String> result = new HashMap<>();
        if (rows.isEmpty()) {
            return result;
        }
        List<UUID> customerIds = idsOf(rows, SUBJECT_CUSTOMER);
        List<UUID> projectIds = idsOf(rows, SUBJECT_PROJECT);
        List<UUID> assetIds = idsOf(rows, SUBJECT_ASSET);
        Map<UUID, String> names = new HashMap<>();
        if (!customerIds.isEmpty()) {
            customerRepository.findAllById(customerIds).forEach(item -> names.put(item.getId(), item.getName()));
        }
        if (!projectIds.isEmpty()) {
            projectRepository.findAllById(projectIds).forEach(item -> names.put(item.getId(), item.getName()));
        }
        if (!assetIds.isEmpty()) {
            assetRepository.findAllById(assetIds).forEach(item -> names.put(item.getId(), item.getName()));
        }
        for (FormSubmission row : rows) {
            result.put(row.getId().toString(), row.getSubjectId() == null ? null : names.get(row.getSubjectId()));
        }
        return result;
    }

    private List<UUID> idsOf(List<FormSubmission> rows, String subjectType) {
        return rows.stream()
                .filter(row -> subjectType.equals(row.getSubjectType()))
                .map(FormSubmission::getSubjectId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }


    // ------------------------------------------------------------------ t180 填写 / 覆盖 / 确认退回

    @Override
    @Transactional
    public FormFillResultVO fillValues(UUID id, FormFillRequests.FillValues request, Authentication authentication) {
        UUID operatorId = requireOperator(authentication);
        FormSubmission submission = requireSubmission(id);
        requireFillable(submission, operatorId);

        Map<UUID, FormField> fields = fieldsOf(submission);
        Map<UUID, FormValue> valueByField = new HashMap<>();
        formValueRepository.findBySubmissionId(submission.getId())
                .forEach(value -> valueByField.put(value.getFieldId(), value));

        int updated = 0;
        Map<String, String> effective = new LinkedHashMap<>();
        for (FormFillRequests.ValueItem item : request.values()) {
            FormField field = fields.get(item.fieldId());
            if (field == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "字段不属于该表单实例: " + item.fieldId());
            }
            // 注意：请求里没有 sourceValue —— 快照不可写（带了也会被 Jackson 忽略）
            String normalized = item.filledValue() == null || item.filledValue().isBlank()
                    ? null : item.filledValue().trim();
            validateFieldValue(field, normalized);
            FormValue value = valueByField.computeIfAbsent(field.getId(), key ->
                    formValueRepository.save(Objects.requireNonNull(FormValue.builder()
                            .submissionId(submission.getId()).fieldId(field.getId()).build())));
            value.setFilledValue(normalized);
            value.setUpdatedBy(operatorId);
            formValueRepository.save(value);
            effective.put(field.getFieldKey(), FormValues.effectiveValue(value));
            updated++;
        }
        log.info("表单填写: submission={}, by={}, fields={}, status={}",
                id, operatorId, updated, submission.getStatus());
        return new FormFillResultVO(id, submission.getStatus(), updated, List.of(), effective);
    }

    @Override
    @Transactional
    public FormSubmissionVO submit(UUID id, Authentication authentication) {
        UUID operatorId = requireOperator(authentication);
        FormSubmission submission = requireSubmission(id);
        requireFillable(submission, operatorId);
        String status = submission.getStatus();
        if (FormSubmissionStatus.SUBMITTED.name().equals(status)
                || FormSubmissionStatus.CONFIRMED.name().equals(status)) {
            throw new BusinessException(ErrorCode.CONFLICT, "表单已提交/已确认，不能重复提交（当前状态：" + status + "）");
        }

        // 必填校验：effective = override ?? filled ?? source
        List<String> missing = new ArrayList<>();
        Map<UUID, FormField> fields = fieldsOf(submission);
        for (FormValue value : formValueRepository.findBySubmissionId(submission.getId())) {
            FormField field = fields.get(value.getFieldId());
            if (field != null && field.isRequired() && !FormValues.hasText(FormValues.effectiveValue(value))) {
                missing.add(field.getLabel());
            }
        }
        if (!missing.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "以下必填字段尚未填写: " + String.join("、", missing));
        }

        submission.setStatus(FormSubmissionStatus.SUBMITTED.name());
        submission.setSubmittedAt(LocalDateTime.now());
        submissionRepository.save(submission);
        auditLogService.log(operatorId, "SUBMIT_FORM_SUBMISSION", "form_submission", submission.getId(),
                "提交表单: title=" + submission.getTitle());
        return detail(id, authentication);
    }

    @Override
    @Transactional
    public FormFillResultVO setOverrides(UUID id, FormFillRequests.OverrideValues request,
                                         Authentication authentication) {
        UUID operatorId = requireOperator(authentication);
        FormSubmission submission = requireSubmission(id);
        requireOverrideAuthority(submission, operatorId, authentication);
        String status = submission.getStatus();
        if (FormSubmissionStatus.DRAFT.name().equals(status) || FormSubmissionStatus.ASSIGNED.name().equals(status)
                || FormSubmissionStatus.RETURNED.name().equals(status)) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "表单尚未提交（当前状态：" + status + "），请直接修改填写值而不是设置覆盖");
        }

        Map<UUID, FormField> fields = fieldsOf(submission);
        Map<UUID, FormValue> valueByField = new HashMap<>();
        formValueRepository.findBySubmissionId(submission.getId())
                .forEach(value -> valueByField.put(value.getFieldId(), value));

        int updated = 0;
        Map<String, String> effective = new LinkedHashMap<>();
        for (FormFillRequests.OverrideItem item : request.values()) {
            FormField field = fields.get(item.fieldId());
            if (field == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "字段不属于该表单实例: " + item.fieldId());
            }
            FormValue value = valueByField.get(field.getId());
            if (value == null) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "该字段没有表单值记录: " + item.fieldId());
            }
            // null = 清除覆盖（回落到 filledValue / sourceValue）；**不动 filledValue、不动业务库**
            value.setOverrideValue(item.overrideValue() == null ? null : item.overrideValue().trim());
            value.setUpdatedBy(operatorId);
            formValueRepository.save(value);
            effective.put(field.getFieldKey(), FormValues.effectiveValue(value));
            updated++;
        }
        auditLogService.log(operatorId, "OVERRIDE_FORM_VALUES", "form_submission", submission.getId(),
                "设置覆盖值: fields=" + updated + "（只影响导出，不改填写值与业务库）");
        return new FormFillResultVO(id, submission.getStatus(), updated, List.of(), effective);
    }

    @Override
    @Transactional
    public FormSubmissionVO confirm(UUID id, Authentication authentication) {
        UUID operatorId = requireOperator(authentication);
        FormSubmission submission = requireSubmission(id);
        requireConfirmAuthority(submission, operatorId, authentication);
        if (!FormSubmissionStatus.SUBMITTED.name().equals(submission.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "只有已提交（SUBMITTED）的表单可以确认，当前状态：" + submission.getStatus());
        }
        submission.setStatus(FormSubmissionStatus.CONFIRMED.name());
        submission.setConfirmedBy(operatorId);
        submission.setConfirmedAt(LocalDateTime.now());
        submissionRepository.save(submission);
        auditLogService.log(operatorId, "CONFIRM_FORM_SUBMISSION", "form_submission", submission.getId(),
                "确认表单: title=" + submission.getTitle());
        return detail(id, authentication);
    }

    @Override
    @Transactional
    public FormSubmissionVO returnSubmission(UUID id, FormFillRequests.ReturnRequest request,
                                             Authentication authentication) {
        UUID operatorId = requireOperator(authentication);
        FormSubmission submission = requireSubmission(id);
        requireConfirmAuthority(submission, operatorId, authentication);
        if (!FormSubmissionStatus.SUBMITTED.name().equals(submission.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT,
                    "只有已提交（SUBMITTED）的表单可以退回，当前状态：" + submission.getStatus());
        }
        if (request == null || !StringUtils.hasText(request.note())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "退回必须填写说明（note）");
        }
        String note = request.note().trim();
        if (note.length() > 2000) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "退回说明不能超过 2000 个字符");
        }
        submission.setStatus(FormSubmissionStatus.RETURNED.name());
        submission.setNote(note);
        submissionRepository.save(submission);
        auditLogService.log(operatorId, "RETURN_FORM_SUBMISSION", "form_submission", submission.getId(),
                "退回表单: note=" + note);
        return detail(id, authentication);
    }

    @Override
    @Transactional
    public FormSubmissionVO setConfirmer(UUID id, FormFillRequests.SetConfirmer request,
                                         Authentication authentication) {
        UUID operatorId = requireOperator(authentication);
        FormSubmission submission = requireSubmission(id);
        if (!operatorId.equals(submission.getCreatedBy()) && !isAdmin(authentication)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有发起人或管理员可以设置确认人");
        }
        if (FormSubmissionStatus.CONFIRMED.name().equals(submission.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "表单已确认，不能再改确认人");
        }
        User confirmer = userRepository.findById(request.confirmerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "确认人不存在"));
        if (confirmer.getStatus() != null && !"active".equalsIgnoreCase(confirmer.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "确认人不可用（状态：" + confirmer.getStatus() + "）");
        }
        submission.setConfirmedBy(confirmer.getId());
        submissionRepository.save(submission);
        log.info("设置表单确认人: submission={}, confirmer={}", id, confirmer.getUsername());
        return detail(id, authentication);
    }

    @Override
    public PageResponse<FormSubmissionSummaryVO> findAssigned(Authentication authentication, @Nullable String status,
                                                              int page, int size) {
        UUID operatorId = requireOperator(authentication);
        String normalizedStatus = normalizeStatus(status);
        int safePage = Math.max(page, 0);
        int safeSize = size <= 0 ? 20 : Math.min(size, 100);
        PageRequest pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<FormSubmission> result = submissionRepository.findAssigned(operatorId, normalizedStatus, pageable);
        return new PageResponse<>(summaries(result.getContent()), result.getTotalElements(),
                result.getTotalPages(), safePage, safeSize);
    }

    // ------------------------------------------------------------------ 内部：填表权限与值校验

    /** 可填写者：ASSIGNED 时必须是被指派人；DRAFT/RETURNED 时发起人或被指派人（ADMIN 不代填） */
    private void requireFillable(FormSubmission submission, UUID operatorId) {
        String status = submission.getStatus();
        if (FormSubmissionStatus.CONFIRMED.name().equals(status)) {
            throw new BusinessException(ErrorCode.CONFLICT, "表单已确认，不能再修改");
        }
        if (FormSubmissionStatus.SUBMITTED.name().equals(status)) {
            throw new BusinessException(ErrorCode.CONFLICT, "表单已提交，等待确认；如需修改请让确认人退回");
        }
        boolean isAssignee = operatorId.equals(submission.getAssignedTo());
        boolean isCreator = operatorId.equals(submission.getCreatedBy());
        if (FormSubmissionStatus.ASSIGNED.name().equals(status)) {
            if (!isAssignee) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "只有被指派人可以填写该表单");
            }
            return;
        }
        if (isCreator || isAssignee) {
            return;
        }
        throw new BusinessException(ErrorCode.FORBIDDEN, "无权填写该表单");
    }

    private void requireOverrideAuthority(FormSubmission submission, UUID operatorId, Authentication authentication) {
        boolean isCreator = operatorId.equals(submission.getCreatedBy());
        boolean isConfirmer = operatorId.equals(submission.getConfirmedBy());
        if (!isCreator && !isConfirmer && !isAdmin(authentication)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有发起人、确认人或管理员可以设置覆盖值");
        }
    }

    private void requireConfirmAuthority(FormSubmission submission, UUID operatorId, Authentication authentication) {
        if (!operatorId.equals(submission.getConfirmedBy()) && !isAdmin(authentication)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有确认人或管理员可以确认/退回该表单");
        }
    }

    /** 实例的全部字段（模板字段 + 实例字段），key = fieldId */
    private Map<UUID, FormField> fieldsOf(FormSubmission submission) {
        Map<UUID, FormField> map = new HashMap<>();
        fieldRepository.findFieldsForSubmission(submission.getId(), submission.getDefinitionId())
                .forEach(field -> map.put(field.getId(), field));
        return map;
    }

    /** 类型校验（空值放行，必填在 submit 时校验） */
    private void validateFieldValue(FormField field, @Nullable String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        FormFieldType type;
        try {
            type = FormFieldType.require(field.getFieldType());
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "字段类型不合法: " + field.getFieldType());
        }
        switch (type) {
            case NUMBER -> {
                try {
                    new java.math.BigDecimal(value);
                } catch (NumberFormatException ex) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST,
                            "字段「" + field.getLabel() + "」必须是数字: " + value);
                }
            }
            case DATE -> {
                try {
                    java.time.LocalDate.parse(value);
                } catch (java.time.format.DateTimeParseException ex) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST,
                            "字段「" + field.getLabel() + "」必须是 ISO 日期（yyyy-MM-dd）: " + value);
                }
            }
            case SELECT -> {
                if (!optionsOf(field).contains(value)) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST,
                            "字段「" + field.getLabel() + "」的值不在可选范围内: " + value);
                }
            }
            case CHECKBOX -> {
                String lower = value.toLowerCase(Locale.ROOT);
                if (!"true".equals(lower) && !"false".equals(lower)) {
                    throw new BusinessException(ErrorCode.BAD_REQUEST,
                            "字段「" + field.getLabel() + "」只能是 true/false: " + value);
                }
            }
            default -> {
                // TEXT/TEXTAREA/FILE 不做格式限制
            }
        }
    }

    /** SELECT 的 options 是 JSON 数组字符串（模板侧已保证非空），这里解析出选项文本 */
    private List<String> optionsOf(FormField field) {
        if (!StringUtils.hasText(field.getOptions())) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        try {
            var node = objectMapper.readTree(field.getOptions());
            if (node.isArray()) {
                node.forEach(item -> result.add(item.asText()));
            }
        } catch (Exception ex) {
            log.warn("字段 options 不是合法 JSON，按空选项处理: field={}", field.getFieldKey());
        }
        return result;
    }
    // ------------------------------------------------------------------ 内部：通用

    private UUID requireOperator(Authentication authentication) {
        UUID userId = currentUserProvider.currentUserId().orElse(null);
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return userId;
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> RoleAuthority.ADMIN.equals(authority.getAuthority()));
    }

    private FormSubmission requireSubmission(UUID id) {
        return submissionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "表单不存在"));
    }

    /** 发起人 / 被指派人 / 确认人 / ADMIN 可读；其他人 404（不泄露存在性） */
    private FormSubmission requireReadable(UUID id, Authentication authentication) {
        FormSubmission submission = requireSubmission(id);
        UUID operatorId = requireOperator(authentication);
        boolean involved = operatorId.equals(submission.getCreatedBy())
                || operatorId.equals(submission.getAssignedTo())
                || operatorId.equals(submission.getConfirmedBy());
        if (!involved && !isAdmin(authentication)) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "表单不存在");
        }
        return submission;
    }

    private User requireActiveUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "被指派人不存在"));
        if (user.getStatus() != null && !"active".equalsIgnoreCase(user.getStatus())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "被指派人不可用（状态：" + user.getStatus() + "）");
        }
        return user;
    }

    private String normalizeStatus(@Nullable String status) {
        if (!StringUtils.hasText(status) || "all".equalsIgnoreCase(status.trim())) {
            return null;
        }
        try {
            return FormSubmissionStatus.require(status).name();
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, ex.getMessage());
        }
    }

    private int templateFieldCount(UUID definitionId) {
        return (int) fieldRepository.countByDefinitionIdAndSubmissionIdIsNullAndDeletedAtIsNull(definitionId);
    }

    private String displayName(User user) {
        return StringUtils.hasText(user.getFullName()) ? user.getFullName() : user.getUsername();
    }

    private FormValueVO toValueVO(FormField field, @Nullable FormValue value) {
        String sourceLabel = FormFieldSource.fromCode(field.getSourceType())
                .map(FormFieldSource::getLabel).orElse(null);
        return new FormValueVO(
                field.getId(),
                field.getFieldKey(),
                field.getLabel(),
                field.getFieldType(),
                field.isRequired(),
                field.getSortOrder(),
                field.getSourceType(),
                sourceLabel,
                field.getOptions(),
                field.getSubmissionId() != null,
                value == null ? null : value.getSourceValue(),
                value == null ? null : value.getFilledValue(),
                value == null ? null : value.getOverrideValue(),
                // t180：生效值 = override ?? filled ?? source（导出/展示统一口径）
                FormValues.effectiveValue(value));
    }


}
