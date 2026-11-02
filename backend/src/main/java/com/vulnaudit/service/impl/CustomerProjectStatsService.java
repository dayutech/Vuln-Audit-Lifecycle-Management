package com.vulnaudit.service.impl;

import com.vulnaudit.dto.CustomerStatsResponse;
import com.vulnaudit.dto.ProjectStatsResponse;
import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.AssetProjectLink;
import com.vulnaudit.entity.Customer;
import com.vulnaudit.entity.Project;
import com.vulnaudit.entity.ProjectPhase;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.AssetProjectLinkRepository;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.CustomerRepository;
import com.vulnaudit.repository.ProjectPhaseRepository;
import com.vulnaudit.repository.ProjectRepository;
import com.vulnaudit.repository.TaskRepository;
import com.vulnaudit.repository.VulnerabilityRepository;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 客户/项目聚合统计（t70）
 *
 * <p><b>计数口径差异</b>（写入 README §36 与验收报告 §2.10）：
 * <ul>
 *   <li>项目/期次类计数 = 组织结构事实，<b>不受资产可见性影响</b>（项目对所有登录用户可见）；</li>
 *   <li>资产/漏洞/任务类计数 = 按 {@code visibleAssetIds} <b>收敛</b>：非管理层只统计自己可见的资产
 *       及其漏洞/任务，管理层（null）全量。</li>
 * </ul>
 *
 * <p>实现要点：先用挂靠表取「客户/项目 → 资产集合」，再与可见集合求交，之后一律按交集做
 * {@code in (...)} 统计，避免逐资产 N 次查询。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerProjectStatsService {

    private final CustomerRepository customerRepository;
    private final ProjectRepository projectRepository;
    private final ProjectPhaseRepository projectPhaseRepository;
    private final AssetProjectLinkRepository linkRepository;
    private final AssetRepository assetRepository;
    private final VulnerabilityRepository vulnerabilityRepository;
    private final TaskRepository taskRepository;

    // ------------------------------------------------------------------ 客户统计

    @Transactional(readOnly = true)
    public CustomerStatsResponse customerStats(@Nullable UUID customerId,
                                               @Nullable Set<UUID> visibleAssetIds) {
        Customer customer = customerRepository.findById(requireId(customerId, ErrorCode.CUSTOMER_NOT_FOUND))
                .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND, "客户不存在: " + customerId));

        List<Project> projects = projectRepository.findByCustomerId(customer.getId());

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (String status : List.of("DRAFT", "ACTIVE", "PAUSED", "CLOSED")) {
            byStatus.put(status, 0L);
        }
        for (Project project : projects) {
            String status = project.getStatus() == null ? "DRAFT" : project.getStatus();
            byStatus.merge(status, 1L, Long::sum);
        }

        List<AssetProjectLink> links = linkRepository.findByCustomerId(customer.getId());
        Set<UUID> scopedAssets = scopedAssetIds(links, visibleAssetIds);
        // assetCount 也按可见范围收敛（只数可见资产的挂靠记录），与 distinctAssetCount 同一口径
        long scopedLinkCount = links.stream()
                .filter(link -> link.getAssetId() != null)
                .filter(link -> visibleAssetIds == null || visibleAssetIds.contains(link.getAssetId()))
                .count();

        return new CustomerStatsResponse(
                projects.size(),
                byStatus.getOrDefault("ACTIVE", 0L),
                projects.stream().filter(p -> Boolean.TRUE.equals(p.getIsPhased())).count(),
                scopedLinkCount,
                scopedAssets.size(),
                countVulnerabilities(scopedAssets),
                byStatus);
    }

    // ------------------------------------------------------------------ 项目统计

    @Transactional(readOnly = true)
    public ProjectStatsResponse projectStats(@Nullable UUID projectId,
                                             @Nullable Set<UUID> visibleAssetIds) {
        Project project = projectRepository.findById(requireId(projectId, ErrorCode.PROJECT_NOT_FOUND))
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND, "项目不存在: " + projectId));

        List<ProjectPhase> phases = projectPhaseRepository.findByProjectIdOrderByPhaseNo(project.getId());
        List<AssetProjectLink> links = linkRepository.findByProjectId(project.getId());
        Set<UUID> scopedAssets = scopedAssetIds(links, visibleAssetIds);

        // 按期次的资产分布（只统计可见范围内的资产）
        Map<UUID, Set<UUID>> assetsByPhase = new HashMap<>();
        for (AssetProjectLink link : links) {
            if (link.getPhaseId() == null || link.getAssetId() == null) {
                continue;
            }
            if (visibleAssetIds != null && !visibleAssetIds.contains(link.getAssetId())) {
                continue;
            }
            assetsByPhase.computeIfAbsent(link.getPhaseId(), key -> new LinkedHashSet<>()).add(link.getAssetId());
        }
        List<CustomerStatsResponse.PhaseAssetCount> byPhase = phases.stream()
                .map(phase -> new CustomerStatsResponse.PhaseAssetCount(
                        phase.getId(),
                        phase.getName(),
                        assetsByPhase.getOrDefault(phase.getId(), Set.of()).size()))
                .toList();

        return new ProjectStatsResponse(
                phases.size(),
                scopedAssets.size(),
                countVulnerabilities(scopedAssets),
                countTasks(scopedAssets),
                byPhase);
    }

    // ------------------------------------------------------------------ 仪表盘分布

    @Transactional(readOnly = true)
    public List<ProjectStatsResponse.CustomerDistributionItem> customerDistribution(int topN,
                                                                                   @Nullable Set<UUID> visibleAssetIds) {
        int limit = normalizeTopN(topN);
        List<Customer> customers = customerRepository.findAll();
        List<AssetProjectLink> allLinks = linkRepository.findAll();

        Map<UUID, Set<UUID>> assetsByCustomer = new HashMap<>();
        for (AssetProjectLink link : allLinks) {
            if (link.getCustomerId() == null || link.getAssetId() == null) {
                continue;
            }
            if (visibleAssetIds != null && !visibleAssetIds.contains(link.getAssetId())) {
                continue;
            }
            assetsByCustomer.computeIfAbsent(link.getCustomerId(), key -> new LinkedHashSet<>())
                    .add(link.getAssetId());
        }

        Map<UUID, Long> projectCounts = projectRepository.findAll().stream()
                .filter(p -> p.getCustomerId() != null)
                .collect(Collectors.groupingBy(Project::getCustomerId, Collectors.counting()));

        List<ProjectStatsResponse.CustomerDistributionItem> items = new ArrayList<>();
        for (Customer customer : customers) {
            items.add(new ProjectStatsResponse.CustomerDistributionItem(
                    customer.getId(),
                    customer.getCode(),
                    customer.getName(),
                    assetsByCustomer.getOrDefault(customer.getId(), Set.of()).size(),
                    projectCounts.getOrDefault(customer.getId(), 0L)));
        }
        items.sort(Comparator
                .comparingLong(ProjectStatsResponse.CustomerDistributionItem::assetCount).reversed()
                .thenComparing(ProjectStatsResponse.CustomerDistributionItem::customerCode,
                        Comparator.nullsLast(Comparator.naturalOrder())));
        return items.stream().limit(limit).toList();
    }

    @Transactional(readOnly = true)
    public List<ProjectStatsResponse.ProjectDistributionItem> projectDistribution(@Nullable UUID customerId,
                                                                                 int topN,
                                                                                 @Nullable Set<UUID> visibleAssetIds) {
        int limit = normalizeTopN(topN);
        List<Project> projects = customerId == null
                ? projectRepository.findAll()
                : projectRepository.findByCustomerId(customerId);
        List<AssetProjectLink> links = customerId == null
                ? linkRepository.findAll()
                : linkRepository.findByCustomerId(customerId);

        Map<UUID, Set<UUID>> assetsByProject = new HashMap<>();
        for (AssetProjectLink link : links) {
            if (link.getProjectId() == null || link.getAssetId() == null) {
                continue;
            }
            if (visibleAssetIds != null && !visibleAssetIds.contains(link.getAssetId())) {
                continue;
            }
            assetsByProject.computeIfAbsent(link.getProjectId(), key -> new LinkedHashSet<>())
                    .add(link.getAssetId());
        }

        List<ProjectStatsResponse.ProjectDistributionItem> items = projects.stream()
                .map(project -> new ProjectStatsResponse.ProjectDistributionItem(
                        project.getId(),
                        project.getCode(),
                        project.getName(),
                        project.getCustomerId(),
                        project.getStatus(),
                        assetsByProject.getOrDefault(project.getId(), Set.of()).size()))
                .sorted(Comparator
                        .comparingLong(ProjectStatsResponse.ProjectDistributionItem::assetCount).reversed()
                        .thenComparing(ProjectStatsResponse.ProjectDistributionItem::projectCode,
                                Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(limit)
                .toList();
        return items;
    }

    // ------------------------------------------------------------------ 内部

    private Set<UUID> projectIdSet(List<Project> projects) {
        return projects.stream().map(Project::getId).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    /** 挂靠资产 ∩ 可见资产（visibleAssetIds == null 表示不限制） */
    private Set<UUID> scopedAssetIds(List<AssetProjectLink> links, @Nullable Set<UUID> visibleAssetIds) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (AssetProjectLink link : links) {
            UUID assetId = link.getAssetId();
            if (assetId == null) {
                continue;
            }
            if (visibleAssetIds != null && !visibleAssetIds.contains(assetId)) {
                continue;
            }
            ids.add(assetId);
        }
        return ids;
    }

    private long countVulnerabilities(Set<UUID> assetIds) {
        if (assetIds.isEmpty()) {
            return 0;
        }
        return vulnerabilityRepository.count((root, query, cb) -> root.get("assetId").in(assetIds));
    }

    private long countTasks(Set<UUID> assetIds) {
        if (assetIds.isEmpty()) {
            return 0;
        }
        return taskRepository.count((root, query, cb) -> root.get("assetId").in(assetIds));
    }

    private UUID requireId(@Nullable UUID id, ErrorCode code) {
        if (id == null) {
            throw new BusinessException(code);
        }
        return id;
    }

    private int normalizeTopN(int topN) {
        if (topN <= 0) {
            return 10;
        }
        return Math.min(topN, 100);
    }

    /** 供统计复用：客户可见范围下的去重资产数（避免重复实现口径） */
    @Transactional(readOnly = true)
    public long distinctAssetCountForCustomer(UUID customerId, @Nullable Set<UUID> visibleAssetIds) {
        return scopedAssetIds(linkRepository.findByCustomerId(customerId), visibleAssetIds).size();
    }

    /** 供测试/诊断：确认可见范围过滤与资产总数差异 */
    @Transactional(readOnly = true)
    public long totalAssetCountForCustomer(UUID customerId) {
        List<AssetProjectLink> links = linkRepository.findByCustomerId(customerId);
        return links.stream().map(AssetProjectLink::getAssetId).filter(Objects::nonNull).distinct().count();
    }
}
