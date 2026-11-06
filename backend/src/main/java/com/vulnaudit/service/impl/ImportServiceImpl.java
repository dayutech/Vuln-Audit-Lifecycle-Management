package com.vulnaudit.service.impl;

import com.vulnaudit.dto.ImportFailedRow;
import com.vulnaudit.dto.CustomerRequest;
import com.vulnaudit.dto.CustomerResponse;
import com.vulnaudit.dto.ImportResult;
import com.vulnaudit.dto.ProjectRequest;
import com.vulnaudit.dto.ProjectResponse;
import com.vulnaudit.dto.ImportUsersResult;
import com.vulnaudit.dto.ImportedUser;
import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.AssetVersion;
import com.vulnaudit.entity.Role;
import com.vulnaudit.entity.Team;
import com.vulnaudit.entity.User;
import com.vulnaudit.entity.Vulnerability;
import com.vulnaudit.entity.enums.WorkflowStatus;
import com.vulnaudit.entity.Customer;
import com.vulnaudit.entity.Project;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.AssetVersionRepository;
import com.vulnaudit.repository.CustomerRepository;
import com.vulnaudit.repository.ProjectPhaseRepository;
import com.vulnaudit.repository.ProjectRepository;
import com.vulnaudit.repository.UserRepository;
import com.vulnaudit.repository.TeamRepository;
import com.vulnaudit.service.AssetProjectLinkService;
import com.vulnaudit.service.AssetService;
import com.vulnaudit.service.CustomerService;
import com.vulnaudit.service.ProjectService;
import com.vulnaudit.service.AuditLogService;
import com.vulnaudit.service.ImportService;
import com.vulnaudit.service.RoleService;
import com.vulnaudit.service.UserService;
import com.vulnaudit.service.VulnerabilityService;
import com.vulnaudit.util.CsvSupport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.annotation.Nullable;

/**
 * 批量导入服务实现
 *
 * <p>注意：本类<b>刻意不加 {@code @Transactional}</b>。若整体开启事务，
 * 某一行失败会将被 {@code @Transactional} 的服务方法标记为 rollback-only，
 * 导致「部分成功」退化为「全部回滚」。逐行调用服务方法即可让每行拥有独立事务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImportServiceImpl implements ImportService {

    /** 单次导入的最大数据行数，避免超大文件拖垮请求线程 */
    private static final int MAX_ROWS = 5000;

    /** 失败原因最大长度，避免响应体膨胀 */
    private static final int REASON_MAX_LENGTH = 300;

    private static final List<String> ASSET_PRIORITIES = List.of("P0", "P1", "P2", "P3");
    private static final List<String> ASSET_STATUSES = List.of("active", "under_test", "suspended", "archived");

    // t85：客户 / 项目导入列
    /** 客户编码 / 项目编码共用同名列 */
    private static final String COL_CODE = "code";
    private static final String COL_INDUSTRY = "industry";
    private static final String COL_LEVEL = "level";
    private static final String COL_OWNER_NAME = "ownername";
    private static final String COL_CONTACT_NAME = "contactname";
    private static final String COL_CONTACT_PHONE = "contactphone";
    private static final String COL_CONTACT_EMAIL = "contactemail";
    private static final String COL_CUSTOMER_CODE_IMPORT = "customercode";
    private static final String COL_MANAGER_NAME = "managername";
    private static final String COL_START_DATE = "startdate";
    private static final String COL_END_DATE = "enddate";
    private static final String COL_IS_PHASED = "isphased";
    private static final String COL_CONTRACT_NO = "contractno";

    /** 客户级别仅 A/B/C（t85） */
    private static final List<String> CUSTOMER_LEVELS = List.of("A", "B", "C");

    /** 项目类型枚举（与 ProjectServiceImpl.PROJECT_TYPES 一致） */
    private static final List<String> PROJECT_TYPE_ENUM =
            List.of("PENTEST", "REAUDIT", "CONSULTING", "TRAINING");

    private static final String COL_NAME = "name";
    private static final String COL_TARGET_TYPE = "targettype";
    private static final String COL_TARGET_SOURCE = "targetsource";
    private static final String COL_PRIORITY = "priority";
    private static final String COL_STATUS = "status";
    private static final String COL_TEAM_ID = "teamid";
    private static final String COL_TEAM_CODE = "teamcode";
    private static final String COL_DESCRIPTION = "description";

    // t78：资产导入必须挂项目 —— 客户 / 项目 / 期次列
    /** 客户编码（可选，仅用于与 projectCode 交叉校验） */
    private static final String COL_CUSTOMER_CODE = "customercode";
    /** 项目编码（可选；不填则挂靠历史项目，并在 notes 里提示） */
    private static final String COL_PROJECT_CODE = "projectcode";
    /** 期次序号（可选；项目必须启用分期且期次存在） */
    private static final String COL_PHASE_NO = "phaseno";

    /** 未指定 projectCode 时的兜底：默认客户 + 历史项目（V18 迁移创建） */
    private static final String DEFAULT_CUSTOMER_CODE = "CUST-DEFAULT";
    private static final String HISTORY_PROJECT_CODE = "PRJ-HISTORY";

    /** 成功行提示文案（放在 ImportResult.notes，不进 failedRows） */
    private static final String IMPORT_NOTE_HISTORY_PROJECT =
            "未指定项目，已挂靠历史项目（PRJ-HISTORY）";

    private static final String COL_TITLE = "title";
    private static final String COL_ASSET_CODE = "assetcode";
    private static final String COL_TYPE = "type";
    private static final String COL_SEVERITY = "severity";
    private static final String COL_CVSS = "cvssscore";
    private static final String COL_WORKFLOW_STATUS = "workflowstatus";

    private static final String COL_USERNAME = "username";
    private static final String COL_EMAIL = "email";
    private static final String COL_FULL_NAME = "fullname";
    private static final String COL_PHONE = "phone";
    private static final String COL_ROLE_CODE = "rolecode";

    /** 资产导入必需列（teamId / teamCode 二选一，单独校验） */
    private static final List<String> ASSET_REQUIRED_COLUMNS = List.of(COL_NAME, COL_TARGET_TYPE, COL_TARGET_SOURCE);

    /** 漏洞导入必需列 */
    private static final List<String> VULNERABILITY_REQUIRED_COLUMNS =
            List.of(COL_TITLE, COL_ASSET_CODE, COL_TYPE, COL_SEVERITY, COL_DESCRIPTION);

    /** 用户导入必需列（phone / roleCode 可选） */
    private static final List<String> USER_REQUIRED_COLUMNS = List.of(COL_USERNAME, COL_EMAIL, COL_FULL_NAME);

    /** roleCode 留空时的默认角色（SRS：新成员默认漏洞挖掘工程师） */
    private static final String DEFAULT_USER_ROLE_CODE = "vulnerability_engineer";

    /** 权限拒绝的失败原因前缀：与校验失败区分（两者都进 failedRows） */
    private static final String PERMISSION_DENIED_PREFIX = "权限拒绝：";

    /** 初始密码长度 */
    private static final int INITIAL_PASSWORD_LENGTH = 16;

    /** 邮箱格式校验（与 UserCreateRequest @Email 口径一致的基础校验） */
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    /** 密码学安全随机源 */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AssetService assetService;
    private final VulnerabilityService vulnerabilityService;
    private final TeamRepository teamRepository;
    private final AssetVersionRepository assetVersionRepository;
    private final AuditLogService auditLogService;
    private final UserService userService;
    private final RoleService roleService;
    private final AssetProjectLinkService assetProjectLinkService;
    private final CustomerRepository customerRepository;
    private final ProjectRepository projectRepository;
    private final ProjectPhaseRepository projectPhaseRepository;
    private final CustomerService customerService;
    private final ProjectService projectService;
    private final UserRepository userRepository;

    // ================================================================== 资产导入

    @Override
    public ImportResult importAssets(byte[] content, @Nullable String fileName, @Nullable UUID operatorId) {
        return importAssets(content, fileName, operatorId, null);
    }

    /**
     * 导入资产（带团队范围）
     *
     * <p>权限拒绝与校验失败都会进 {@code failedRows}，但原因以「权限拒绝：」前缀区分，
     * 便于前端与运维快速分辨「数据格式不对」还是「没有该团队的写权限」。
     * 被拒的行不会落库（校验在 {@code assetService.createAsset} 之前完成）。
     */
    @Override
    public ImportResult importAssets(byte[] content, @Nullable String fileName, @Nullable UUID operatorId,
                                     @Nullable java.util.Set<UUID> allowedTeamIds) {
        long startedAt = System.currentTimeMillis();
        ParseResult parsed = parse(content, fileName, ASSET_REQUIRED_COLUMNS);
        if (parsed.records().isEmpty()) {
            return ImportResult.empty();
        }
        if (parsed.records().size() > MAX_ROWS) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "单次导入最多 " + MAX_ROWS + " 行，当前 " + parsed.records().size() + " 行");
        }

        int successRows = 0;
        List<ImportFailedRow> failedRows = new ArrayList<>();
        List<ImportResult.ImportNote> notes = new ArrayList<>();

        for (CsvSupport.CsvRecord record : parsed.records()) {
            try {
                Asset asset = buildAsset(record, parsed.headerIndex(), operatorId, allowedTeamIds);
                // t78：资产必须挂项目 —— 与手建资产走同一条事务路径（资产 + 挂靠一起成功/回滚）
                ImportTarget target = resolveImportTarget(record, parsed.headerIndex());
                assetProjectLinkService.createAssetWithLink(asset, target.projectId(), target.phaseId(),
                        target.linkType(), target.remark(), operatorId);
                if (target.note() != null) {
                    // 成功行上的提示：已有明确落点但不是「精确指定项目」，不能静默
                    notes.add(new ImportResult.ImportNote(record.line(), target.note()));
                }
                successRows++;
            } catch (Exception e) {
                failedRows.add(new ImportFailedRow(record.line(), reasonOf(e)));
                log.debug("资产导入第 {} 行失败: {}", record.line(), reasonOf(e));
            }
        }

        ImportResult result = ImportResult.of(parsed.records().size(), successRows, failedRows, notes);
        log.info("资产批量导入完成: file={}, total={}, success={}, failed={}, notes={}, scoped={}, cost={}ms",
                fileName, result.totalRows(), result.successRows(), result.failedRows().size(),
                result.notes().size(), allowedTeamIds != null, System.currentTimeMillis() - startedAt);
        audit(operatorId, "POST ImportController.importAssets",
                describe("资产", fileName, result));
        return result;
    }

    /**
     * 解析该行要挂靠的项目（t78）
     *
     * <ol>
     *   <li>给了 {@code projectCode} → 按 code 查项目（不存在 → 该行失败）；</li>
     *   <li>同时给了 {@code customerCode} → 必须与该项目的客户一致（不一致 → 该行失败）；</li>
     *   <li>给了 {@code phaseNo}：项目必须 {@code isPhased=true} 且期次属于该项目（否则该行失败）；</li>
     *   <li><b>未给 {@code projectCode}</b> → 挂到默认客户 {@code CUST-DEFAULT} 的 {@code PRJ-HISTORY}
     *       （linkType=SHARED），并在 notes 里提示「未指定项目，已挂靠历史项目」：
     *       这是<b>成功行</b>，但必须提示，不得静默。</li>
     * </ol>
     */
    private ImportTarget resolveImportTarget(CsvSupport.CsvRecord record, Map<String, Integer> headerIndex) {
        Map<String, String> cells = toCells(record, headerIndex);
        String projectCode = optionalCell(cells, COL_PROJECT_CODE);
        String customerCode = optionalCell(cells, COL_CUSTOMER_CODE);
        String phaseNoText = optionalCell(cells, COL_PHASE_NO);

        if (projectCode == null) {
            if (customerCode != null || phaseNoText != null) {
                // 只给客户/期次却没给项目：无法确定唯一项目，宁可失败也不“猜”
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "指定 customerCode / phaseNo 时必须同时提供 projectCode");
            }
            return defaultHistoryTarget();
        }

        Project project = projectRepository.findByCode(projectCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND,
                        "项目不存在: " + projectCode));

        if (customerCode != null) {
            String actualCustomerCode = customerRepository.findById(project.getCustomerId())
                    .map(Customer::getCode).orElse(null);
            if (!customerCode.equalsIgnoreCase(actualCustomerCode)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "客户与项目不匹配: customerCode=" + customerCode + " 但项目 "
                                + projectCode + " 属于 " + actualCustomerCode);
            }
        }

        UUID phaseId = null;
        if (phaseNoText != null) {
            if (!Boolean.TRUE.equals(project.getIsPhased())) {
                throw new BusinessException(ErrorCode.PROJECT_NOT_PHASED,
                        "项目未启用分期，不能指定 phaseNo: " + projectCode);
            }
            int phaseNo;
            try {
                phaseNo = Integer.parseInt(phaseNoText.trim());
            } catch (NumberFormatException e) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的 phaseNo: " + phaseNoText);
            }
            phaseId = projectPhaseRepository.findByProjectIdAndPhaseNo(project.getId(), phaseNo)
                    .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_PHASE_NOT_FOUND,
                            "期次不存在: " + projectCode + " 第 " + phaseNo + " 期"))
                    .getId();
        }

        return new ImportTarget(project.getId(), phaseId, "TARGET", null, null);
    }

    /**
     * 「未指定项目」的兜底：默认客户 CUST-DEFAULT 下的历史项目 PRJ-HISTORY
     *
     * <p>两者由 V18 迁移创建；<b>缺失时按需补齐</b>（幂等，codes 唯一）—— 这样无论迁移是否执行过、
     * 还是在测试库（{@code ddl-auto=create-drop}）里，导入都不会产生「无挂靠资产」。
     * 补齐用 upsert 语义：先查 code，查不到才插入，并发下由唯一键兜底。
     */
    private ImportTarget defaultHistoryTarget() {
        Customer defaultCustomer = customerRepository.findByCode(DEFAULT_CUSTOMER_CODE)
                .orElseGet(() -> customerRepository.save(Objects.requireNonNull(Customer.builder()
                        .code(DEFAULT_CUSTOMER_CODE)
                        .name("历史数据")
                        .industry("未分类")
                        .level("C")
                        .status("active")
                        .description("CSV 导入兜底自动创建：承接未指定项目的资产")
                        .build())));

        Project historyProject = projectRepository.findByCode(HISTORY_PROJECT_CODE)
                .orElseGet(() -> projectRepository.save(Objects.requireNonNull(Project.builder()
                        .code(HISTORY_PROJECT_CODE)
                        .customerId(defaultCustomer.getId())
                        .name("历史项目")
                        .type("OTHER")
                        .status("ACTIVE")
                        .managerId(resolveHistoryManagerId(defaultCustomer))
                        .isPhased(false)
                        .description("CSV 导入兜底自动创建：承接未指定项目的资产")
                        .build())));

        if (!defaultCustomer.getId().equals(historyProject.getCustomerId())) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "历史项目 " + HISTORY_PROJECT_CODE + " 不属于默认客户 " + DEFAULT_CUSTOMER_CODE);
        }
        return new ImportTarget(historyProject.getId(), null, "SHARED",
                IMPORT_NOTE_HISTORY_PROJECT, "CSV 导入未指定 projectCode，自动挂靠历史项目");
    }

    /** 历史项目的经理人：优先默认客户负责人，退化到当前操作者（projects.manager_id 非空） */
    private UUID resolveHistoryManagerId(Customer defaultCustomer) {
        if (defaultCustomer.getOwnerId() != null) {
            return defaultCustomer.getOwnerId();
        }
        return userService.findAll(0, 1, null, null).getContent().stream()
                .map(com.vulnaudit.entity.User::getId)
                .findFirst()
                .orElseGet(() -> {
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR,
                            "无法确定历史项目的经理人（系统内没有用户）");
                });
    }

    /** 单行的挂靠目标 */
    private record ImportTarget(UUID projectId, @Nullable UUID phaseId, String linkType,
                                @Nullable String note, @Nullable String remark) {
    }

    private Asset buildAsset(CsvSupport.CsvRecord record, Map<String, Integer> headerIndex, @Nullable UUID operatorId,
                             @Nullable java.util.Set<UUID> allowedTeamIds) {
        if (operatorId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未授权");
        }
        Map<String, String> cells = toCells(record, headerIndex);

        String name = requireCell(cells, COL_NAME, "资产名称");
        checkLength(name, 200, "资产名称");
        String targetType = requireCell(cells, COL_TARGET_TYPE, "目标类型");
        checkLength(targetType, 100, "目标类型");
        String targetSource = requireCell(cells, COL_TARGET_SOURCE, "目标来源");
        checkLength(targetSource, 20, "目标来源");
        String description = optionalCell(cells, COL_DESCRIPTION);
        if (description != null) {
            checkLength(description, 1000, "资产描述");
        }

        String priority = optionalCell(cells, COL_PRIORITY);
        if (priority != null) {
            priority = priority.toUpperCase(Locale.ROOT);
            if (!ASSET_PRIORITIES.contains(priority)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "无效的优先级: " + priority + "，可选值 P0/P1/P2/P3");
            }
        }

        String status = optionalCell(cells, COL_STATUS);
        if (status != null) {
            status = status.toLowerCase(Locale.ROOT);
            if (!ASSET_STATUSES.contains(status)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "无效的资产状态: " + status + "，可选值 active/under_test/suspended/archived");
            }
        }

        UUID teamId = resolveTeamId(cells);
        checkTeamWritable(teamId, allowedTeamIds);

        return Asset.builder()
                .name(name)
                .targetType(targetType)
                .targetSource(targetSource)
                .priority(priority)
                .status(status)
                .teamId(teamId)
                .ownerId(operatorId)
                .description(description)
                .build();
    }

    /**
     * 团队范围校验（fail-closed）
     *
     * <p>{@code allowedTeamIds == null} 表示不限制（admin/project_leader）；
     * 否则该行必须显式指定团队且落在允许集合内，缺失或越界都按「权限拒绝」处理，
     * 不存在「未指定团队就默认放行」的分支。
     */
    private void checkTeamWritable(@Nullable UUID teamId, @Nullable java.util.Set<UUID> allowedTeamIds) {
        if (allowedTeamIds == null) {
            return;
        }
        if (teamId == null) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    PERMISSION_DENIED_PREFIX + "导入资产必须指定 teamId 或 teamCode（只能导入自己领导/所属的团队）");
        }
        if (!allowedTeamIds.contains(teamId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    PERMISSION_DENIED_PREFIX + "无权导入该团队（teamId=" + teamId + "）");
        }
    }

    // ================================================================== 漏洞导入

    @Override
    public ImportResult importVulnerabilities(byte[] content, @Nullable String fileName, @Nullable UUID operatorId) {
        return importVulnerabilities(content, fileName, operatorId, null);
    }

    /**
     * 导入漏洞（逐行按目标资产做写权限校验）
     *
     * <p>不整单拒绝：无权行记入 {@code failedRows}（含行号与「权限拒绝：无权在该资产下创建漏洞」），
     * 其余行照常导入；权限校验发生在创建资产版本之前，被拒行不会留下任何写入。
     */
    @Override
    public ImportResult importVulnerabilities(byte[] content, @Nullable String fileName, @Nullable UUID operatorId,
                                              @Nullable java.util.function.Predicate<UUID> assetWritable) {
        long startedAt = System.currentTimeMillis();
        ParseResult parsed = parse(content, fileName, VULNERABILITY_REQUIRED_COLUMNS);
        if (parsed.records().isEmpty()) {
            return ImportResult.empty();
        }
        if (parsed.records().size() > MAX_ROWS) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "单次导入最多 " + MAX_ROWS + " 行，当前 " + parsed.records().size() + " 行");
        }

        int successRows = 0;
        List<ImportFailedRow> failedRows = new ArrayList<>();

        for (CsvSupport.CsvRecord record : parsed.records()) {
            try {
                Vulnerability vulnerability = buildVulnerability(record, parsed.headerIndex(), operatorId, assetWritable);
                vulnerabilityService.createVulnerability(vulnerability);
                successRows++;
            } catch (Exception e) {
                failedRows.add(new ImportFailedRow(record.line(), reasonOf(e)));
                log.debug("漏洞导入第 {} 行失败: {}", record.line(), reasonOf(e));
            }
        }

        ImportResult result = ImportResult.of(parsed.records().size(), successRows, failedRows);
        log.info("漏洞批量导入完成: file={}, total={}, success={}, failed={}, scoped={}, cost={}ms",
                fileName, result.totalRows(), result.successRows(), result.failedRows().size(),
                assetWritable != null, System.currentTimeMillis() - startedAt);
        audit(operatorId, "POST ImportController.importVulnerabilities",
                describe("漏洞", fileName, result));
        return result;
    }

    private Vulnerability buildVulnerability(CsvSupport.CsvRecord record, Map<String, Integer> headerIndex,
                                             @Nullable UUID operatorId,
                                             @Nullable java.util.function.Predicate<UUID> assetWritable) {
        if (operatorId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未授权");
        }
        Map<String, String> cells = toCells(record, headerIndex);

        String title = requireCell(cells, COL_TITLE, "漏洞标题");
        checkLength(title, 200, "漏洞标题");
        String assetCode = requireCell(cells, COL_ASSET_CODE, "资产编号");
        Asset asset = assetService.findByCode(assetCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.ASSET_NOT_FOUND, "资产编号不存在: " + assetCode));

        // 权限判定必须在任何写操作（例如自动创建资产版本）之前完成
        checkAssetWritable(asset, assetWritable);

        String type = requireCell(cells, COL_TYPE, "漏洞类型");
        checkLength(type, 100, "漏洞类型");
        // severity 不在此处归一：交由 VulnerabilityService 归一为大写并校验，避免绕过服务层规则
        String severity = requireCell(cells, COL_SEVERITY, "严重程度");
        String description = requireCell(cells, COL_DESCRIPTION, "漏洞描述");
        BigDecimal cvssScore = parseCvssScore(optionalCell(cells, COL_CVSS));
        String workflowStatus = parseWorkflowStatus(optionalCell(cells, COL_WORKFLOW_STATUS));

        UUID discoveredVersionId = resolveOrCreateAssetVersion(asset, operatorId);

        return Vulnerability.builder()
                .title(title)
                .assetId(asset.getId())
                .discoveredVersionId(discoveredVersionId)
                .type(type)
                .severity(severity)
                .cvssScore(cvssScore)
                .workflowStatus(workflowStatus)
                .description(description)
                // CSV 无 detail 列，实体要求 detail 非空，用描述兜底，避免落库失败
                .detail(description)
                .discovererId(operatorId)
                .build();
    }

    /**
     * 资产写权限校验（fail-closed）
     *
     * <p>{@code assetWritable == null} 表示不限制（内部调用）；否则判定为 false 即拒绝。
     * 判定异常按拒绝处理，绝不因异常而放行。
     */
    private void checkAssetWritable(Asset asset, @Nullable java.util.function.Predicate<UUID> assetWritable) {
        if (assetWritable == null) {
            return;
        }
        boolean writable;
        try {
            writable = assetWritable.test(asset.getId());
        } catch (RuntimeException e) {
            log.warn("资产写权限判定异常，按拒绝处理: assetId={}", asset.getId(), e);
            writable = false;
        }
        if (!writable) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    PERMISSION_DENIED_PREFIX + "无权在该资产下创建漏洞（assetCode=" + asset.getCode() + "）");
        }
    }

    /**
     * 取资产默认版本；资产尚无任何版本时自动创建 v1 作为默认/最新版本。
     *
     * <p>漏洞必须挂在某个资产版本上（服务层强校验版本归属），而通过 API 创建的资产
     * 默认没有版本，因此导入时需要补齐这一前置数据。
     */
    private UUID resolveOrCreateAssetVersion(Asset asset, UUID operatorId) {
        Optional<AssetVersion> existing = assetVersionRepository.findByAssetIdAndIsDefaultTrue(asset.getId())
                .or(() -> assetVersionRepository.findFirstByAssetIdOrderByVersionNoDesc(asset.getId()));
        if (existing.isPresent()) {
            return existing.get().getId();
        }

        // 能进入这里说明资产还没有任何版本，因此无需清理旧版本的 default/latest 标记
        AssetVersion created = assetVersionRepository.save(AssetVersion.builder()
                .assetId(asset.getId())
                .versionNo(1)
                .name("v1")
                .description("批量导入漏洞时自动创建的初始版本")
                .isDefault(true)
                .isLatest(true)
                .status("active")
                .createdBy(operatorId)
                .build());
        log.info("导入漏洞时为资产自动创建初始版本: assetId={}, versionId={}", asset.getId(), created.getId());
        return created.getId();
    }

    // ================================================================== 用户导入

    @Override
    public ImportUsersResult importUsers(byte[] content, @Nullable String fileName, @Nullable UUID operatorId) {
        long startedAt = System.currentTimeMillis();
        if (operatorId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未授权");
        }

        ParseResult parsed = parse(content, fileName, USER_REQUIRED_COLUMNS);
        if (parsed.records().isEmpty()) {
            return ImportUsersResult.empty();
        }
        if (parsed.records().size() > MAX_ROWS) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "单次导入最多 " + MAX_ROWS + " 行，当前 " + parsed.records().size() + " 行");
        }

        int successRows = 0;
        List<ImportFailedRow> failedRows = new ArrayList<>();
        List<ImportedUser> createdUsers = new ArrayList<>();

        for (CsvSupport.CsvRecord record : parsed.records()) {
            try {
                ImportedUser imported = createUserFromRow(record, parsed.headerIndex(), operatorId);
                createdUsers.add(imported);
                successRows++;
            } catch (Exception e) {
                failedRows.add(new ImportFailedRow(record.line(), reasonOf(e)));
                log.debug("用户导入第 {} 行失败: {}", record.line(), reasonOf(e));
            }
        }

        ImportUsersResult result = ImportUsersResult.of(parsed.records().size(), successRows, failedRows, createdUsers);
        log.info("用户批量导入完成: file={}, total={}, success={}, failed={}, cost={}ms",
                fileName, result.totalRows(), result.successRows(), result.failedRows().size(),
                System.currentTimeMillis() - startedAt);
        audit(operatorId, "POST ImportController.importUsers",
                describeUsers(fileName, result));
        return result;
    }

    /**
     * 单行用户导入：校验 → 生成初始密码 → UserService.createUser（唯一性 + BCrypt）→ RoleService 绑定角色
     */
    private ImportedUser createUserFromRow(CsvSupport.CsvRecord record, Map<String, Integer> headerIndex,
                                           UUID operatorId) {
        Map<String, String> cells = toCells(record, headerIndex);

        String username = requireCell(cells, COL_USERNAME, "用户名");
        checkLength(username, 50, "用户名");
        if (username.length() < 3) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "用户名长度必须在 3-50 个字符之间");
        }
        String email = requireCell(cells, COL_EMAIL, "邮箱");
        checkLength(email, 255, "邮箱");
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "邮箱格式不正确: " + email);
        }
        // 复用 UserService 的唯一性校验（同时 createUser 内部还会再校验一次，双保险）
        if (userService.existsByUsername(username)) {
            throw new BusinessException(ErrorCode.USERNAME_ALREADY_EXISTS, "用户名已存在: " + username);
        }
        if (userService.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS, "邮箱已存在: " + email);
        }

        String fullName = requireCell(cells, COL_FULL_NAME, "姓名");
        checkLength(fullName, 100, "姓名");
        String phone = optionalCell(cells, COL_PHONE);
        if (phone != null) {
            checkLength(phone, 20, "手机号");
        }

        String roleCodeCell = optionalCell(cells, COL_ROLE_CODE);
        final String roleCode = roleCodeCell == null ? DEFAULT_USER_ROLE_CODE : roleCodeCell;
        // 角色必须先存在：先校验再创建用户，避免「用户建好了但角色绑定失败」的中间态
        Role role = roleService.findByCode(roleCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROLE_NOT_FOUND, "角色不存在: " + roleCode));

        // CSV 不含密码列：生成强随机初始密码（16 位，含大小写/数字/符号），禁止弱默认口令
        String initialPassword = generateInitialPassword();

        User user = User.builder()
                .username(username)
                .email(email)
                .fullName(fullName)
                .phone(phone)
                .passwordHash(initialPassword) // UserService.createUser 内部用 BCrypt 编码
                .status("active")
                .build();

        // 必须走 service 层：唯一性校验 + 密码编码都在 UserService.createUser 内完成
        User savedUser = userService.createUser(user);

        boolean assigned = roleService.assignRoleToUser(savedUser.getId(), role.getId(), operatorId);
        log.info("用户导入成功: userId={}, username={}, role={}, roleAssigned={}",
                savedUser.getId(), savedUser.getUsername(), roleCode, assigned);

        return new ImportedUser(record.line(), savedUser.getId(), savedUser.getUsername(),
                role.getCode(), initialPassword, true);
    }

    /**
     * 生成强随机初始密码：长度 16，至少包含大写、小写、数字、符号各一位
     */
    String generateInitialPassword() {
        String upper = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        String lower = "abcdefghijkmnopqrstuvwxyz";
        String digits = "23456789";
        String symbols = "!@#$%^&*-_=+?";
        String all = upper + lower + digits + symbols;

        char[] password = new char[INITIAL_PASSWORD_LENGTH];
        password[0] = upper.charAt(SECURE_RANDOM.nextInt(upper.length()));
        password[1] = lower.charAt(SECURE_RANDOM.nextInt(lower.length()));
        password[2] = digits.charAt(SECURE_RANDOM.nextInt(digits.length()));
        password[3] = symbols.charAt(SECURE_RANDOM.nextInt(symbols.length()));
        for (int i = 4; i < password.length; i++) {
            password[i] = all.charAt(SECURE_RANDOM.nextInt(all.length()));
        }
        for (int i = password.length - 1; i > 0; i--) {
            int j = SECURE_RANDOM.nextInt(i + 1);
            char tmp = password[i];
            password[i] = password[j];
            password[j] = tmp;
        }
        return new String(password);
    }

    private String describeUsers(@Nullable String fileName, ImportUsersResult result) {
        StringBuilder builder = new StringBuilder();
        builder.append("CSV导入用户")
                .append(": file=").append(safeFileName(fileName))
                .append(", total=").append(result.totalRows())
                .append(", success=").append(result.successRows())
                .append(", failed=").append(result.failedRows().size());
        if (!result.failedRows().isEmpty()) {
            builder.append(", firstError=line ")
                    .append(result.failedRows().get(0).line())
                    .append(": ").append(result.failedRows().get(0).reason());
        }
        return builder.toString();
    }

    // ================================================================== 客户 / 项目导入（t85）

    /** 客户导入：表头 + 必填列 */
    private static final List<String> CUSTOMER_REQUIRED_COLUMNS = List.of(COL_CODE, COL_NAME);

    /** 项目导入：表头 + 必填列 */
    private static final List<String> PROJECT_REQUIRED_COLUMNS =
            List.of(COL_CODE, COL_NAME, COL_CUSTOMER_CODE_IMPORT, COL_MANAGER_NAME);

    /**
     * 导入客户（t85）
     *
     * <p>逐行独立：任何一行失败只记入 failedRows，不影响其他行；失败行不落库（校验在
     * {@code customerService.create} 之前完成，异常行不会写库）。
     * 成功行逐条写审计（resourceType=customer）。
     */
    @Override
    public ImportResult importCustomers(byte[] content, @Nullable String fileName, @Nullable UUID operatorId) {
        long startedAt = System.currentTimeMillis();
        ParseResult parsed = parse(content, fileName, CUSTOMER_REQUIRED_COLUMNS);
        if (parsed.records().isEmpty()) {
            return ImportResult.empty();
        }
        if (parsed.records().size() > MAX_ROWS) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "单次导入最多 " + MAX_ROWS + " 行，当前 " + parsed.records().size() + " 行");
        }

        int successRows = 0;
        List<ImportFailedRow> failedRows = new ArrayList<>();

        for (CsvSupport.CsvRecord record : parsed.records()) {
            try {
                CustomerRequest request = buildCustomerRequest(record, parsed.headerIndex(), operatorId);
                CustomerResponse created = customerService.create(request, operatorId);
                successRows++;
                auditRow(operatorId, "import_customer", "customer", created.id(),
                        "批量导入客户: " + created.code());
            } catch (Exception e) {
                failedRows.add(new ImportFailedRow(record.line(), reasonOf(e)));
                log.debug("客户导入第 {} 行失败: {}", record.line(), reasonOf(e));
            }
        }

        ImportResult result = ImportResult.of(parsed.records().size(), successRows, failedRows);
        log.info("客户批量导入完成: file={}, total={}, success={}, failed={}, cost={}ms",
                fileName, result.totalRows(), result.successRows(), result.failedRows().size(),
                System.currentTimeMillis() - startedAt);
        audit(operatorId, "POST ImportController.importCustomers", describe("客户", fileName, result));
        return result;
    }

    private CustomerRequest buildCustomerRequest(CsvSupport.CsvRecord record, Map<String, Integer> headerIndex,
                                                 @Nullable UUID operatorId) {
        if (operatorId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未授权");
        }
        Map<String, String> cells = toCells(record, headerIndex);

        String code = requireCell(cells, COL_CODE, "客户编码");
        checkLength(code, 64, "客户编码");
        String name = requireCell(cells, COL_NAME, "客户名称");
        checkLength(name, 200, "客户名称");
        String industry = optionalCell(cells, COL_INDUSTRY);
        if (industry != null) {
            checkLength(industry, 100, "行业");
        }

        // level 仅 A/B/C（大小写归一）；留空则不设
        String level = optionalCell(cells, COL_LEVEL);
        if (level != null) {
            level = level.trim().toUpperCase(Locale.ROOT);
            if (!CUSTOMER_LEVELS.contains(level)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "客户级别非法: " + level + "，可选值 " + String.join(" / ", CUSTOMER_LEVELS));
            }
        }

        // ownerName 按 username 解析；找不到 → 该行失败（不猜测、不给默认负责人）
        UUID ownerId = null;
        String ownerName = optionalCell(cells, COL_OWNER_NAME);
        if (ownerName != null) {
            ownerId = resolveUserIdByUsername(ownerName, "客户经理");
        }

        String contactName = optionalCell(cells, COL_CONTACT_NAME);
        if (contactName != null) {
            checkLength(contactName, 100, "联系人");
        }
        String contactPhone = optionalCell(cells, COL_CONTACT_PHONE);
        if (contactPhone != null) {
            checkLength(contactPhone, 50, "联系电话");
        }
        String contactEmail = optionalCell(cells, COL_CONTACT_EMAIL);
        if (contactEmail != null) {
            checkLength(contactEmail, 200, "联系邮箱");
        }
        String description = optionalCell(cells, COL_DESCRIPTION);
        if (description != null) {
            checkLength(description, 1000, "客户备注");
        }

        return new CustomerRequest(code, name, industry, null, level, contactName, contactPhone,
                contactEmail, ownerId, "active", description);
    }

    /**
     * 导入项目（t85）
     *
     * <p>逐行独立；{@code customerCode}/{@code managerName} 必须能解析成已存在的客户/用户，
     * {@code type} 必须属于枚举集合，日期必须 {@code YYYY-MM-DD}，{@code isPhased} 接受 true/false/1/0。
     * 成功行逐条写审计（resourceType=project）。
     */
    @Override
    public ImportResult importProjects(byte[] content, @Nullable String fileName, @Nullable UUID operatorId) {
        long startedAt = System.currentTimeMillis();
        ParseResult parsed = parse(content, fileName, PROJECT_REQUIRED_COLUMNS);
        if (parsed.records().isEmpty()) {
            return ImportResult.empty();
        }
        if (parsed.records().size() > MAX_ROWS) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "单次导入最多 " + MAX_ROWS + " 行，当前 " + parsed.records().size() + " 行");
        }

        int successRows = 0;
        List<ImportFailedRow> failedRows = new ArrayList<>();

        for (CsvSupport.CsvRecord record : parsed.records()) {
            try {
                ProjectRequest request = buildProjectRequest(record, parsed.headerIndex(), operatorId);
                ProjectResponse created = projectService.create(request, operatorId);
                successRows++;
                auditRow(operatorId, "import_project", "project", created.id(),
                        "批量导入项目: " + created.code());
            } catch (Exception e) {
                failedRows.add(new ImportFailedRow(record.line(), reasonOf(e)));
                log.debug("项目导入第 {} 行失败: {}", record.line(), reasonOf(e));
            }
        }

        ImportResult result = ImportResult.of(parsed.records().size(), successRows, failedRows);
        log.info("项目批量导入完成: file={}, total={}, success={}, failed={}, cost={}ms",
                fileName, result.totalRows(), result.successRows(), result.failedRows().size(),
                System.currentTimeMillis() - startedAt);
        audit(operatorId, "POST ImportController.importProjects", describe("项目", fileName, result));
        return result;
    }

    private ProjectRequest buildProjectRequest(CsvSupport.CsvRecord record, Map<String, Integer> headerIndex,
                                               @Nullable UUID operatorId) {
        if (operatorId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "未授权");
        }
        Map<String, String> cells = toCells(record, headerIndex);

        String code = requireCell(cells, COL_CODE, "项目编码");
        checkLength(code, 64, "项目编码");
        String name = requireCell(cells, COL_NAME, "项目名称");
        checkLength(name, 200, "项目名称");

        String customerCode = requireCell(cells, COL_CUSTOMER_CODE_IMPORT, "客户编码");
        Customer customer = customerRepository.findByCode(customerCode)
                .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND,
                        "客户不存在: " + customerCode));

        String managerName = requireCell(cells, COL_MANAGER_NAME, "项目经理用户名");
        UUID managerId = resolveUserIdByUsername(managerName, "项目经理");

        // type 留空时由服务层默认 PENTEST；给了就必须合法（服务层同样校验，这里提前给出可读原因）
        String type = optionalCell(cells, COL_TYPE);
        if (type != null) {
            String normalized = type.trim().toUpperCase(Locale.ROOT);
            if (!PROJECT_TYPE_ENUM.contains(normalized)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST,
                        "项目类型非法: " + type + "，可选值 " + String.join(" / ", PROJECT_TYPE_ENUM));
            }
            type = normalized;
        }

        LocalDate startDate = parseDate(optionalCell(cells, COL_START_DATE), "startDate");
        LocalDate endDate = parseDate(optionalCell(cells, COL_END_DATE), "endDate");
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "结束日期不能早于开始日期");
        }

        Boolean isPhased = parseBoolean(optionalCell(cells, COL_IS_PHASED), "isPhased");

        String contractNo = optionalCell(cells, COL_CONTRACT_NO);
        if (contractNo != null) {
            checkLength(contractNo, 100, "合同号");
        }
        String description = optionalCell(cells, COL_DESCRIPTION);
        if (description != null) {
            checkLength(description, 1000, "项目备注");
        }

        return new ProjectRequest(code, customer.getId(), name, type, managerId,
                startDate, endDate, isPhased, contractNo, description);
    }

    /** username → userId（找不到直接失败，不猜测、不默认） */
    private UUID resolveUserIdByUsername(String username, String fieldLabel) {
        return userRepository.findByUsername(username)
                .map(User::getId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND,
                        fieldLabel + "不存在: " + username + "（按用户名匹配，请先在用户管理中创建）"));
    }

    /** YYYY-MM-DD 解析（空值返回 null；格式错 → 该行失败） */
    @Nullable
    private LocalDate parseDate(@Nullable String text, String fieldLabel) {
        if (text == null) {
            return null;
        }
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    fieldLabel + " 日期格式错误: " + text + "（应为 YYYY-MM-DD）");
        }
    }

    /** isPhased 接受 true/false/1/0（大小写不敏感） */
    @Nullable
    private Boolean parseBoolean(@Nullable String text, String fieldLabel) {
        if (text == null) {
            return null;
        }
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "true", "1", "y", "yes" -> Boolean.TRUE;
            case "false", "0", "n", "no" -> Boolean.FALSE;
            default -> throw new BusinessException(ErrorCode.BAD_REQUEST,
                    fieldLabel + " 取值非法: " + text + "（应为 true/false/1/0）");
        };
    }

    /** 单行审计（resourceType 区分 customer / project）；审计失败不影响导入 */
    private void auditRow(@Nullable UUID operatorId, String action, String resourceType,
                          @Nullable UUID resourceId, String detail) {
        if (operatorId == null) {
            return;
        }
        try {
            auditLogService.log(operatorId, action, resourceType, resourceId, detail);
        } catch (Exception e) {
            log.warn("导入单行审计写入失败: {}", e.getMessage());
        }
    }

    // ================================================================== 模板

    @Override
    public String assetTemplate() {
        // t78：新增 customerCode / projectCode / phaseNo —— 资产必须挂项目；
        // 第二行示例演示「不填 projectCode」的兜底语义（自动挂 PRJ-HISTORY 并在 notes 提示）
        return "name,targetType,targetSource,priority,status,teamCode,customerCode,projectCode,phaseNo,description\n"
                + "示例-客户门户Web应用,Web应用,客户委托,P2,active,红队一组,CUST-20250101-AB12,PRJ-20250101-CD34,1,示例资产（导入前请删除本行）\n"
                + "示例-未指定项目的资产,Web应用,客户委托,P3,active,红队一组,,,,不填 projectCode 将自动挂靠历史项目 PRJ-HISTORY（导入前请删除本行）\n";
    }

    @Override
    public String vulnerabilityTemplate() {
        return "title,assetCode,type,severity,cvssScore,workflowStatus,description\n"
                + "示例-登录接口SQL注入,AST-20250101-ABC123,SQL注入,HIGH,8.8,discovered,示例漏洞（导入前请删除本行）\n";
    }

    @Override
    public String userTemplate() {
        return "username,email,fullName,phone,roleCode\n"
                + "示例-zhangsan,zhangsan@example.com,张三,13800000000,vulnerability_engineer\n";
    }

    @Override
    public String customerTemplate() {
        // t85：客户导入模板（两行示例，第二行演示「不带客户经理」）
        return "code,name,industry,level,ownerName,contactName,contactPhone,contactEmail,description\n"
                + "CUST-20250101-AB12,示例-某银行,金融,A,zhangsan,张三,13800000000,zhangsan@example.com,示例客户（导入前请删除本行）\n"
                + "CUST-20250101-CD34,示例-某制造业,制造,B,,李四,13900000000,lisi@example.com,不填 ownerName 则不设客户经理\n";
    }

    @Override
    public String projectTemplate() {
        // t85：项目导入模板（customerCode / managerName 都按已有记录解析）
        return "code,name,customerCode,type,managerName,startDate,endDate,isPhased,contractNo,description\n"
                + "PRJ-20250101-CD34,示例-年度渗透测试,CUST-20250101-AB12,PENTEST,zhangsan,2025-01-01,2025-12-31,true,HT-2025-001,示例项目（导入前请删除本行）\n"
                + "PRJ-20250101-EF56,示例-代码审计,CUST-20250101-AB12,CONSULTING,lisi,2025-03-01,,0,,不分期项目（导入前请删除本行）\n";
    }

    // ================================================================== 解析与校验

    /**
     * 解析 CSV 并校验表头
     *
     * @return 表头索引 + 数据记录（不含表头与空行）
     */
    private ParseResult parse(byte[] content, @Nullable String fileName, List<String> requiredColumns) {
        if (content == null || content.length == 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "CSV 文件为空");
        }
        String text = new String(content, StandardCharsets.UTF_8);
        List<CsvSupport.CsvRecord> records = CsvSupport.parse(text);
        if (records.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "CSV 文件为空或缺少表头");
        }

        CsvSupport.CsvRecord header = records.get(0);
        Map<String, Integer> headerIndex = new LinkedHashMap<>();
        for (int i = 0; i < header.fields().size(); i++) {
            String key = CsvSupport.normalizeHeader(header.get(i));
            if (!key.isEmpty()) {
                headerIndex.putIfAbsent(key, i);
            }
        }

        List<String> missing = requiredColumns.stream()
                .filter(column -> !headerIndex.containsKey(column))
                .toList();
        if (!missing.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "CSV 表头缺少必需列: " + String.join(", ", missing) + "（文件: " + safeFileName(fileName) + "）");
        }

        List<CsvSupport.CsvRecord> dataRecords = records.size() > 1
                ? new ArrayList<>(records.subList(1, records.size()))
                : List.of();
        return new ParseResult(headerIndex, dataRecords);
    }

    private Map<String, String> toCells(CsvSupport.CsvRecord record, Map<String, Integer> headerIndex) {
        Map<String, String> cells = new HashMap<>();
        headerIndex.forEach((key, index) -> cells.put(key, record.get(index)));
        return cells;
    }

    private UUID resolveTeamId(Map<String, String> cells) {
        String teamIdRaw = optionalCell(cells, COL_TEAM_ID);
        String teamCodeRaw = optionalCell(cells, COL_TEAM_CODE);

        if (teamIdRaw != null) {
            UUID teamId = parseUuid(teamIdRaw, "teamId");
            if (!teamRepository.existsById(teamId)) {
                throw new BusinessException(ErrorCode.TEAM_NOT_FOUND, "团队不存在: " + teamIdRaw);
            }
            return teamId;
        }

        if (teamCodeRaw != null) {
            try {
                UUID teamId = UUID.fromString(teamCodeRaw);
                if (teamRepository.existsById(teamId)) {
                    return teamId;
                }
            } catch (IllegalArgumentException ignored) {
                // 不是 UUID，按团队名称解析
            }
            return teamRepository.findByName(teamCodeRaw)
                    .map(Team::getId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.TEAM_NOT_FOUND,
                            "团队不存在（teamCode 支持团队名称或团队ID）: " + teamCodeRaw));
        }

        throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少 teamId 或 teamCode（二者填其一）");
    }

    private BigDecimal parseCvssScore(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        BigDecimal value;
        try {
            value = new BigDecimal(raw);
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "cvssScore 必须是数字: " + raw);
        }
        if (value.compareTo(BigDecimal.ZERO) < 0 || value.compareTo(BigDecimal.TEN) > 0) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "cvssScore 必须在 0.0 - 10.0 之间: " + raw);
        }
        return value.setScale(1, RoundingMode.HALF_UP);
    }

    private String parseWorkflowStatus(@Nullable String raw) {
        if (raw == null) {
            return "discovered";
        }
        String code = raw.toLowerCase(Locale.ROOT);
        if (WorkflowStatus.fromCode(code) == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "无效的 workflowStatus: " + raw
                            + "，可选值 discovered/verified/analyzing/analyzed/exploit_development/field_testing/delivered/closed");
        }
        return code;
    }

    private String requireCell(Map<String, String> cells, String key, String label) {
        String value = optionalCell(cells, key);
        if (value == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, label + " 不能为空");
        }
        return value;
    }

    @Nullable
    private String optionalCell(Map<String, String> cells, String key) {
        String value = cells.get(key);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void checkLength(String value, int max, String label) {
        if (value != null && value.length() > max) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, label + " 长度不能超过 " + max + " 个字符");
        }
    }

    private UUID parseUuid(String raw, String label) {
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, label + " 不是合法的 UUID: " + raw);
        }
    }

    // ================================================================== 辅助

    /**
     * 逐行失败原因：业务异常直接使用其消息，其余异常降级为可读描述
     */
    private String reasonOf(Exception e) {
        String reason;
        if (e instanceof BusinessException businessException) {
            reason = businessException.getMessage();
        } else if (e instanceof IllegalArgumentException) {
            reason = e.getMessage();
        } else {
            log.warn("导入行处理异常: {}", e.getMessage(), e);
            reason = "处理失败: " + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage());
        }
        if (reason == null || reason.isBlank()) {
            reason = "处理失败";
        }
        return reason.length() > REASON_MAX_LENGTH ? reason.substring(0, REASON_MAX_LENGTH) : reason;
    }

    private String describe(String domain, @Nullable String fileName, ImportResult result) {
        StringBuilder builder = new StringBuilder();
        builder.append("CSV导入").append(domain)
                .append(": file=").append(safeFileName(fileName))
                .append(", total=").append(result.totalRows())
                .append(", success=").append(result.successRows())
                .append(", failed=").append(result.failedRows().size());
        if (!result.failedRows().isEmpty()) {
            builder.append(", firstError=line ")
                    .append(result.failedRows().get(0).line())
                    .append(": ").append(result.failedRows().get(0).reason());
        }
        return builder.toString();
    }

    private void audit(@Nullable UUID operatorId, String action, String detail) {
        if (operatorId == null) {
            return;
        }
        try {
            auditLogService.log(operatorId, action, "import", null, detail, null);
        } catch (Exception e) {
            // 审计失败不影响导入结果
            log.warn("导入审计日志写入失败: {}", e.getMessage());
        }
    }

    private String safeFileName(@Nullable String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "unknown.csv";
        }
        return fileName.length() > 100 ? fileName.substring(0, 100) : fileName;
    }

    /**
     * 解析结果
     */
    private record ParseResult(Map<String, Integer> headerIndex, List<CsvSupport.CsvRecord> records) {
    }
}
