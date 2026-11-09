package com.vulnaudit.service.impl;

import com.vulnaudit.dto.AssetCreateRequest;
import com.vulnaudit.dto.AssetProjectLinkRequest;
import com.vulnaudit.dto.AssetProjectLinkResponse;
import com.vulnaudit.dto.CustomerBrief;
import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.dto.ProjectAssetItem;
import com.vulnaudit.dto.ProjectBrief;
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
import com.vulnaudit.service.AssetProjectLinkService;
import com.vulnaudit.service.AssetService;
import com.vulnaudit.util.Paging;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 资产—项目挂靠服务实现（t69）
 *
 * <p>见接口注释的三条不变式。这里补充两个实现细节：
 * <ul>
 *   <li><b>跨客户挂靠允许</b>：同一资产可挂到 A 客户的项目，也可挂到 B 客户的项目；
 *       挂靠行上的 {@code customerId} 始终取自**该项目**的客户，不做「资产只能属于一个客户」的限制；</li>
 *   <li><b>期次校验</b>：{@code phaseId} 非空时要求项目 {@code isPhased=true} 且期次属于该项目，
 *       否则 400；期次本身不存在则 404。</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetProjectLinkServiceImpl implements AssetProjectLinkService {

    private static final List<String> LINK_TYPES = List.of("TARGET", "SHARED", "REFERENCE");

    private final AssetService assetService;
    private final AssetRepository assetRepository;
    private final AssetProjectLinkRepository linkRepository;
    private final ProjectRepository projectRepository;
    private final ProjectPhaseRepository projectPhaseRepository;
    private final CustomerRepository customerRepository;
    /** t141：挂靠时自动打标签（客户名 + 项目名） */
    private final com.vulnaudit.service.TagDefinitionService tagDefinitionService;

    // ------------------------------------------------------------------ 创建资产 + 挂靠

    @Override
    @Transactional
    public Asset createAssetWithProject(@Nonnull AssetCreateRequest request, @Nullable UUID ownerId) {
        if (request.getProjectId() == null) {
            // 双保险：DTO 上 @NotNull 已拦一层（400 + 字段消息），这里再兜一层业务语义
            throw new BusinessException(ErrorCode.ASSET_PROJECT_REQUIRED);
        }

        Asset asset = Asset.builder()
                .name(request.getName())
                .targetType(request.getTargetType())
                .targetSource(request.getTargetSource())
                .description(request.getDescription())
                .priority(request.getPriority())
                .status(request.getStatus())
                .teamId(request.getTeamId())
                .ownerId(ownerId)
                .build();

        return createAssetWithLink(asset, request.getProjectId(), request.getPhaseId(),
                request.getLinkType(), request.getRemark(), ownerId);
    }

    /**
     * 创建资产 + 挂靠（单事务，t78 供 CSV 导入复用）
     *
     * <p>顺序：先校验项目/客户/期次（只读，不写库），再建资产，最后写挂靠；
     * 任一步抛异常都由外层事务整体回滚 —— 导入失败行不会留下「资产建成但没挂靠」的残留。
     */
    @Override
    @Transactional
    public Asset createAssetWithLink(@Nonnull Asset assetDraft,
                                     @Nonnull UUID projectId,
                                     @Nullable UUID phaseId,
                                     @Nullable String linkType,
                                     @Nullable String remark,
                                     @Nullable UUID operatorId) {
        Project project = requireProject(projectId);
        Customer customer = requireCustomer(project.getCustomerId());
        ProjectPhase phase = validatePhase(project, phaseId);

        Asset created = assetService.createAsset(assetDraft);

        // 同一事务：资产落库失败或挂靠冲突都会整体回滚，不产生「无项目资产」
        AssetProjectLink link = buildLink(created.getId(), project, customer, phase,
                normalizeLinkType(linkType), remark, operatorId);
        linkRepository.saveAndFlush(Objects.requireNonNull(link));

        // t141：挂靠即自动打标签（客户名 + 项目名；定义不存在则自动创建为自定义标签）
        autoTag(created.getId(), customer, project, operatorId);

        log.info("创建资产并挂靠: assetId={}, projectId={}, phaseId={}, customerId={}, linkType={}",
                created.getId(), project.getId(), phase != null ? phase.getId() : null,
                customer.getId(), link.getLinkType());
        return created;
    }

    // ------------------------------------------------------------------ 追加 / 解除挂靠

    @Override
    @Transactional
    public AssetProjectLinkResponse link(@Nullable UUID assetId, @Nonnull AssetProjectLinkRequest request,
                                         @Nullable UUID operatorId) {
        Asset asset = requireAsset(assetId);
        if (request.getProjectId() == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "挂靠必须指定项目");
        }
        Project project = requireProject(request.getProjectId());
        Customer customer = requireCustomer(project.getCustomerId());
        ProjectPhase phase = validatePhase(project, request.getPhaseId());

        if (linkRepository.existsByAssetIdAndProjectIdAndPhaseId(
                asset.getId(), project.getId(), phase != null ? phase.getId() : null)) {
            throw new BusinessException(ErrorCode.ASSET_PROJECT_LINK_EXISTS);
        }

        AssetProjectLink saved = linkRepository.saveAndFlush(Objects.requireNonNull(
                buildLink(asset.getId(), project, customer, phase,
                        normalizeLinkType(request.getLinkType()), request.getRemark(), operatorId)));

        // t141：追加挂靠同样自动打标签（幂等）
        autoTag(asset.getId(), customer, project, operatorId);

        log.info("资产追加挂靠: assetId={}, projectId={}, phaseId={}, linkId={}",
                asset.getId(), project.getId(), phase != null ? phase.getId() : null, saved.getId());
        return toResponse(saved, project, customer, phase);
    }

    @Override
    @Transactional
    public void unlink(@Nullable UUID assetId, @Nullable UUID linkId) {
        Asset asset = requireAsset(assetId);
        AssetProjectLink link = linkRepository.findById(linkId == null ? null : linkId)
                .filter(item -> asset.getId().equals(item.getAssetId()))
                .orElseThrow(() -> new BusinessException(ErrorCode.ASSET_PROJECT_LINK_NOT_FOUND));

        long remaining = linkRepository.countByAssetId(asset.getId());
        if (remaining <= 1) {
            // 不变式：资产必须至少挂靠一个项目
            throw new BusinessException(ErrorCode.ASSET_LINK_REQUIRED);
        }

        linkRepository.delete(link);
        log.info("资产解除挂靠: assetId={}, linkId={}, remaining={}", asset.getId(), link.getId(), remaining - 1);
    }

    @Override
    public List<AssetProjectLinkResponse> listByAsset(@Nullable UUID assetId) {
        Asset asset = requireAsset(assetId);
        List<AssetProjectLink> links = linkRepository.findByAssetId(asset.getId());
        if (links.isEmpty()) {
            return List.of();
        }
        Map<UUID, Project> projects = projectRepository.findAllById(
                        links.stream().map(AssetProjectLink::getProjectId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Project::getId, Function.identity()));
        Map<UUID, Customer> customers = customerRepository.findAllById(
                        links.stream().map(AssetProjectLink::getCustomerId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Customer::getId, Function.identity()));
        Map<UUID, ProjectPhase> phases = linkRepository.findByAssetId(asset.getId()).stream()
                .map(AssetProjectLink::getPhaseId).filter(Objects::nonNull).collect(Collectors.toSet())
                .isEmpty() ? Map.of()
                : projectPhaseRepository.findAllById(links.stream().map(AssetProjectLink::getPhaseId)
                        .filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(ProjectPhase::getId, Function.identity()));

        return links.stream()
                .sorted(Comparator.comparing(AssetProjectLink::getLinkedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(link -> toResponse(link,
                        projects.get(link.getProjectId()),
                        customers.get(link.getCustomerId()),
                        link.getPhaseId() != null ? phases.get(link.getPhaseId()) : null))
                .toList();
    }

    // ------------------------------------------------------------------ 反向查询

    @Override
    public PageResponse<ProjectAssetItem> listAssetsByProject(@Nullable UUID projectId, @Nullable UUID phaseId,
                                                              int page, int size) {
        return listAssetsByProject(projectId, phaseId, page, size, null);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProjectAssetItem> listAssetsByProject(@Nullable UUID projectId, @Nullable UUID phaseId,
                                                              int page, int size,
                                                              @Nullable java.util.Set<UUID> visibleAssetIds) {
        Project project = requireProject(projectId);
        ProjectPhase phase = phaseId == null ? null : requirePhaseOfProject(project, phaseId);

        PageRequest pageable = PageRequest.of(Paging.page(page), Paging.size(size),
                Sort.by("linkedAt").descending());
        // 不传期次 = 该项目全部挂靠（含项目级与各期次）；传期次 = 只看该期次
        Page<AssetProjectLink> linkPage = phase == null
                ? linkRepository.findByProjectIdOrderByLinkedAtDesc(project.getId(), pageable)
                : linkRepository.findPageByProjectAndPhase(project.getId(), phase.getId(), pageable);

        List<AssetProjectLink> links = linkPage.getContent();
        Map<UUID, Asset> assets = links.isEmpty() ? Map.of()
                : assetRepository.findAllById(links.stream().map(AssetProjectLink::getAssetId)
                        .collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Asset::getId, Function.identity()));
        Map<UUID, ProjectPhase> phases = links.stream().map(AssetProjectLink::getPhaseId)
                .filter(Objects::nonNull).collect(Collectors.toSet()).isEmpty() ? Map.of()
                : projectPhaseRepository.findAllById(links.stream().map(AssetProjectLink::getPhaseId)
                        .filter(Objects::nonNull).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(ProjectPhase::getId, Function.identity()));

        List<ProjectAssetItem> items = new ArrayList<>();
        for (AssetProjectLink link : links) {
            Asset asset = assets.get(link.getAssetId());
            if (asset == null) {
                continue;
            }
            // t70：项目可见 ≠ 项目下资产可见 —— 按调用者可见资产范围过滤（null = 管理层不限制）
            if (visibleAssetIds != null && !visibleAssetIds.contains(asset.getId())) {
                continue;
            }
            ProjectPhase linkPhase = link.getPhaseId() != null ? phases.get(link.getPhaseId()) : null;
            items.add(new ProjectAssetItem(
                    asset.getId(), asset.getName(), asset.getCode(), asset.getStatus(), asset.getPriority(),
                    asset.getTargetType(), asset.getOwnerId(),
                    link.getId(), link.getPhaseId(), linkPhase != null ? linkPhase.getName() : null,
                    link.getLinkType(), link.getRemark(), link.getLinkedAt(), java.util.List.of()));
        }

        // t145：一次批量取本页资产的标签（与页大小无关），供「项目资产」列表内联
        java.util.Map<UUID, java.util.List<com.vulnaudit.dto.AssetTagBrief>> tagsByAsset =
                assetService.tagsByAssetIds(items.stream().map(ProjectAssetItem::assetId).toList());
        List<ProjectAssetItem> itemsWithTags = items.stream()
                .map(item -> new ProjectAssetItem(item.assetId(), item.assetName(), item.assetCode(),
                        item.assetStatus(), item.assetPriority(), item.targetType(), item.ownerId(),
                        item.linkId(), item.phaseId(), item.phaseName(), item.linkType(), item.remark(),
                        item.linkedAt(),
                        tagsByAsset.getOrDefault(item.assetId(), java.util.List.of())))
                .toList();

        return PageResponse.<ProjectAssetItem>builder()
                .content(itemsWithTags)
                .totalElements(linkPage.getTotalElements())
                .totalPages(linkPage.getTotalPages())
                .currentPage(linkPage.getNumber())
                .pageSize(linkPage.getSize())
                .build();
    }

    // ------------------------------------------------------------------ 批量摘要 / 过滤

    @Override
    public Map<UUID, Ownership> ownershipOf(@Nonnull Collection<UUID> assetIds) {
        if (assetIds.isEmpty()) {
            return Map.of();
        }
        List<AssetProjectLink> links = linkRepository.findByAssetIdIn(assetIds);
        if (links.isEmpty()) {
            return Map.of();
        }

        // 一次批量取项目、客户（不再按资产逐个查 → 无 N+1）
        Map<UUID, Project> projects = projectRepository.findAllById(
                        links.stream().map(AssetProjectLink::getProjectId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Project::getId, Function.identity()));
        Map<UUID, Customer> customers = customerRepository.findAllById(
                        links.stream().map(AssetProjectLink::getCustomerId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Customer::getId, Function.identity()));

        Map<UUID, Set<UUID>> assetProjectIds = new LinkedHashMap<>();
        Map<UUID, Set<UUID>> assetCustomerIds = new LinkedHashMap<>();
        for (AssetProjectLink link : links) {
            assetProjectIds.computeIfAbsent(link.getAssetId(), key -> new HashSet<>()).add(link.getProjectId());
            assetCustomerIds.computeIfAbsent(link.getAssetId(), key -> new HashSet<>()).add(link.getCustomerId());
        }

        Map<UUID, Ownership> result = new HashMap<>();
        assetProjectIds.forEach((assetId, projectIds) -> {
            List<ProjectBrief> projectBriefs = projectIds.stream()
                    .map(projects::get).filter(Objects::nonNull)
                    .sorted(Comparator.comparing(Project::getName, Comparator.nullsLast(String::compareTo)))
                    .map(project -> new ProjectBrief(project.getId(), project.getCode(), project.getName(),
                            project.getCustomerId()))
                    .toList();
            List<CustomerBrief> customerBriefs = assetCustomerIds.getOrDefault(assetId, Set.of()).stream()
                    .map(customers::get).filter(Objects::nonNull)
                    .sorted(Comparator.comparing(Customer::getName, Comparator.nullsLast(String::compareTo)))
                    .map(customer -> new CustomerBrief(customer.getId(), customer.getCode(), customer.getName()))
                    .toList();
            result.put(assetId, new Ownership(customerBriefs, projectBriefs));
        });
        return result;
    }

    @Override
    @Nullable
    public Set<UUID> filterAssetIds(@Nullable UUID customerId, @Nullable UUID projectId, @Nullable UUID phaseId) {
        if (customerId == null && projectId == null && phaseId == null) {
            return null;
        }
        List<AssetProjectLink> matched;
        if (projectId != null) {
            matched = linkRepository.findByProjectIdAndPhaseId(projectId, phaseId);
        } else if (customerId != null) {
            matched = linkRepository.findByCustomerId(customerId);
        } else {
            // 只给 phaseId：按期次直接查挂靠
            matched = linkRepository.findByPhaseId(phaseId);
        }
        // 客户 + 项目同时给时，仍需客户维度一致（项目已隐含客户，做一次防御性过滤）
        if (customerId != null && projectId != null) {
            Map<UUID, UUID> projectCustomer = projectRepository.findAllById(
                            matched.stream().map(AssetProjectLink::getProjectId).collect(Collectors.toSet()))
                    .stream().collect(Collectors.toMap(Project::getId, Project::getCustomerId));
            matched = matched.stream()
                    .filter(link -> customerId.equals(projectCustomer.get(link.getProjectId())))
                    .toList();
        }
        return matched.stream().map(AssetProjectLink::getAssetId).collect(Collectors.toCollection(HashSet::new));
    }

    // ------------------------------------------------------------------ 内部

    private Asset requireAsset(@Nullable UUID assetId) {
        return assetRepository.findById(assetId == null ? null : assetId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ASSET_NOT_FOUND));
    }

    private Project requireProject(@Nullable UUID projectId) {
        return projectRepository.findById(projectId == null ? null : projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
    }

    private Customer requireCustomer(@Nullable UUID customerId) {
        return customerRepository.findById(customerId == null ? null : customerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND));
    }

    /**
     * 挂靠时自动打标签（t141）：客户名 + 项目名
     *
     * <p>规则：
     * <ul>
     *   <li>标签定义不存在 → **自动创建**为自定义标签（builtin=false），name 即客户名/项目名；</li>
     *   <li>幂等：同一资产重复挂靠同一项目不会产生重复的 {@code asset_tags} 行；</li>
     *   <li>解绑**不**自动删标签（用户可能手工加过同名标签，删除会误伤），见 README §64.4。</li>
     * </ul>
     */
    private void autoTag(UUID assetId, Customer customer, Project project, @Nullable UUID operatorId) {
        java.util.List<String> names = new java.util.ArrayList<>(2);
        if (customer != null && org.springframework.util.StringUtils.hasText(customer.getName())) {
            names.add(customer.getName().trim());
        }
        if (project != null && org.springframework.util.StringUtils.hasText(project.getName())) {
            names.add(project.getName().trim());
        }
        if (names.isEmpty()) {
            return;
        }
        tagDefinitionService.applyTags(assetId, names, operatorId);
    }

    private ProjectPhase requirePhaseOfProject(@Nonnull Project project, @Nonnull UUID phaseId) {
        ProjectPhase phase = projectPhaseRepository.findById(phaseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_PHASE_NOT_FOUND));
        if (!project.getId().equals(phase.getProjectId())) {
            throw new BusinessException(ErrorCode.PHASE_NOT_IN_PROJECT, "期次不属于该项目");
        }
        return phase;
    }

    /** phaseId 非空时：项目必须启用分期 + 期次必须属于该项目 */
    @Nullable
    private ProjectPhase validatePhase(@Nonnull Project project, @Nullable UUID phaseId) {
        if (phaseId == null) {
            return null;
        }
        if (!Boolean.TRUE.equals(project.getIsPhased())) {
            throw new BusinessException(ErrorCode.PROJECT_NOT_PHASED,
                    "该项目未启用分期，不能按期次挂靠");
        }
        ProjectPhase phase = projectPhaseRepository.findById(phaseId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_PHASE_NOT_FOUND));
        if (!project.getId().equals(phase.getProjectId())) {
            throw new BusinessException(ErrorCode.PHASE_NOT_IN_PROJECT, "期次不属于该项目");
        }
        return phase;
    }

    private String normalizeLinkType(@Nullable String linkType) {
        if (!StringUtils.hasText(linkType)) {
            return "TARGET";
        }
        String normalized = linkType.trim().toUpperCase(Locale.ROOT);
        if (!LINK_TYPES.contains(normalized)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "挂靠类型非法，仅支持 " + String.join(" / ", LINK_TYPES));
        }
        return normalized;
    }

    private AssetProjectLink buildLink(UUID assetId, Project project, Customer customer,
                                       @Nullable ProjectPhase phase, String linkType,
                                       @Nullable String remark, @Nullable UUID operatorId) {
        return AssetProjectLink.builder()
                .assetId(assetId)
                .projectId(project.getId())
                .phaseId(phase != null ? phase.getId() : null)
                .phaseKey(phase != null ? phase.getId() : AssetProjectLink.PHASE_KEY_NONE)
                .customerId(customer.getId())
                .linkType(linkType)
                .remark(remark)
                .linkedBy(operatorId)
                .build();
    }

    private AssetProjectLinkResponse toResponse(AssetProjectLink link, @Nullable Project project,
                                                @Nullable Customer customer, @Nullable ProjectPhase phase) {
        return new AssetProjectLinkResponse(
                link.getId(),
                link.getAssetId(),
                link.getProjectId(),
                project != null ? project.getCode() : null,
                project != null ? project.getName() : null,
                link.getCustomerId(),
                customer != null ? customer.getName() : null,
                link.getPhaseId(),
                phase != null ? phase.getName() : null,
                link.getLinkType(),
                link.getRemark(),
                link.getLinkedBy(),
                link.getLinkedAt());
    }
}
