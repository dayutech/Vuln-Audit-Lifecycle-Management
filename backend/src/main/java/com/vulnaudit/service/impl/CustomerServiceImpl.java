package com.vulnaudit.service.impl;

import com.vulnaudit.dto.CustomerDetailResponse;
import com.vulnaudit.dto.CustomerRequest;
import com.vulnaudit.dto.CustomerResponse;
import com.vulnaudit.dto.CustomerSummaryResponse;
import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.AssetProjectLink;
import com.vulnaudit.entity.Customer;
import com.vulnaudit.entity.Project;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.AssetProjectLinkRepository;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.ProjectPhaseRepository;
import com.vulnaudit.repository.UserRepository;
import com.vulnaudit.repository.VulnerabilityRepository;
import com.vulnaudit.repository.CustomerRepository;
import com.vulnaudit.repository.ProjectRepository;
import com.vulnaudit.service.CustomerService;
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
import java.util.Map;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 客户服务实现（t68）
 *
 * <p>业务规则：
 * <ul>
 *   <li>{@code code} 全局唯一（重复 → 409 并回显冲突码）；</li>
 *   <li>状态仅 {@code active}/{@code inactive}；</li>
 *   <li>删除保护：客户下仍有项目 → 409，消息带项目数。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerServiceImpl implements CustomerService {

    private static final List<String> CUSTOMER_STATUSES = List.of("active", "inactive");

    /** 客户详情聚合里 recentAssets 的条数上限（t76） */
    private static final int RECENT_ASSET_LIMIT = 10;

    private final CustomerRepository customerRepository;
    private final ProjectRepository projectRepository;
    private final AssetProjectLinkRepository assetProjectLinkRepository;
    private final ProjectPhaseRepository projectPhaseRepository;
    private final AssetRepository assetRepository;
    private final VulnerabilityRepository vulnerabilityRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> findAll(int page, int size, @Nullable String keyword,
                                                  @Nullable String status, @Nullable String level) {
        PageRequest pageable = PageRequest.of(Paging.page(page), Paging.size(size),
                Sort.by(Sort.Direction.DESC, "createdAt"));

        Specification<Customer> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            if (StringUtils.hasText(keyword)) {
                String like = "%" + keyword.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("code")), like),
                        cb.like(cb.lower(root.get("name")), like),
                        cb.like(cb.lower(root.get("industry")), like)));
            }
            if (StringUtils.hasText(status)) {
                predicates.add(cb.equal(root.get("status"), status.trim().toLowerCase(Locale.ROOT)));
            }
            if (StringUtils.hasText(level)) {
                predicates.add(cb.equal(root.get("level"), level.trim()));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        Page<Customer> result = customerRepository.findAll(spec, pageable);
        return PageResponse.<CustomerResponse>builder()
                .content(result.getContent().stream().map(this::toResponse).toList())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .currentPage(result.getNumber())
                .pageSize(result.getSize())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerDetailResponse findDetail(@Nullable UUID id) {
        Customer customer = requireCustomer(id);
        long projectCount = projectRepository.findByCustomerId(customer.getId()).size();
        long activeProjectCount = projectRepository
                .findByCustomerIdAndStatus(customer.getId(), "ACTIVE").size();
        long assetCount = assetProjectLinkRepository.findByCustomerId(customer.getId()).stream()
                .map(link -> link.getAssetId())
                .filter(Objects::nonNull)
                .distinct()
                .count();
        return new CustomerDetailResponse(toResponse(customer), projectCount, activeProjectCount, assetCount);
    }

    /**
     * 客户详情聚合（t76）
     *
     * <p>查询次数固定、与项目数/资产数无关（无 N+1）：
     * <ol>
     *   <li>客户本体 1 次；</li>
     *   <li>项目列表 1 次；</li>
     *   <li>期次数 group by 1 次；项目级资产数 group by 1 次；</li>
     *   <li>客户下挂靠 1 次（同时用于 stats.assetCount 与 recentAssets，避免重复查询）；</li>
     *   <li>相关资产 1 次、项目名 1 次、经理名 1 次、漏洞数 1 次。</li>
     * </ol>
     *
     * <p><b>可见性差异</b>：{@code assetCount / vulnerabilityCount / recentAssets} 按
     * {@code visibleAssetIds} 收敛（资产是权限边界）；{@code projects} 与 {@code phaseCount /
     * assetCount（项目级）} 保持全量口径（客户/项目是组织维度，读权限对全员开放）。
     */
    @Override
    @Transactional(readOnly = true)
    public CustomerSummaryResponse findSummary(@Nullable UUID id, @Nullable java.util.Set<UUID> visibleAssetIds) {
        Customer customer = requireCustomer(id);

        List<Project> projects = projectRepository.findByCustomerId(customer.getId());
        List<UUID> projectIds = projects.stream().map(Project::getId).toList();

        // 期次数 / 项目级资产数：各一次 group by（禁止逐项目查询）
        Map<UUID, Long> phaseCounts = projectIds.isEmpty() ? Map.of()
                : toCountMap(projectPhaseRepository.countGroupedByProjectIds(projectIds));
        Map<UUID, Long> projectAssetCounts = projectIds.isEmpty() ? Map.of()
                : toCountMap(assetProjectLinkRepository.countAssetsGroupedByProjectIds(projectIds));

        // 客户下的挂靠（一次查询既算 stats 也用于 recentAssets）
        List<AssetProjectLink> customerLinks =
                assetProjectLinkRepository.findByCustomerIdOrderByLinkedAtDesc(customer.getId());
        List<UUID> allAssetIds = customerLinks.stream().map(AssetProjectLink::getAssetId)
                .filter(Objects::nonNull).distinct().toList();
        List<UUID> visibleAssetIdsOfCustomer = visibleAssetIds == null
                ? allAssetIds
                : allAssetIds.stream().filter(visibleAssetIds::contains).toList();

        // 项目名映射（recentAssets 的 projectNames 只取本客户下的项目）
        Map<UUID, String> projectNameById = new HashMap<>();
        projects.forEach(project -> projectNameById.put(project.getId(), project.getName()));

        // 最近 10 个可见资产（客户挂靠已按 linkedAt 倒序，去重后取前 10）
        List<UUID> recentAssetIds = visibleAssetIdsOfCustomer.stream().limit(RECENT_ASSET_LIMIT).toList();
        Map<UUID, Asset> assetById = recentAssetIds.isEmpty() ? Map.of()
                : assetRepository.findAllById(recentAssetIds).stream()
                .collect(Collectors.toMap(Asset::getId, Function.identity()));

        // 每个资产在本客户下的项目名（从已取到的 customerLinks 过滤，不再回查）
        Map<UUID, List<String>> projectNamesByAsset = new LinkedHashMap<>();
        for (AssetProjectLink link : customerLinks) {
            if (!assetById.containsKey(link.getAssetId())) {
                continue;
            }
            String projectName = projectNameById.get(link.getProjectId());
            if (projectName != null) {
                projectNamesByAsset.computeIfAbsent(link.getAssetId(), key -> new ArrayList<>())
                        .add(projectName);
            }
        }

        List<CustomerSummaryResponse.RecentAsset> recentAssets = recentAssetIds.stream()
                .map(assetById::get)
                .filter(Objects::nonNull)
                .map(asset -> new CustomerSummaryResponse.RecentAsset(
                        asset.getId(), asset.getCode(), asset.getName(),
                        asset.getTargetType(), asset.getStatus(),
                        projectNamesByAsset.getOrDefault(asset.getId(), List.of())))
                .toList();

        // 漏洞数：按可见资产收敛（资产不可见 → 其漏洞也不该被计入）
        long vulnerabilityCount = visibleAssetIdsOfCustomer.isEmpty() ? 0L
                : vulnerabilityRepository.countByAssetIdIn(visibleAssetIdsOfCustomer);

        Map<UUID, String> managerNames = resolveUserNames(
                projects.stream().map(Project::getManagerId).filter(Objects::nonNull).distinct().toList());

        List<CustomerSummaryResponse.ProjectSummary> projectSummaries = projects.stream()
                .sorted(Comparator.comparing(Project::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(project -> new CustomerSummaryResponse.ProjectSummary(
                        project.getId(), project.getCode(), project.getName(),
                        project.getType(), project.getStatus(), project.getIsPhased(),
                        phaseCounts.getOrDefault(project.getId(), 0L),
                        projectAssetCounts.getOrDefault(project.getId(), 0L),
                        managerNames.get(project.getManagerId())))
                .toList();

        CustomerSummaryResponse.Stats stats = new CustomerSummaryResponse.Stats(
                projects.size(),
                projects.stream().filter(project -> "ACTIVE".equals(project.getStatus())).count(),
                projects.stream().filter(project -> Boolean.TRUE.equals(project.getIsPhased())).count(),
                visibleAssetIdsOfCustomer.size(),
                vulnerabilityCount);

        log.debug("客户详情聚合: customerId={}, projects={}, visibleAssets={}, recentAssets={}",
                customer.getId(), projects.size(), visibleAssetIdsOfCustomer.size(), recentAssets.size());

        return new CustomerSummaryResponse(toResponse(customer), stats, projectSummaries, recentAssets);
    }

    private static Map<UUID, Long> toCountMap(List<Object[]> rows) {
        Map<UUID, Long> result = new HashMap<>();
        for (Object[] row : rows) {
            if (row[0] instanceof UUID key && row[1] instanceof Number count) {
                result.put(key, count.longValue());
            }
        }
        return result;
    }

    /** 一次批量取用户显示名（经理名），不逐项目查询 */
    private Map<UUID, String> resolveUserNames(List<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> names = new HashMap<>();
        userRepository.findAllById(userIds).forEach(user ->
                names.put(user.getId(),
                        StringUtils.hasText(user.getFullName()) ? user.getFullName() : user.getUsername()));
        return names;
    }

    @Override
    @Transactional
    public CustomerResponse create(CustomerRequest request, @Nullable UUID operatorId) {
        String code = request.code().trim();
        if (customerRepository.existsByCode(code)) {
            throw new BusinessException(ErrorCode.CUSTOMER_CODE_EXISTS, "客户编码已存在: " + code);
        }
        Customer customer = Customer.builder()
                .code(code)
                .name(request.name().trim())
                .industry(request.industry())
                .level(request.level())
                .contactName(request.contactName())
                .contactPhone(request.contactPhone())
                .contactEmail(request.contactEmail())
                .ownerId(request.ownerId())
                .status(normalizeStatus(request.status(), "active"))
                .description(request.description())
                .createdBy(operatorId)
                .build();
        Customer saved = customerRepository.save(Objects.requireNonNull(customer));
        log.info("创建客户: id={}, code={}", saved.getId(), saved.getCode());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public CustomerResponse update(@Nullable UUID id, CustomerRequest request) {
        Customer customer = requireCustomer(id);
        String code = request.code().trim();
        if (!code.equals(customer.getCode())) {
            customerRepository.findByCode(code).ifPresent(existing -> {
                if (!existing.getId().equals(customer.getId())) {
                    throw new BusinessException(ErrorCode.CUSTOMER_CODE_EXISTS, "客户编码已存在: " + code);
                }
            });
        }
        customer.setCode(code);
        customer.setName(request.name().trim());
        customer.setIndustry(request.industry());
        customer.setLevel(request.level());
        customer.setContactName(request.contactName());
        customer.setContactPhone(request.contactPhone());
        customer.setContactEmail(request.contactEmail());
        customer.setOwnerId(request.ownerId());
        customer.setDescription(request.description());
        if (StringUtils.hasText(request.status())) {
            customer.setStatus(normalizeStatus(request.status(), customer.getStatus()));
        }
        return toResponse(customerRepository.save(customer));
    }

    @Override
    @Transactional
    public CustomerResponse changeStatus(@Nullable UUID id, @Nullable String status) {
        Customer customer = requireCustomer(id);
        if (!StringUtils.hasText(status)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "状态不能为空（active / inactive）");
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        if (!CUSTOMER_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "客户状态非法: " + status + "，可选值 active / inactive");
        }
        customer.setStatus(normalized);
        return toResponse(customerRepository.save(customer));
    }

    @Override
    @Transactional
    public void delete(@Nullable UUID id) {
        Customer customer = requireCustomer(id);
        List<Project> projects = projectRepository.findByCustomerId(customer.getId());
        if (!projects.isEmpty()) {
            throw new BusinessException(ErrorCode.CUSTOMER_HAS_PROJECTS,
                    "该客户下仍有 " + projects.size() + " 个项目，无法删除");
        }
        customerRepository.delete(customer);
        log.info("删除客户: id={}, code={}", customer.getId(), customer.getCode());
    }

    // ------------------------------------------------------------------ 内部

    private Customer requireCustomer(@Nullable UUID id) {
        if (id == null) {
            throw new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND);
        }
        return customerRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "客户不存在: " + id));
    }

    private String normalizeStatus(@Nullable String status, String fallback) {
        if (!StringUtils.hasText(status)) {
            return fallback;
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        if (!CUSTOMER_STATUSES.contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "客户状态非法: " + status + "，可选值 active / inactive");
        }
        return normalized;
    }

    private CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getCode(),
                customer.getName(),
                customer.getIndustry(),
                customer.getLevel(),
                customer.getContactName(),
                customer.getContactPhone(),
                customer.getContactEmail(),
                customer.getOwnerId(),
                customer.getStatus(),
                customer.getDescription(),
                customer.getCreatedBy(),
                customer.getCreatedAt(),
                customer.getUpdatedAt());
    }
}
