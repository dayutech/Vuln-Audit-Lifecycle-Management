package com.vulnaudit.service.impl;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.AssetClaim;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.AssetClaimRepository;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.UserRepository;
import com.vulnaudit.service.AssetClaimService;
import com.vulnaudit.util.Paging;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * 资产认领服务实现类
 * 
 * 功能说明：
 * - 实现资产认领的业务逻辑
 * - 提供认领CRUD操作
 * - 提供认领状态管理
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetClaimServiceImpl implements AssetClaimService {

    private final AssetClaimRepository assetClaimRepository;
    private final AssetRepository assetRepository;
    private final UserRepository userRepository;

    /**
     * 创建认领申请
     */
    @Override
    @Transactional
    public AssetClaim createClaim(@Nonnull AssetClaim assetClaim) {
        Objects.requireNonNull(assetClaim, "assetClaim must not be null");
        UUID assetId = Objects.requireNonNull(assetClaim.getAssetId(), "assetClaim.assetId must not be null");
        UUID userId = Objects.requireNonNull(assetClaim.getUserId(), "assetClaim.userId must not be null");
        log.info("创建认领申请: assetId={}, userId={}", assetId, userId);

        // 检查资产是否存在
        if (!assetRepository.existsById(assetId)) {
            log.warn("资产不存在: assetId={}", assetId);
            throw new BusinessException(ErrorCode.ASSET_NOT_FOUND);
        }

        // 检查用户是否存在
        if (!userRepository.existsById(userId)) {
            log.warn("用户不存在: userId={}", userId);
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 检查资产是否已被认领
        if (hasActiveClaim(assetId)) {
            log.warn("资产已被认领: assetId={}", assetId);
            throw new BusinessException(ErrorCode.ASSET_ALREADY_CLAIMED);
        }

        // 检查用户是否已有待审核的认领申请
        if (assetClaimRepository.existsByAssetIdAndStatus(assetId, "pending")) {
            log.warn("资产已有待审核的认领申请: assetId={}", assetId);
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        
        // 设置默认状态
        assetClaim.setStatus("pending");
        
        AssetClaim savedClaim = assetClaimRepository.save(assetClaim);
        log.info("认领申请创建成功: id={}", savedClaim.getId());
        
        return savedClaim;
    }

    /**
     * 根据ID查询认领申请
     */
    @Override
    public Optional<AssetClaim> findById(@Nullable UUID id) {
        log.debug("查询认领申请: id={}", id);
        return id != null ? assetClaimRepository.findById(id) : Optional.empty();
    }

    /**
     * 分页查询认领申请列表
     */
    @Override
    public PageResponse<AssetClaim> findAll(int page, int size, @Nullable UUID assetId, @Nullable UUID userId, @Nullable String status) {
        log.debug("分页查询认领申请: page={}, size={}, assetId={}, userId={}, status={}",
                page, size, assetId, userId, status);
        
        Pageable pageable = PageRequest.of(Paging.page(page), Paging.size(size), Sort.by("createdAt").descending());
        
        Specification<AssetClaim> spec = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            
            if (assetId != null) {
                predicates.add(cb.equal(root.get("assetId"), assetId));
            }
            
            if (userId != null) {
                predicates.add(cb.equal(root.get("userId"), userId));
            }
            
            if (status != null && !status.isEmpty()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        
        Page<AssetClaim> claimPage = assetClaimRepository.findAll(spec, pageable);
        
        return PageResponse.<AssetClaim>builder()
                .content(claimPage.getContent())
                .totalElements(claimPage.getTotalElements())
                .totalPages(claimPage.getTotalPages())
                .currentPage(claimPage.getNumber())
                .pageSize(claimPage.getSize())
                .build();
    }

    /**
     * 根据资产ID查询认领申请列表
     */
    @Override
    public List<AssetClaim> findByAssetId(UUID assetId) {
        log.debug("查询资产认领申请: assetId={}", assetId);
        return assetClaimRepository.findByAssetId(assetId);
    }

    /**
     * 根据用户ID查询认领申请列表
     */
    @Override
    public List<AssetClaim> findByUserId(UUID userId) {
        log.debug("查询用户认领申请: userId={}", userId);
        return assetClaimRepository.findByUserId(userId);
    }

    /**
     * 审批认领申请
     */
    @Override
    @Transactional
    public AssetClaim reviewClaim(@Nullable UUID id, boolean approved, @Nullable String reviewNote, @Nullable UUID reviewerId) {
        log.info("审批认领申请: id={}, approved={}, reviewerId={}", id, approved, reviewerId);

        if (id == null) {
            throw new BusinessException(ErrorCode.ASSET_CLAIM_NOT_FOUND);
        }

        AssetClaim claim = assetClaimRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("认领申请不存在: id={}", id);
                    return new BusinessException(ErrorCode.ASSET_CLAIM_NOT_FOUND);
                });

        // 检查是否为待审核状态
        if (!"pending".equals(claim.getStatus())) {
            log.warn("认领申请已处理: id={}, status={}", id, claim.getStatus());
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }

        // 更新审批信息
        claim.setStatus(approved ? "approved" : "rejected");
        claim.setReviewedBy(reviewerId);
        claim.setReviewedAt(LocalDateTime.now());
        claim.setReviewNote(reviewNote);

        // 如果批准，设置认领时间
        if (approved) {
            claim.setClaimedAt(LocalDateTime.now());
        }

        AssetClaim updatedClaim = assetClaimRepository.save(claim);
        log.info("认领申请审批完成: id={}, status={}", updatedClaim.getId(), updatedClaim.getStatus());

        return updatedClaim;
    }

    /**
     * 完成认领
     */
    @Override
    @Transactional
    public void completeClaim(@Nullable UUID id, @Nullable UUID userId) {
        log.info("完成认领: id={}, userId={}", id, userId);

        if (id == null) {
            throw new BusinessException(ErrorCode.ASSET_CLAIM_NOT_FOUND);
        }

        AssetClaim claim = assetClaimRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("认领申请不存在: id={}", id);
                    return new BusinessException(ErrorCode.ASSET_CLAIM_NOT_FOUND);
                });

        // 检查是否为认领人
        if (!claim.getUserId().equals(userId)) {
            log.warn("非认领人无法完成: id={}, userId={}", id, userId);
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 检查是否为已批准状态
        if (!"approved".equals(claim.getStatus())) {
            log.warn("认领申请未批准: id={}, status={}", id, claim.getStatus());
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }

        claim.setStatus("completed");
        claim.setCompletedAt(LocalDateTime.now());
        assetClaimRepository.save(claim);

        log.info("认领已完成: id={}", id);
    }

    /**
     * 放弃认领
     */
    @Override
    @Transactional
    public void abandonClaim(@Nullable UUID id, @Nullable UUID userId, @Nullable String note) {
        log.info("放弃认领: id={}, userId={}", id, userId);

        if (id == null) {
            throw new BusinessException(ErrorCode.ASSET_CLAIM_NOT_FOUND);
        }

        AssetClaim claim = assetClaimRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("认领申请不存在: id={}", id);
                    return new BusinessException(ErrorCode.ASSET_CLAIM_NOT_FOUND);
                });

        // 检查是否为认领人
        if (!claim.getUserId().equals(userId)) {
            log.warn("非认领人无法放弃: id={}, userId={}", id, userId);
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        // 检查是否为已批准状态
        if (!"approved".equals(claim.getStatus())) {
            log.warn("认领申请未批准: id={}, status={}", id, claim.getStatus());
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }

        claim.setStatus("abandoned");
        claim.setReviewNote(note);
        assetClaimRepository.save(claim);

        log.info("认领已放弃: id={}", id);
    }

    /**
     * 撤销认领申请
     */
    @Override
    @Transactional
    public void revokeClaim(@Nullable UUID id, @Nullable UUID userId) {
        log.info("撤销认领申请: id={}, userId={}", id, userId);

        if (id == null) {
            throw new BusinessException(ErrorCode.ASSET_CLAIM_NOT_FOUND);
        }

        AssetClaim claim = assetClaimRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("认领申请不存在: id={}", id);
                    return new BusinessException(ErrorCode.ASSET_CLAIM_NOT_FOUND);
                });
        
        // 检查是否为申请人
        if (!claim.getUserId().equals(userId)) {
            log.warn("非申请人无法撤销: id={}, userId={}", id, userId);
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        
        // 只能撤销待审核的申请
        if (!"pending".equals(claim.getStatus())) {
            log.warn("只能撤销待审核的申请: id={}, status={}", id, claim.getStatus());
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }
        
        assetClaimRepository.delete(claim);
        log.info("认领申请已撤销: id={}", id);
    }

    /**
     * 检查资产是否有活跃认领
     */
    @Override
    public boolean hasActiveClaim(UUID assetId) {
        return assetClaimRepository.existsByAssetIdAndStatus(assetId, "approved");
    }

    /**
     * 获取资产的当前活跃认领
     */
    @Override
    public Optional<AssetClaim> getActiveClaim(UUID assetId) {
        return assetClaimRepository.findByAssetIdAndStatusOrderByCreatedAtDesc(assetId, "approved");
    }

    /**
     * 检查用户是否有资产的活跃认领
     */
    @Override
    public boolean hasActiveClaimByUser(UUID assetId, UUID userId) {
        return assetClaimRepository.existsByAssetIdAndUserIdAndStatus(assetId, userId, "approved");
    }

    /**
     * 统计用户指定状态的认领数量
     */
    @Override
    public long countByUserIdAndStatus(UUID userId, String status) {
        return assetClaimRepository.countByUserIdAndStatus(userId, status);
    }
}
