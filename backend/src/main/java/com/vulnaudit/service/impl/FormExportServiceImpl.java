package com.vulnaudit.service.impl;

import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.Customer;
import com.vulnaudit.entity.FormDefinition;
import com.vulnaudit.entity.FormField;
import com.vulnaudit.entity.FormSubmission;
import com.vulnaudit.entity.FormValue;
import com.vulnaudit.entity.Project;
import com.vulnaudit.entity.User;
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
import com.vulnaudit.repository.UserRepository;
import com.vulnaudit.security.CurrentUserProvider;
import com.vulnaudit.security.RoleAuthority;
import com.vulnaudit.service.AuditLogService;
import com.vulnaudit.service.FormExportService;
import com.vulnaudit.service.FormValues;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 填表导出实现（t181）
 *
 * <p>列结构：11 个固定前置列 + 动态字段列（模板字段按 sort_order，实例级字段按实例追加）。
 * 动态列**按 fieldId 归并**：不同实例的同名字段只占一列，列名取**首次出现**的 label。
 *
 * <p>CSV 写 UTF-8 BOM（Excel 打开中文不乱码）；XLSX 用 SXSSF 流式写（5000 行内存可控）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FormExportServiceImpl implements FormExportService {

    private static final String FORMAT_CSV = "csv";
    private static final String FORMAT_XLSX = "xlsx";
    private static final String CSV_CONTENT_TYPE = "text/csv; charset=UTF-8";
    private static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    /** 固定前置列（顺序即导出顺序） */
    private static final List<String> FIXED_COLUMNS = List.of(
            "表单ID", "模板名称", "主体类型", "主体名称", "状态", "发起人", "被指派人", "确认人",
            "创建时间", "提交时间", "确认时间");

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final FormSubmissionRepository submissionRepository;
    private final FormFieldRepository fieldRepository;
    private final FormValueRepository formValueRepository;
    private final FormDefinitionRepository definitionRepository;
    private final UserRepository userRepository;
    private final CustomerRepository customerRepository;
    private final ProjectRepository projectRepository;
    private final AssetRepository assetRepository;
    private final CurrentUserProvider currentUserProvider;
    private final AuditLogService auditLogService;

    /** 单次导出行数上限（默认 5000；测试可覆盖以便用小数据验证 400） */
    @Value("${app.forms.export.max-rows:5000}")
    private int maxRows = 5000;

    // ------------------------------------------------------------------ 入口

    @Override
    public ExportFile export(@Nullable UUID definitionId, @Nullable String status, @Nullable LocalDate from,
                             @Nullable LocalDate to, String format, Authentication authentication) {
        UUID operatorId = requireOperator(authentication);
        String normalizedFormat = normalizeFormat(format);
        String normalizedStatus = normalizeStatus(status);
        boolean manager = isManager(authentication);

        if (from != null && to != null && from.isAfter(to)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "from 不能晚于 to");
        }
        LocalDateTime fromTime = from == null ? null : from.atStartOfDay();
        LocalDateTime toTime = to == null ? null : to.atTime(LocalTime.of(23, 59, 59, 999_000_000));

        // 多取 1 行用于判断超限（避免先 count 再查两次）
        Page<FormSubmission> page = submissionRepository.searchForExport(definitionId, normalizedStatus,
                fromTime, toTime, !manager, operatorId, PageRequest.of(0, maxRows + 1));
        List<FormSubmission> rows = page.getContent();
        if (rows.size() > maxRows) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "导出结果超过单次上限 " + maxRows + " 行，请按模板/状态/时间范围缩小后重试");
        }
        if (rows.isEmpty()) {
            // 空结果也导出（只含表头），前端不用特判
            log.info("表单导出：无数据（definitionId={}, status={}, from={}, to={}）",
                    definitionId, normalizedStatus, from, to);
        }

        ExportFile file = build(rows, normalizedFormat, "表单导出_" + LocalDateTime.now().format(FILE_STAMP));
        auditLogService.log(operatorId, "EXPORT_FORM_SUBMISSIONS", "form_submission", definitionId,
                "导出表单: format=" + normalizedFormat + ", rows=" + file.rowCount()
                        + ", definitionId=" + definitionId + ", status=" + normalizedStatus
                        + ", from=" + from + ", to=" + to + ", scope=" + (manager ? "ALL" : "MINE"));
        return file;
    }

    @Override
    public ExportFile exportOne(UUID id, String format, Authentication authentication) {
        UUID operatorId = requireOperator(authentication);
        String normalizedFormat = normalizeFormat(format);
        FormSubmission submission = submissionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "表单不存在"));
        boolean involved = operatorId.equals(submission.getCreatedBy())
                || operatorId.equals(submission.getAssignedTo())
                || operatorId.equals(submission.getConfirmedBy());
        if (!involved && !isAdmin(authentication)) {
            // 与详情一致：不泄露存在性
            throw new BusinessException(ErrorCode.NOT_FOUND, "表单不存在");
        }
        FormDefinition definition = definitionRepository.findById(submission.getDefinitionId()).orElse(null);
        String base = definition == null ? "表单" : "表单_" + definition.getName();
        ExportFile file = build(List.of(submission), normalizedFormat, base);
        auditLogService.log(operatorId, "EXPORT_FORM_SUBMISSIONS", "form_submission", id,
                "导出单条表单: format=" + normalizedFormat + ", rows=1");
        return file;
    }

    // ------------------------------------------------------------------ 数据装配

    /** 列定义（fieldId 归并后的动态列） */
    private record Column(UUID fieldId, String label, String fieldType) {
    }

    private ExportFile build(List<FormSubmission> rows, String format, String fileBase) {
        List<UUID> ids = rows.stream().map(FormSubmission::getId).toList();
        List<UUID> definitionIds = rows.stream().map(FormSubmission::getDefinitionId).distinct().toList();
        Map<UUID, FormDefinition> definitions = new HashMap<>();
        definitionRepository.findAllById(definitionIds).forEach(item -> definitions.put(item.getId(), item));

        // 动态列：模板字段 + 实例级字段，按 fieldId 归并（首次出现的 label 为准）
        Map<UUID, Column> columns = new LinkedHashMap<>();
        List<FormField> templateFields = definitionIds.isEmpty() ? List.of()
                : fieldRepository.findByDefinitionIdInAndSubmissionIdIsNullAndDeletedAtIsNullOrderBySortOrderAsc(
                        definitionIds);
        // 模板字段按 definitionId 分组，行内顺序按 sort_order
        Map<UUID, List<FormField>> templateByDefinition = new HashMap<>();
        templateFields.stream()
                .sorted(java.util.Comparator.comparingInt(FormField::getSortOrder))
                .forEach(field -> templateByDefinition
                        .computeIfAbsent(field.getDefinitionId(), key -> new ArrayList<>()).add(field));
        // 先按行序铺模板字段（不同模板的字段都进来），再铺实例级字段
        for (FormSubmission row : rows) {
            templateByDefinition.getOrDefault(row.getDefinitionId(), List.of())
                    .forEach(field -> columns.putIfAbsent(field.getId(), toColumn(field)));
        }
        List<FormField> instanceFields = ids.isEmpty() ? List.of()
                : fieldRepository.findBySubmissionIdInAndDeletedAtIsNullOrderBySortOrderAsc(ids);
        instanceFields.stream()
                .sorted(java.util.Comparator.comparingInt(FormField::getSortOrder))
                .forEach(field -> columns.putIfAbsent(field.getId(), toColumn(field)));
        List<Column> dynamicColumns = new ArrayList<>(columns.values());

        // 行数据
        Map<UUID, FormSubmission> byId = new HashMap<>();
        rows.forEach(row -> byId.put(row.getId(), row));
        List<String[]> matrix = cellMatrix(rows, definitions, dynamicColumns);

        List<String> headers = new ArrayList<>(FIXED_COLUMNS);
        // 动态列按 fieldId 归并（不同实例的实例级字段 fieldId 不同 → 各自一列）；
        // 列名撞车时补 ` (2)`、` (3)`，避免 Excel 里出现两个同名表头无法定位
        Map<String, Integer> labelUses = new HashMap<>();
        for (Column column : dynamicColumns) {
            String label = column.label() == null ? "" : column.label();
            int used = labelUses.merge(label, 1, Integer::sum);
            headers.add(used == 1 ? label : label + " (" + used + ")");
        }

        byte[] content = FORMAT_CSV.equals(format)
                ? toCsv(headers, matrix)
                : toXlsx(headers, matrix, dynamicColumns);
        String fileName = fileBase + "." + format;
        String contentType = FORMAT_CSV.equals(format) ? CSV_CONTENT_TYPE : XLSX_CONTENT_TYPE;
        log.info("表单导出生成: format={}, rows={}, columns={}", format, rows.size(), headers.size());
        return new ExportFile(content, fileName, contentType, rows.size());
    }

    private Column toColumn(FormField field) {
        return new Column(field.getId(), field.getLabel(), field.getFieldType());
    }

    /** 行 × 列 的字符串矩阵（值 = effectiveValue；空值 → 空串） */
    private List<String[]> cellMatrix(List<FormSubmission> rows, Map<UUID, FormDefinition> definitions,
                                      List<Column> dynamicColumns) {
        List<UUID> ids = rows.stream().map(FormSubmission::getId).toList();
        Map<UUID, Map<UUID, String>> valuesBySubmission = new HashMap<>();
        if (!ids.isEmpty()) {
            for (FormValue value : formValueRepository.findBySubmissionIdIn(ids)) {
                valuesBySubmission.computeIfAbsent(value.getSubmissionId(), key -> new HashMap<>())
                        .put(value.getFieldId(), FormValues.effectiveValue(value));
            }
        }
        Map<UUID, String> userNames = userNames(rows);
        Map<String, String> subjectNames = subjectNames(rows);

        List<String[]> matrix = new ArrayList<>(rows.size());
        for (FormSubmission row : rows) {
            FormDefinition definition = definitions.get(row.getDefinitionId());
            String[] line = new String[FIXED_COLUMNS.size() + dynamicColumns.size()];
            int index = 0;
            line[index++] = row.getId().toString();
            line[index++] = definition == null ? "" : nullToEmpty(definition.getName());
            line[index++] = nullToEmpty(row.getSubjectType());
            line[index++] = nullToEmpty(subjectNames.get(row.getId().toString()));
            line[index++] = nullToEmpty(row.getStatus());
            line[index++] = nullToEmpty(row.getCreatedBy() == null ? null : userNames.get(row.getCreatedBy()));
            line[index++] = nullToEmpty(row.getAssignedTo() == null ? null : userNames.get(row.getAssignedTo()));
            line[index++] = nullToEmpty(row.getConfirmedBy() == null ? null : userNames.get(row.getConfirmedBy()));
            line[index++] = row.getCreatedAt() == null ? "" : row.getCreatedAt().format(DATE_TIME);
            line[index++] = row.getSubmittedAt() == null ? "" : row.getSubmittedAt().format(DATE_TIME);
            line[index++] = row.getConfirmedAt() == null ? "" : row.getConfirmedAt().format(DATE_TIME);

            Map<UUID, String> values = valuesBySubmission.getOrDefault(row.getId(), Map.of());
            for (Column column : dynamicColumns) {
                line[index++] = nullToEmpty(values.get(column.fieldId()));
            }
            matrix.add(line);
        }
        return matrix;
    }

    private Map<UUID, String> userNames(List<FormSubmission> rows) {
        List<UUID> ids = new ArrayList<>();
        rows.forEach(row -> {
            if (row.getCreatedBy() != null) {
                ids.add(row.getCreatedBy());
            }
            if (row.getAssignedTo() != null) {
                ids.add(row.getAssignedTo());
            }
            if (row.getConfirmedBy() != null) {
                ids.add(row.getConfirmedBy());
            }
        });
        Map<UUID, String> names = new HashMap<>();
        if (ids.isEmpty()) {
            return names;
        }
        userRepository.findAllById(ids.stream().distinct().toList()).forEach(user -> names.put(user.getId(),
                StringUtils.hasText(user.getFullName()) ? user.getFullName() : user.getUsername()));
        return names;
    }

    /** key = submissionId（字符串），与取值侧保持一致 */
    private Map<String, String> subjectNames(List<FormSubmission> rows) {
        Map<String, String> names = new HashMap<>();
        List<UUID> customerIds = subjectIds(rows, "CUSTOMER");
        List<UUID> projectIds = subjectIds(rows, "PROJECT");
        List<UUID> assetIds = subjectIds(rows, "ASSET");
        Map<UUID, String> byId = new HashMap<>();
        if (!customerIds.isEmpty()) {
            customerRepository.findAllById(customerIds).forEach(item -> byId.put(item.getId(), item.getName()));
        }
        if (!projectIds.isEmpty()) {
            projectRepository.findAllById(projectIds).forEach(item -> byId.put(item.getId(), item.getName()));
        }
        if (!assetIds.isEmpty()) {
            assetRepository.findAllById(assetIds).forEach(item -> byId.put(item.getId(), item.getName()));
        }
        rows.forEach(row -> names.put(row.getId().toString(),
                row.getSubjectId() == null ? null : byId.get(row.getSubjectId())));
        return names;
    }

    private List<UUID> subjectIds(List<FormSubmission> rows, String type) {
        return rows.stream().filter(row -> type.equals(row.getSubjectType()))
                .map(FormSubmission::getSubjectId).filter(Objects::nonNull).distinct().toList();
    }

    // ------------------------------------------------------------------ CSV

    /** CSV：UTF-8 BOM + 逗号分隔 + 必要转义（含逗号/引号/换行时用双引号包裹） */
    private byte[] toCsv(List<String> headers, List<String[]> matrix) {
        StringBuilder builder = new StringBuilder(8192);
        builder.append('\uFEFF'); // UTF-8 BOM：Excel 打开中文不乱码
        appendCsvLine(builder, headers.toArray(new String[0]));
        for (String[] line : matrix) {
            appendCsvLine(builder, line);
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void appendCsvLine(StringBuilder builder, String[] cells) {
        for (int i = 0; i < cells.length; i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(escapeCsv(cells[i]));
        }
        builder.append("\r\n");
    }

    private String escapeCsv(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        boolean needQuote = value.indexOf(',') >= 0 || value.indexOf('"') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0;
        String escaped = value.replace("\"", "\"\"");
        return needQuote ? "\"" + escaped + "\"" : escaped;
    }

    // ------------------------------------------------------------------ XLSX

    /**
     * XLSX：SXSSF 流式写（内存窗口 100 行），表头加粗。
     *
     * <p>数字类型字段（`fieldType=NUMBER` 且能解析）写成**数值单元格**，其余写字符串；
     * 固定列里的时间是字符串（避免时区/格式歧义）。
     */
    private byte[] toXlsx(List<String> headers, List<String[]> matrix, List<Column> dynamicColumns) {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("表单数据");
            CellStyle headerStyle = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            headerStyle.setFont(bold);
            // 让 Excel 直接识别为文本，避免长 ID 变成科学计数法
            CellStyle textStyle = workbook.createCellStyle();
            textStyle.setDataFormat(workbook.createDataFormat().getFormat("@"));

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }
            for (int r = 0; r < matrix.size(); r++) {
                String[] line = matrix.get(r);
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < line.length; c++) {
                    Cell cell = row.createCell(c);
                    String value = line[c];
                    if (value == null || value.isEmpty()) {
                        continue; // 空单元格
                    }
                    boolean numericColumn = c >= FIXED_COLUMNS.size()
                            && isNumericColumn(dynamicColumns.get(c - FIXED_COLUMNS.size()));
                    if (numericColumn) {
                        try {
                            cell.setCellValue(new BigDecimal(value).doubleValue());
                            continue;
                        } catch (NumberFormatException ignored) {
                            // 落到字符串分支
                        }
                    }
                    cell.setCellValue(value);
                    if (c < FIXED_COLUMNS.size()) {
                        cell.setCellStyle(textStyle);
                    }
                }
            }
            workbook.write(out);
            workbook.dispose();
            return out.toByteArray();
        } catch (IOException ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "生成 XLSX 失败: " + ex.getMessage());
        }
    }

    private boolean isNumericColumn(Column column) {
        try {
            return FormFieldType.require(column.fieldType()) == FormFieldType.NUMBER;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    // ------------------------------------------------------------------ 内部

    private String normalizeFormat(@Nullable String format) {
        String normalized = format == null ? "" : format.trim().toLowerCase(Locale.ROOT);
        if (!FORMAT_CSV.equals(normalized) && !FORMAT_XLSX.equals(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "format 不合法: " + format + "（可选：csv/xlsx）");
        }
        return normalized;
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

    private UUID requireOperator(Authentication authentication) {
        UUID userId = currentUserProvider.currentUserId().orElse(null);
        if (userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未登录");
        }
        return userId;
    }

    private boolean isAdmin(@Nullable Authentication authentication) {
        return authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> RoleAuthority.ADMIN.equals(authority.getAuthority()));
    }

    /** 管理层（ADMIN / PROJECT_LEADER）可导出全部 */
    private boolean isManager(@Nullable Authentication authentication) {
        if (authentication == null) {
            return false;
        }
        return authentication.getAuthorities().stream().map(authority -> authority.getAuthority())
                .anyMatch(authority -> RoleAuthority.ADMIN.equals(authority)
                        || "ROLE_PROJECT_LEADER".equals(authority));
    }

    private String nullToEmpty(@Nullable String value) {
        return value == null ? "" : value;
    }

    /** 保留给后续块（导出模板化） */
    @SuppressWarnings("unused")
    private String describe(User user) {
        return user == null ? "" : user.getUsername();
    }
}
