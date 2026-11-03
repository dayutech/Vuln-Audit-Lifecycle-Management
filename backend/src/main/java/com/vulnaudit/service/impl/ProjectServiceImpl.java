package com.vulnaudit.service.impl;

import com.vulnaudit.dto.CustomerBrief;
import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.dto.PhaseRequest;
import com.vulnaudit.dto.PhaseResponse;
import com.vulnaudit.dto.ProjectDetailResponse;
import com.vulnaudit.dto.ProjectRequest;
import com.vulnaudit.dto.ProjectSummaryItem;
import com.vulnaudit.dto.ProjectResponse;
import com.vulnaudit.entity.AssetProjectLink;
import com.vulnaudit.entity.Customer;
import com.vulnaudit.entity.Project;
import com.vulnaudit.entity.ProjectPhase;
import com.vulnaudit.entity.User;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.AssetProjectLinkRepository;
import com.vulnaudit.repository.CustomerRepository;
import com.vulnaudit.repository.ProjectPhaseRepository;
import com.vulnaudit.repository.ProjectRepository;
import com.vulnaudit.repository.UserRepository;
import com.vulnaudit.service.ProjectService;
import com.vulnaudit.util.Paging;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * 项目与期次服务实现（t68）
 *
 * <p>状态机（{@link #TRANSITIONS}）：
 * <pre>
 *   DRAFT ──▶ ACTIVE ──▶ PAUSED ──▶ ACTIVE
 *                │           │
 *                └──▶ CLOSED ◀┘        CLOSED 为终态
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    /** 项目状态机：当前状态 → 允许的下一步 */
    private static final Map<String, Set<String>> TRANSITIONS = Map.of(
            "DRAFT", Set.of("ACTIVE"),
            "ACTIVE", Set.of("PAUSED", "CLOSED"),
            "PAUSED", Set.of("ACTIVE", "CLOSED"),
            "CLOSED", Set.of());

    private static final List<String> PROJECT_TYPES = List.of("PENTEST", "REAUDIT", "CONSULTING", "TRAINING");

    private final ProjectRepository projectRepository;
    private final ProjectPhaseRepository projectPhaseRepository;
    private final CustomerRepository customerRepository;
    private final AssetProjectLinkRepository assetProjectLinkRepository;
    private final UserRepository userRepository;
    private final com.vulnaudit.repository.TaskRepository taskRepository;

    // ------------------------------------------------------------------ 项目查询

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProjectResponse> findAll(int page, int size, @Nullable UUID customerId,
                                                 @Nullable String status, @Nullable String type,
                                                 @Nullable String keyword) {
        PageRequest pageable = PageRequest.of(Paging.page(page), Paging.size(size),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Specification<Project> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (StringUtils.hasText(status)) {
                predicates.add(cb.equal(root.get("status"), normalizeStatusFilter(status)));
            }
            if (StringUtils.hasText(type)) {
                predicates.add(cb.equal(root.get("type"), type.trim().toUpperCase(Locale.ROOT)));
            }
            if (StringUtils.hasText(keyword)) {
                String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("code")), like),
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("contractNo")), like)));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        Page<Project> result = projectRepository.findAll(spec, pageable);
        return PageResponse.<ProjectResponse>builder()
                .content(result.getContent().stream().map(this::toResponse).toList())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .currentPage(result.getNumber())
                .pageSize(result.getSize())
                .build();
    }

    /**
     * 分页查询项目（带聚合计数，t82）
     *
     * <p><b>为什么要有这个接口</b>：项目列表此前只返回项目本体，前端渲染「期次数/资产数」只能
     * 对当页每个项目再发 {@code GET /projects/{id}}（上限 20 个并发）——典型 N+1。
     * 客户详情已在 t76 用聚合解决，这里把同一套做法搬到项目列表。
     *
     * <p><b>查询次数（固定，与页大小无关）</b>：
     * <ol>
     *   <li>项目分页 1 次（+ count 1 次）；</li>
     *   <li>期次数 group by 1 次；</li>
     *   <li>资产数 group by 1 次（带可见范围条件）；</li>
     *   <li>未完成任务数 group by 1 次；</li>
     *   <li>客户名 findAllById 1 次、经理名 findAllById 1 次。</li>
     * </ol>
     *
     * <p><b>口径差异（勿混用）</b>：{@code phaseCount} 是组织结构事实，**不收敛**；
     * {@code assetCount} 与 {@code openTaskCount} 按 {@code visibleAssetIds} **收敛**
     * （资产是权限边界，与 GET /assets、GET /customers/{id}/summary 一致）。
     *
     * <p><b>assetCount 排序</b>：计数是聚合结果，无法在库里直接 order by，因此
     * {@code sort=assetCount} 时先取筛选命中的全部项目 ID（组织级数据量可控）、算完计数后在内存排序再切页；
     * 默认 {@code createdAt} 排序仍走数据库分页。
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProjectSummaryItem> findSummary(int page, int size, @Nullable UUID customerId,
                                                        @Nullable String status, @Nullable String type,
                                                        @Nullable String keyword, @Nullable String sort,
                                                        @Nullable Set<UUID> visibleAssetIds) {
        int safePage = Paging.page(page);
        int safeSize = Paging.size(size);
        boolean sortByAssetCount = "assetCount".equalsIgnoreCase(
                sort == null ? "" : sort.trim());

        Specification<Project> spec = projectFilter(customerId, status, type, keyword);

        Page<Project> projectPage;
        List<Project> pageContent;
        long totalElements;
        int totalPages;
        if (sortByAssetCount) {
            // 聚合字段无法在 SQL 里 order by：取全部命中 → 算计数 → 内存排序 → 切页
            List<Project> all = projectRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt"));
            Map<UUID, Long> assetCounts = loadAssetCounts(
                    all.stream().map(Project::getId).toList(), visibleAssetIds);
            all = all.stream()
                    .sorted(Comparator.comparingLong(
                            (Project project) -> assetCounts.getOrDefault(project.getId(), 0L)).reversed()
                            .thenComparing(Project::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();
            totalElements = all.size();
            totalPages = (int) Math.ceil(totalElements / (double) safeSize);
            int from = Math.min(safePage * safeSize, all.size());
            pageContent = all.subList(from, Math.min(from + safeSize, all.size()));
            projectPage = null;
        } else {
            // createdAt 可能同毫秒 → 追加 id 作为 tiebreak，保证分页稳定（两页不重复、不漏项）
            PageRequest pageable = PageRequest.of(safePage, safeSize,
                    Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by(Sort.Direction.ASC, "id")));
            projectPage = projectRepository.findAll(spec, pageable);
            pageContent = projectPage.getContent();
            totalElements = projectPage.getTotalElements();
            totalPages = projectPage.getTotalPages();
        }

        List<ProjectSummaryItem> content = buildSummaries(pageContent, visibleAssetIds);

        return PageResponse.<ProjectSummaryItem>builder()
                .content(content)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .currentPage(safePage)
                .pageSize(safeSize)
                .build();
    }

    /** 项目列表筛选条件（findAll 与 findSummary 共用，避免两处口径漂移） */
    private Specification<Project> projectFilter(@Nullable UUID customerId, @Nullable String status,
                                                 @Nullable String type, @Nullable String keyword) {
        return (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (customerId != null) {
                predicates.add(cb.equal(root.get("customerId"), customerId));
            }
            if (StringUtils.hasText(status)) {
                predicates.add(cb.equal(root.get("status"), normalizeStatusFilter(status)));
            }
            if (StringUtils.hasText(type)) {
                predicates.add(cb.equal(root.get("type"), type.trim().toUpperCase(Locale.ROOT)));
            }
            if (StringUtils.hasText(keyword)) {
                String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("code")), like),
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("contractNo")), like)));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
    }

    /** 可见范围内的项目资产数（一次 group by；空可见集合直接全 0，避免非法 in ()） */
    private Map<UUID, Long> loadAssetCounts(List<UUID> projectIds, @Nullable Set<UUID> visibleAssetIds) {
        if (projectIds.isEmpty() || (visibleAssetIds != null && visibleAssetIds.isEmpty())) {
            return Map.of();
        }
        return toCountMap(assetProjectLinkRepository.countVisibleAssetsGroupedByProjectIds(
                projectIds, visibleAssetIds));
    }

    /** 组装配聚合计数的项目列表（此方法内查询次数固定，不随列表长度变化） */
    private List<ProjectSummaryItem> buildSummaries(List<Project> projects, @Nullable Set<UUID> visibleAssetIds) {
        if (projects.isEmpty()) {
            return List.of();
        }
        List<UUID> projectIds = projects.stream().map(Project::getId).toList();

        Map<UUID, Long> phaseCounts = toCountMap(
                projectPhaseRepository.countGroupedByProjectIds(projectIds));
        Map<UUID, Long> assetCounts = loadAssetCounts(projectIds, visibleAssetIds);
        Map<UUID, Long> openTaskCounts = toCountMap(
                taskRepository.countOpenTasksGroupedByProjectIds(projectIds));

        // 客户名 / 经理名：各一次批量取名（不做 per-project findById）
        Map<UUID, String> customerNames = new HashMap<>();
        customerRepository.findAllById(projects.stream().map(Project::getCustomerId)
                        .filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet()))
                .forEach(customer -> customerNames.put(customer.getId(), customer.getName()));

        Map<UUID, String> managerNames = new HashMap<>();
        userRepository.findAllById(projects.stream().map(Project::getManagerId)
                        .filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet()))
                .forEach(user -> managerNames.put(user.getId(),
                        StringUtils.hasText(user.getFullName()) ? user.getFullName() : user.getUsername()));

        return projects.stream()
                .map(project -> new ProjectSummaryItem(
                        project.getId(),
                        project.getCode(),
                        project.getName(),
                        project.getCustomerId(),
                        customerNames.get(project.getCustomerId()),
                        project.getType(),
                        project.getStatus(),
                        project.getManagerId(),
                        managerNames.get(project.getManagerId()),
                        project.getStartDate(),
                        project.getEndDate(),
                        project.getIsPhased(),
                        project.getContractNo(),
                        project.getDescription(),
                        phaseCounts.getOrDefault(project.getId(), 0L),
                        assetCounts.getOrDefault(project.getId(), 0L),
                        openTaskCounts.getOrDefault(project.getId(), 0L),
                        project.getCreatedBy(),
                        project.getCreatedAt(),
                        project.getUpdatedAt()))
                .toList();
    }

    /** group by 结果 → Map&lt;projectId, count&gt; */
    private static Map<UUID, Long> toCountMap(List<Object[]> rows) {
        Map<UUID, Long> result = new HashMap<>();
        for (Object[] row : rows) {
            if (row[0] instanceof UUID key && row[1] instanceof Number count) {
                result.put(key, count.longValue());
            }
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public ProjectDetailResponse findDetail(@Nullable UUID id) {        Project project = requireProject(id);
        CustomerBrief brief = customerBrief(project.getCustomerId());
        List<PhaseResponse> phases = projectPhaseRepository
                .findByProjectIdOrderByPhaseNo(project.getId()).stream()
                .map(this::toPhaseResponse)
                .toList();
        long assetCount = assetProjectLinkRepository.findByProjectId(project.getId()).stream()
                .map(AssetProjectLink::getAssetId)
                .filter(Objects::nonNull)
                .distinct()
                .count();
        return new ProjectDetailResponse(toResponse(project), brief, phases, assetCount);
    }

    @Nullable
    @Override
    public UUID findManagerId(@Nullable UUID projectId) {
        if (projectId == null) {
            return null;
        }
        return projectRepository.findById(projectId).map(Project::getManagerId).orElse(null);
    }

    // ------------------------------------------------------------------ 项目写

    @Override
    @Transactional
    public ProjectResponse create(ProjectRequest request, @Nullable UUID operatorId) {
        String code = request.code().trim();
        if (projectRepository.existsByCode(code)) {
            throw new BusinessException(ErrorCode.PROJECT_CODE_EXISTS, "项目编码已存在: " + code);
        }
        requireCustomer(request.customerId());
        if (request.managerId() != null) {
            requireUser(request.managerId());
        }
        Project project = Project.builder()
                .code(code)
                .customerId(request.customerId())
                .name(request.name().trim())
                .type(normalizeType(request.type(), "PENTEST"))
                .status("DRAFT")
                .managerId(request.managerId())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .isPhased(Boolean.TRUE.equals(request.isPhased()))
                .contractNo(request.contractNo())
                .description(request.description())
                .createdBy(operatorId)
                .build();
        Project saved = projectRepository.save(Objects.requireNonNull(project));
        log.info("创建项目: id={}, code={}, customerId={}", saved.getId(), saved.getCode(), saved.getCustomerId());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public ProjectResponse update(@Nullable UUID id, ProjectRequest request) {
        Project project = requireProject(id);
        String code = request.code().trim();
        if (!code.equals(project.getCode())) {
            projectRepository.findByCode(code).ifPresent(existing -> {
                if (!existing.getId().equals(project.getId())) {
                    throw new BusinessException(ErrorCode.PROJECT_CODE_EXISTS, "项目编码已存在: " + code);
                }
            });
        }
        requireCustomer(request.customerId());
        if (request.managerId() != null) {
            requireUser(request.managerId());
        }

        boolean newPhased = request.isPhased() == null
                ? Boolean.TRUE.equals(project.getIsPhased())
                : request.isPhased();
        // isPhased: true → false 的约束：已有期次且存在按期次挂靠 → 409
        if (Boolean.TRUE.equals(project.getIsPhased()) && !newPhased) {
            List<ProjectPhase> phases = projectPhaseRepository.findByProjectIdOrderByPhaseNo(project.getId());
            if (!phases.isEmpty()) {
                long phaseLinks = assetProjectLinkRepository.findByProjectId(project.getId()).stream()
                        .filter(link -> link.getPhaseId() != null)
                        .count();
                if (phaseLinks > 0) {
                    throw new BusinessException(ErrorCode.PROJECT_PHASED_HAS_LINKS,
                            "该项目已存在按期次挂靠的记录（" + phaseLinks + " 条），无法关闭分期");
                }
            }
        }

        project.setCode(code);
        project.setCustomerId(request.customerId());
        project.setName(request.name().trim());
        project.setType(normalizeType(request.type(), project.getType()));
        project.setManagerId(request.managerId());
        project.setStartDate(request.startDate());
        project.setEndDate(request.endDate());
        project.setIsPhased(newPhased);
        project.setContractNo(request.contractNo());
        project.setDescription(request.description());
        return toResponse(projectRepository.save(project));
    }

    @Override
    @Transactional
    public ProjectResponse changeStatus(@Nullable UUID id, @Nullable String status) {
        Project project = requireProject(id);
        if (!StringUtils.hasText(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "状态不能为空（DRAFT/ACTIVE/PAUSED/CLOSED）");
        }
        String target = normalizeStatusFilter(status);
        String current = project.getStatus();
        Set<String> allowed = TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(target)) {
            String next = allowed.isEmpty() ? "无（项目已关闭，不可再流转）" : String.join(" / ", allowed);
            throw new BusinessException(ErrorCode.PROJECT_INVALID_TRANSITION,
                    "项目状态不能从 " + current + " 流转到 " + target
                            + "；当前状态 " + current + " 允许的下一步: " + next);
        }
        project.setStatus(target);
        return toResponse(projectRepository.save(project));
    }

    @Override
    @Transactional
    public void delete(@Nullable UUID id) {
        Project project = requireProject(id);
        long linkCount = assetProjectLinkRepository.countByProjectId(project.getId());
        if (linkCount > 0) {
            throw new BusinessException(ErrorCode.PROJECT_HAS_ASSET_LINKS,
                    "该项目下仍有 " + linkCount + " 个资产挂靠，请先解绑");
        }
        List<ProjectPhase> phases = projectPhaseRepository.findByProjectIdOrderByPhaseNo(project.getId());
        if (!phases.isEmpty()) {
            projectPhaseRepository.deleteAll(phases);
        }
        projectRepository.delete(project);
        log.info("删除项目: id={}, code={}, 同时清理期次 {} 个", project.getId(), project.getCode(), phases.size());
    }

    // ------------------------------------------------------------------ 期次

    @Override
    @Transactional(readOnly = true)
    public List<PhaseResponse> listPhases(@Nullable UUID projectId) {
        requireProject(projectId);
        return projectPhaseRepository.findByProjectIdOrderByPhaseNo(projectId).stream()
                .map(this::toPhaseResponse)
                .toList();
    }

    @Override
    @Transactional
    public PhaseResponse createPhase(@Nullable UUID projectId, PhaseRequest request) {
        Project project = requireProject(projectId);
        if (!Boolean.TRUE.equals(project.getIsPhased())) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_PHASED,
                    "该项目未启用分期（isPhased=false），不能创建期次");
        }
        Integer phaseNo = request.phaseNo();
        if (phaseNo == null) {
            phaseNo = projectPhaseRepository.findByProjectIdOrderByPhaseNo(project.getId()).stream()
                    .map(ProjectPhase::getPhaseNo)
                    .filter(Objects::nonNull)
                    .max(Integer::compareTo)
                    .orElse(0) + 1;
        } else if (projectPhaseRepository.existsByProjectIdAndPhaseNo(project.getId(), phaseNo)) {
            throw new BusinessException(ErrorCode.PROJECT_PHASE_NO_EXISTS,
                    "该项目的期次号已存在: " + phaseNo);
        }
        ProjectPhase phase = ProjectPhase.builder()
                .projectId(project.getId())
                .phaseNo(phaseNo)
                .name(request.name().trim())
                .startDate(request.startDate())
                .endDate(request.endDate())
                .status(normalizePhaseStatus(request.status(), "PLANNED"))
                .build();
        ProjectPhase saved = projectPhaseRepository.save(Objects.requireNonNull(phase));
        log.info("创建项目期次: projectId={}, phaseId={}, phaseNo={}", project.getId(), saved.getId(), phaseNo);
        return toPhaseResponse(saved);
    }

    @Override
    @Transactional
    public PhaseResponse updatePhase(@Nullable UUID phaseId, PhaseRequest request) {
        ProjectPhase phase = requirePhase(phaseId);
        Integer phaseNo = request.phaseNo() == null ? phase.getPhaseNo() : request.phaseNo();
        if (!Objects.equals(phaseNo, phase.getPhaseNo())
                && phaseNo != null
                && projectPhaseRepository.existsByProjectIdAndPhaseNo(phase.getProjectId(), phaseNo)) {
            throw new BusinessException(ErrorCode.PROJECT_PHASE_NO_EXISTS,
                    "该项目的期次号已存在: " + phaseNo);
        }
        phase.setPhaseNo(phaseNo);
        phase.setName(request.name().trim());
        phase.setStartDate(request.startDate());
        phase.setEndDate(request.endDate());
        if (StringUtils.hasText(request.status())) {
            phase.setStatus(normalizePhaseStatus(request.status(), phase.getStatus()));
        }
        return toPhaseResponse(projectPhaseRepository.save(phase));
    }

    @Override
    @Transactional
    public void deletePhase(@Nullable UUID phaseId) {
        ProjectPhase phase = requirePhase(phaseId);
        long linkCount = assetProjectLinkRepository
                .findByProjectIdAndPhaseId(phase.getProjectId(), phase.getId()).size();
        if (linkCount > 0) {
            throw new BusinessException(ErrorCode.PROJECT_PHASE_HAS_ASSET_LINKS,
                    "该期次下仍有 " + linkCount + " 个资产挂靠，请先解绑");
        }
        projectPhaseRepository.delete(phase);
        log.info("删除项目期次: projectId={}, phaseId={}", phase.getProjectId(), phase.getId());
    }

    // ------------------------------------------------------------------ 内部

    private Project requireProject(@Nullable UUID id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
        }
        return projectRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "项目不存在: " + id));
    }

    private ProjectPhase requirePhase(@Nullable UUID id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.PROJECT_PHASE_NOT_FOUND);
        }
        return projectPhaseRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_PHASE_NOT_FOUND, "项目期次不存在: " + id));
    }

    private void requireCustomer(@Nullable UUID customerId) {
        if (customerId == null) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "所属客户不能为空");
        }
        customerRepository.findById(customerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "客户不存在: " + customerId));
    }

    private void requireUser(@Nullable UUID userId) {
        if (userId == null) {
            return;
        }
        userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND, "项目经理不存在: " + userId));
    }

    private String normalizeStatusFilter(String status) {
        String normalized = status.trim().toUpperCase(Locale.ROOT);
        if (!TRANSITIONS.containsKey(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "项目状态非法: " + status + "，可选值 DRAFT / ACTIVE / PAUSED / CLOSED");
        }
        return normalized;
    }

    private String normalizeType(@Nullable String type, String fallback) {
        if (!StringUtils.hasText(type)) {
            return fallback;
        }
        String normalized = type.trim().toUpperCase(Locale.ROOT);
        if (!PROJECT_TYPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "项目类型非法: " + type + "，可选值 " + String.join(" / ", PROJECT_TYPES));
        }
        return normalized;
    }

    private String normalizePhaseStatus(@Nullable String status, String fallback) {
        if (!StringUtils.hasText(status)) {
            return fallback;
        }
        return status.trim().toUpperCase(Locale.ROOT);
    }

    private CustomerBrief customerBrief(@Nullable UUID customerId) {
        if (customerId == null) {
            return null;
        }
        return customerRepository.findById(customerId)
                .map(c -> new CustomerBrief(c.getId(), c.getCode(), c.getName()))
                .orElse(null);
    }

    private ProjectResponse toResponse(Project project) {
        String customerName = project.getCustomerId() == null ? null
                : customerRepository.findById(project.getCustomerId()).map(Customer::getName).orElse(null);
        String managerName = project.getManagerId() == null ? null
                : userRepository.findById(project.getManagerId())
                        .map(User::getFullName)
                        .filter(StringUtils::hasText)
                        .orElseGet(() -> userRepository.findById(project.getManagerId())
                                .map(User::getUsername).orElse(null));
        return new ProjectResponse(
                project.getId(),
                project.getCode(),
                project.getCustomerId(),
                customerName,
                project.getName(),
                project.getType(),
                project.getStatus(),
                project.getManagerId(),
                managerName,
                project.getStartDate(),
                project.getEndDate(),
                project.getIsPhased(),
                project.getContractNo(),
                project.getDescription(),
                project.getCreatedBy(),
                project.getCreatedAt(),
                project.getUpdatedAt());
    }

    private PhaseResponse toPhaseResponse(ProjectPhase phase) {
        return new PhaseResponse(
                phase.getId(),
                phase.getProjectId(),
                phase.getPhaseNo(),
                phase.getName(),
                phase.getStartDate(),
                phase.getEndDate(),
                phase.getStatus(),
                phase.getCreatedAt(),
                phase.getUpdatedAt());
    }

    /** 供测试/诊断使用：状态机转移表只读视图 */
    public static Map<String, Set<String>> transitions() {
        return new LinkedHashMap<>(TRANSITIONS);
    }
}
