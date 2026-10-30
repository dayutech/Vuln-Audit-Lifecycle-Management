package com.vulnaudit.service.impl;

import com.vulnaudit.dto.AssetResponse;
import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.AssetAccess;
import com.vulnaudit.entity.Team;
import com.vulnaudit.entity.User;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.AssetAccessRepository;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.TeamRepository;
import com.vulnaudit.repository.UserRepository;
import com.vulnaudit.service.AssetService;
import com.vulnaudit.util.Paging;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.annotation.Nullable;

/**
 * 资产服务实现类
 * 
 * 功能说明：
 * - 实现资产管理的业务逻辑
 * - 提供资产CRUD操作
 * - 提供资产分组管理
 * - 提供资产搜索功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetServiceImpl implements AssetService {

    private final AssetRepository assetRepository;
    private final AssetAccessRepository assetAccessRepository;
    private final TeamRepository teamRepository;
    private final UserRepository userRepository;

    private static final List<String> VALID_PRIORITIES = List.of("P0", "P1", "P2", "P3");
    private static final List<String> VALID_STATUSES = List.of("active", "under_test", "suspended", "archived");
    private static final DateTimeFormatter ASSET_CODE_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

    /**
     * 创建资产
     */
    @Override
    @Transactional
    public Asset createAsset(Asset asset) {
        if (asset.getTeamId() == null || !teamRepository.existsById(asset.getTeamId())) {
            throw new BusinessException(ErrorCode.TEAM_NOT_FOUND);
        }

        if (asset.getOwnerId() == null || !userRepository.existsById(asset.getOwnerId())) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        if (!StringUtils.hasText(asset.getPriority())) {
            asset.setPriority("P2");
        }
        validatePriority(asset.getPriority());

        if (!StringUtils.hasText(asset.getStatus())) {
            asset.setStatus("active");
        }
        validateStatus(asset.getStatus());

        asset.setCode(generateAssetCode());
        log.info("创建资产: code={}, name={}", asset.getCode(), asset.getName());

        Asset savedAsset;
        try {
            // saveAndFlush：让 assets.code 唯一键冲突在可控范围内抛出（编号为随机生成，碰撞概率极低）
            savedAsset = assetRepository.saveAndFlush(asset);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            if (com.vulnaudit.util.PersistenceErrors.isUniqueViolation(e)) {
                log.warn("资产编号冲突: code={}, detail={}", asset.getCode(),
                        com.vulnaudit.util.PersistenceErrors.detail(e));
                throw new BusinessException(ErrorCode.CONFLICT, "资产编号冲突，请重试");
            }
            String detail = com.vulnaudit.util.PersistenceErrors.detail(e);
            log.warn("资产数据完整性校验失败: name={}, detail={}", asset.getName(), detail);
            throw new BusinessException(ErrorCode.BAD_REQUEST, "资产数据不完整或不符合约束: " + detail);
        }
        log.info("资产创建成功: id={}, code={}", savedAsset.getId(), savedAsset.getCode());

        return savedAsset;
    }

    /**
     * 根据ID查询资产
     */
    @Override
    public Optional<Asset> findById(@Nullable UUID id) {
        log.debug("查询资产: id={}", id);
        return id != null ? assetRepository.findById(id) : Optional.empty();
    }

    /**
     * 根据ID查询资产详情（包含团队和负责人信息）
     */
    @Override
    public Optional<AssetResponse> findByIdWithDetails(@Nullable UUID id) {
        log.debug("查询资产详情: id={}", id);
        if (id == null) {
            return Optional.empty();
        }
        
        Optional<Asset> assetOpt = assetRepository.findById(id);
        if (assetOpt.isEmpty()) {
            return Optional.empty();
        }
        
        Asset asset = assetOpt.get();
        String teamName = resolveTeamName(asset.getTeamId());
        String ownerName = resolveOwnerName(asset.getOwnerId());
        
        return Optional.of(AssetResponse.fromAsset(asset, teamName, ownerName));
    }

    /**
     * 根据资产编号查询资产
     */
    @Override
    public Optional<Asset> findByCode(@Nullable String code) {
        log.debug("查询资产: code={}", code);
        return code != null ? assetRepository.findByCode(code) : Optional.empty();
    }

    /**
     * 分页查询资产列表
     */
    @Override
    public PageResponse<Asset> findAll(int page, int size, @Nullable String keyword, @Nullable String status, @Nullable UUID teamId, @Nullable String priority) {
        log.debug("分页查询资产: page={}, size={}, keyword={}, status={}, teamId={}, priority={}",
                page, size, keyword, status, teamId, priority);
        
        Pageable pageable = PageRequest.of(Paging.page(page), Paging.size(size), Sort.by("createdAt").descending());
        
        Specification<Asset> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            
            if (StringUtils.hasText(keyword)) {
                jakarta.persistence.criteria.Predicate nameLike = cb.like(root.get("name"), "%" + keyword + "%");
                jakarta.persistence.criteria.Predicate codeLike = cb.like(root.get("code"), "%" + keyword + "%");
                jakarta.persistence.criteria.Predicate descLike = cb.like(root.get("description"), "%" + keyword + "%");
                predicates.add(cb.or(nameLike, codeLike, descLike));
            }
            
            if (StringUtils.hasText(status)) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            
            if (teamId != null) {
                predicates.add(cb.equal(root.get("teamId"), teamId));
            }
            
            if (StringUtils.hasText(priority)) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }
            
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        
        Page<Asset> assetPage = assetRepository.findAll(spec, pageable);
        
        return PageResponse.<Asset>builder()
                .content(assetPage.getContent())
                .totalElements(assetPage.getTotalElements())
                .totalPages(assetPage.getTotalPages())
                .currentPage(assetPage.getNumber())
                .pageSize(assetPage.getSize())
                .build();
    }

    /**
     * 分页查询资产列表（包含团队和负责人信息）
     */
    @Override
    public PageResponse<AssetResponse> findAllWithDetails(int page, int size, @Nullable String keyword, @Nullable String status, @Nullable UUID teamId, @Nullable String priority) {
        return findAllWithDetails(page, size, keyword, status, teamId, priority, null);
    }

    /**
     * 分页查询资产列表（带数据级可见范围）
     *
     * <p><b>单一事实来源</b>：{@code visibleAssetIds} 由 {@code AccessGuard.visibleAssetIds(auth)} 提供，
     * 与详情/从属资源使用的判定完全相同，因此「列表里出现的资产，详情必然 200」。
     *
     * @param visibleAssetIds 可见资产ID集合；{@code null} = 不限制（管理层），空集合 = 无可见资产
     */
    @Override
    public PageResponse<AssetResponse> findAllWithDetails(int page, int size, @Nullable String keyword,
                                                          @Nullable String status, @Nullable UUID teamId,
                                                          @Nullable String priority,
                                                          @Nullable Set<UUID> visibleAssetIds) {
        log.debug("分页查询资产详情: page={}, size={}, keyword={}, status={}, teamId={}, priority={}, visibilityRestricted={}",
                page, size, keyword, status, teamId, priority, visibleAssetIds != null);

        if (visibleAssetIds != null && visibleAssetIds.isEmpty()) {
            // 无可见资产：直接返回空页，避免生成 IN () 的非法 SQL
            return PageResponse.<AssetResponse>builder()
                    .content(List.of())
                    .totalElements(0L)
                    .totalPages(0)
                    .currentPage(page)
                    .pageSize(size)
                    .build();
        }

        Pageable pageable = PageRequest.of(Paging.page(page), Paging.size(size), Sort.by("createdAt").descending());

        Specification<Asset> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();

            if (visibleAssetIds != null) {
                predicates.add(root.get("id").in(visibleAssetIds));
            }

            if (StringUtils.hasText(keyword)) {
                jakarta.persistence.criteria.Predicate nameLike = cb.like(root.get("name"), "%" + keyword + "%");
                jakarta.persistence.criteria.Predicate codeLike = cb.like(root.get("code"), "%" + keyword + "%");
                jakarta.persistence.criteria.Predicate descLike = cb.like(root.get("description"), "%" + keyword + "%");
                predicates.add(cb.or(nameLike, codeLike, descLike));
            }
            
            if (StringUtils.hasText(status)) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            
            if (teamId != null) {
                predicates.add(cb.equal(root.get("teamId"), teamId));
            }
            
            if (StringUtils.hasText(priority)) {
                predicates.add(cb.equal(root.get("priority"), priority));
            }
            
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        
        Page<Asset> assetPage = assetRepository.findAll(spec, pageable);
        
        List<Asset> assets = assetPage.getContent();
        
        Map<UUID, String> teamNameMap = resolveTeamNames(assets);
        Map<UUID, String> ownerNameMap = resolveOwnerNames(assets);
        
        List<AssetResponse> responses = assets.stream()
                .map(asset -> AssetResponse.fromAsset(
                        asset,
                        teamNameMap.get(asset.getTeamId()),
                        ownerNameMap.get(asset.getOwnerId())
                ))
                .collect(Collectors.toList());
        
        return PageResponse.<AssetResponse>builder()
                .content(responses)
                .totalElements(assetPage.getTotalElements())
                .totalPages(assetPage.getTotalPages())
                .currentPage(assetPage.getNumber())
                .pageSize(assetPage.getSize())
                .build();
    }

    /**
     * 根据团队ID查询资产列表
     */
    @Override
    public List<Asset> findByTeamId(@Nullable UUID teamId) {
        log.debug("查询团队资产: teamId={}", teamId);
        return teamId != null ? assetRepository.findByTeamId(teamId) : List.of();
    }

    /**
     * 根据负责人ID查询资产列表
     */
    @Override
    public List<Asset> findByOwnerId(@Nullable UUID ownerId) {
        log.debug("查询负责人资产: ownerId={}", ownerId);
        return ownerId != null ? assetRepository.findByOwnerId(ownerId) : List.of();
    }

    /**
     * 更新资产信息
     */
    @Override
    @Transactional
    public Asset updateAsset(@Nullable UUID id, Asset asset) {
        log.info("更新资产: id={}", id);

        if (id == null) {
            throw new BusinessException(ErrorCode.ASSET_NOT_FOUND);
        }

        Asset existingAsset = assetRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("资产不存在: id={}", id);
                    return new BusinessException(ErrorCode.ASSET_NOT_FOUND);
                });

        // 更新可修改的字段
        if (asset.getName() != null) {
            existingAsset.setName(asset.getName());
        }
        if (asset.getDescription() != null) {
            existingAsset.setDescription(asset.getDescription());
        }
        if (asset.getTargetType() != null) {
            existingAsset.setTargetType(asset.getTargetType());
        }
        if (asset.getTargetSource() != null) {
            existingAsset.setTargetSource(asset.getTargetSource());
        }
        if (asset.getPriority() != null) {
            validatePriority(asset.getPriority());
            existingAsset.setPriority(asset.getPriority());
        }
        if (asset.getTeamId() != null) {
            if (!teamRepository.existsById(asset.getTeamId())) {
                throw new BusinessException(ErrorCode.TEAM_NOT_FOUND);
            }
            existingAsset.setTeamId(asset.getTeamId());
        }
        if (asset.getOwnerId() != null) {
            if (!userRepository.existsById(asset.getOwnerId())) {
                throw new BusinessException(ErrorCode.USER_NOT_FOUND);
            }
            existingAsset.setOwnerId(asset.getOwnerId());
        }

        Asset updatedAsset = assetRepository.save(Objects.requireNonNull(existingAsset));
        log.info("资产更新成功: id={}", updatedAsset.getId());

        return updatedAsset;
    }

    /**
     * 更新资产状态
     */
    @Override
    @Transactional
    public Asset updateStatus(@Nullable UUID id, @Nullable String status, @Nullable UUID operatorId) {
        log.info("更新资产状态: id={}, status={}, operatorId={}", id, status, operatorId);

        if (id == null) {
            throw new BusinessException(ErrorCode.ASSET_NOT_FOUND);
        }

        Asset asset = assetRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("资产不存在: id={}", id);
                    return new BusinessException(ErrorCode.ASSET_NOT_FOUND);
                });

        validateStatus(status);

        asset.setStatus(status);
        Asset updatedAsset = assetRepository.save(asset);
        log.info("资产状态更新成功: id={}, status={}", updatedAsset.getId(), status);

        return updatedAsset;
    }

    /**
     * 删除资产（软删除）
     */
    @Override
    @Transactional
    public void deleteAsset(@Nullable UUID id, @Nullable UUID operatorId) {
        log.info("删除资产: id={}, operatorId={}", id, operatorId);

        if (id == null) {
            throw new BusinessException(ErrorCode.ASSET_NOT_FOUND);
        }

        Asset asset = assetRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("资产不存在: id={}", id);
                    return new BusinessException(ErrorCode.ASSET_NOT_FOUND);
                });
        
        asset.setStatus("archived");
        assetRepository.save(asset);
        
        log.info("资产已归档: id={}", id);
    }

    /**
     * 检查资产编号是否存在
     */
    @Override
    public boolean existsByCode(String code) {
        return assetRepository.existsByCode(code);
    }

    /**
     * 统计团队下的资产数量
     */
    @Override
    public long countByTeamId(UUID teamId) {
        return assetRepository.countByTeamId(teamId);
    }

    /**
     * 统计指定状态的资产数量
     */
    @Override
    public long countByStatus(String status) {
        return assetRepository.countByStatus(status);
    }

    /**
     * 检查用户是否有资产访问权限
     */
    @Override
    public boolean hasAccess(UUID assetId, UUID userId) {
        if (assetId == null || userId == null) {
            return false;
        }
        Optional<Asset> assetOpt = assetRepository.findById(assetId);
        if (assetOpt.isPresent() && userId.equals(assetOpt.get().getOwnerId())) {
            return true;
        }

        return assetAccessRepository.existsByAssetIdAndUserIdAndStatus(assetId, userId, "approved");
    }

    /**
     * 获取用户可访问的资产列表
     */
    @Override
    public List<Asset> findAccessibleAssets(UUID userId) {
        log.debug("获取用户可访问的资产: userId={}", userId);

        // 获取用户拥有的资产
        List<Asset> ownedAssets = assetRepository.findByOwnerId(userId);

        // 获取用户有访问权限的资产
        List<AssetAccess> accesses = assetAccessRepository.findByUserIdAndStatus(userId, "approved");
        List<Asset> accessibleAssets = accesses.stream()
                .map(AssetAccess::getAssetId)
                .filter(java.util.Objects::nonNull)
                .map(assetRepository::findById)
                .filter(Optional::isPresent)
                .map(opt -> opt.orElse(null))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());

        // 合并并去重
        List<Asset> result = new ArrayList<>(ownedAssets);
        for (Asset asset : accessibleAssets) {
            UUID assetId = asset.getId();
            if (assetId != null && result.stream().noneMatch(a -> a.getId() != null && a.getId().equals(assetId))) {
                result.add(asset);
            }
        }

        return result;
    }

    /**
     * 获取用户已获授权的资产列表
     */
    @Override
    public List<Asset> findAuthorizedAssetsByUser(UUID userId) {
        log.debug("获取用户已获授权的资产: userId={}", userId);

        // 获取用户有已批准访问权限的资产
        List<AssetAccess> accesses = assetAccessRepository.findByUserIdAndStatus(userId, "approved");
        List<Asset> authorizedAssets = accesses.stream()
                .map(AssetAccess::getAssetId)
                .filter(java.util.Objects::nonNull)
                .map(assetRepository::findById)
                .filter(Optional::isPresent)
                .map(opt -> opt.orElse(null))
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());

        return authorizedAssets;
    }

    /**
     * 解析单个团队名称
     */
    private String resolveTeamName(UUID teamId) {
        if (teamId == null) {
            return null;
        }
        return teamRepository.findById(teamId)
                .map(Team::getName)
                .orElse(null);
    }

    /**
     * 解析单个负责人名称
     */
    private String resolveOwnerName(UUID ownerId) {
        if (ownerId == null) {
            return null;
        }
        return userRepository.findById(ownerId)
                .map(User::getUsername)
                .orElse(null);
    }

    /**
     * 批量解析团队名称
     */
    private Map<UUID, String> resolveTeamNames(List<Asset> assets) {
        Set<UUID> teamIds = assets.stream()
                .map(Asset::getTeamId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        // 用可接受 null 键的 HashMap：调用方会以 asset.getTeamId()（可能为 null）取值，
        // Map.of()/Collections.emptyMap() 的 get(null) 会抛 NPE（t46 列表收敛后暴露）
        if (teamIds.isEmpty()) {
            return new java.util.HashMap<>();
        }
        
        List<Team> teams = teamRepository.findAllById(teamIds);
        return teams.stream()
                .collect(Collectors.toMap(Team::getId, Team::getName));
    }

    /**
     * 批量解析负责人名称
     */
    private Map<UUID, String> resolveOwnerNames(List<Asset> assets) {
        Set<UUID> ownerIds = assets.stream()
                .map(Asset::getOwnerId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        
        if (ownerIds.isEmpty()) {
            // 同 resolveTeamNames：调用方会用可能为 null 的 ownerId 取值，Map.of() 的 get(null) 会抛 NPE
            return new java.util.HashMap<>();
        }
        
        List<User> users = userRepository.findAllById(ownerIds);
        return users.stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));
    }

    private void validatePriority(String priority) {
        if (!VALID_PRIORITIES.contains(priority)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的优先级值");
        }
    }

    private void validateStatus(String status) {
        if (!VALID_STATUSES.contains(status)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }
    }

    private String generateAssetCode() {
        String datePart = LocalDate.now().format(ASSET_CODE_DATE_FORMATTER);
        for (int i = 0; i < 20; i++) {
            String randomPart = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
            String code = "AST-" + datePart + "-" + randomPart;
            if (!assetRepository.existsByCode(code)) {
                return code;
            }
        }
        throw new BusinessException(ErrorCode.INTERNAL_ERROR, "资产编号生成失败");
    }
}
