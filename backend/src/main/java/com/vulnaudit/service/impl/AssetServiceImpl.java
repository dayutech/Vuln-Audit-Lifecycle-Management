package com.vulnaudit.service.impl;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.AssetAccess;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.AssetAccessRepository;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.service.AssetService;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

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

    /**
     * 创建资产
     */
    @Override
    @Transactional
    public Asset createAsset(Asset asset) {
        log.info("创建资产: code={}, name={}", asset.getCode(), asset.getName());
        
        // 检查资产编号是否已存在
        if (assetRepository.existsByCode(asset.getCode())) {
            log.warn("资产编号已存在: {}", asset.getCode());
            throw new BusinessException(ErrorCode.ASSET_CODE_EXISTS);
        }
        
        // 设置默认状态
        if (asset.getStatus() == null) {
            asset.setStatus("active");
        }
        
        // 设置默认优先级
        if (asset.getPriority() == null) {
            asset.setPriority("P2");
        }
        
        Asset savedAsset = assetRepository.save(asset);
        log.info("资产创建成功: id={}, code={}", savedAsset.getId(), savedAsset.getCode());
        
        return savedAsset;
    }

    /**
     * 根据ID查询资产
     */
    @Override
    public Optional<Asset> findById(UUID id) {
        log.debug("查询资产: id={}", id);
        return assetRepository.findById(id);
    }

    /**
     * 根据资产编号查询资产
     */
    @Override
    public Optional<Asset> findByCode(String code) {
        log.debug("查询资产: code={}", code);
        return assetRepository.findByCode(code);
    }

    /**
     * 分页查询资产列表
     */
    @Override
    public PageResponse<Asset> findAll(int page, int size, String keyword, String status, UUID teamId, String priority) {
        log.debug("分页查询资产: page={}, size={}, keyword={}, status={}, teamId={}, priority={}", 
                page, size, keyword, status, teamId, priority);
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        
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
     * 根据团队ID查询资产列表
     */
    @Override
    public List<Asset> findByTeamId(UUID teamId) {
        log.debug("查询团队资产: teamId={}", teamId);
        return assetRepository.findByTeamId(teamId);
    }

    /**
     * 根据负责人ID查询资产列表
     */
    @Override
    public List<Asset> findByOwnerId(UUID ownerId) {
        log.debug("查询负责人资产: ownerId={}", ownerId);
        return assetRepository.findByOwnerId(ownerId);
    }

    /**
     * 更新资产信息
     */
    @Override
    @Transactional
    public Asset updateAsset(UUID id, Asset asset) {
        log.info("更新资产: id={}", id);
        
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
            existingAsset.setPriority(asset.getPriority());
        }
        if (asset.getTeamId() != null) {
            existingAsset.setTeamId(asset.getTeamId());
        }
        if (asset.getOwnerId() != null) {
            existingAsset.setOwnerId(asset.getOwnerId());
        }
        
        Asset updatedAsset = assetRepository.save(existingAsset);
        log.info("资产更新成功: id={}", updatedAsset.getId());
        
        return updatedAsset;
    }

    /**
     * 更新资产状态
     */
    @Override
    @Transactional
    public Asset updateStatus(UUID id, String status, UUID operatorId) {
        log.info("更新资产状态: id={}, status={}, operatorId={}", id, status, operatorId);
        
        Asset asset = assetRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("资产不存在: id={}", id);
                    return new BusinessException(ErrorCode.ASSET_NOT_FOUND);
                });
        
        // 验证状态值
        List<String> validStatuses = List.of("active", "under_test", "suspended", "archived");
        if (!validStatuses.contains(status)) {
            log.warn("无效的资产状态: {}", status);
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }
        
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
    public void deleteAsset(UUID id, UUID operatorId) {
        log.info("删除资产: id={}, operatorId={}", id, operatorId);
        
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
        // 资产负责人有权限
        Optional<Asset> assetOpt = assetRepository.findById(assetId);
        if (assetOpt.isPresent() && assetOpt.get().getOwnerId().equals(userId)) {
            return true;
        }
        
        // 检查是否有已批准的访问申请
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
                .map(access -> assetRepository.findById(access.getAssetId()))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
        
        // 合并并去重
        List<Asset> result = new ArrayList<>(ownedAssets);
        for (Asset asset : accessibleAssets) {
            if (result.stream().noneMatch(a -> a.getId().equals(asset.getId()))) {
                result.add(asset);
            }
        }
        
        return result;
    }
}
