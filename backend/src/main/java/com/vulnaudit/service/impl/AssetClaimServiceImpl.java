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
    private final com.vulnaudit.service.ApprovalRoutingService approvalRoutingService;

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

        // 审批权限校验：与访问申请一致，仅限覆盖该资产范围的管理人员
        if (reviewerId == null
                || !approvalRoutingService.canReview(claim.getAssetId(), reviewerId)) {
            log.warn("无认领审批权限: claimId={}, reviewerId={}, assetId={}", id, reviewerId, claim.getAssetId());
            throw new BusinessException(ErrorCode.FORBIDDEN, "无权限审批该资产的认领申请");
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
     *
     * <p>语义（t39 校正）：
     * <ul>
     *     <li>仅<b>认领人本人</b>可完成。SRS 4.4「资产认领」把完成动作定义为认领人确认交付结果，
     *         管理层不代办完成（管理层的介入动作是「强制放弃」，见 {@link #abandonClaim}）；
     *         因此这里不放行 admin/project_leader，宁可 403 也不做隐式授权。</li>
     *     <li>状态必须为 {@code approved}（已批准但未完成），否则 400 {@code INVALID_STATUS}；</li>
     *     <li>认领记录不存在 → 404；非认领人 → 403。</li>
     * </ul>
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

        // 检查是否为认领人（管理层不放行，见方法注释）
        if (userId == null || !claim.getUserId().equals(userId)) {
            log.warn("非认领人无法完成: id={}, userId={}, claimUserId={}", id, userId, claim.getUserId());
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有认领人本人可以完成认领");
        }

        // 检查是否为已批准状态
        if (!"approved".equals(claim.getStatus())) {
            log.warn("认领申请未批准，无法完成: id={}, status={}", id, claim.getStatus());
            throw new BusinessException(ErrorCode.INVALID_STATUS,
                    "仅 approved 状态可完成，当前状态: " + claim.getStatus());
        }

        claim.setStatus("completed");
        claim.setCompletedAt(LocalDateTime.now());
        assetClaimRepository.save(claim);

        log.info("认领已完成: id={}", id);
    }

    /**
     * 放弃认领
     *
     * <p>语义（t39 校正）：
     * <ul>
     *     <li>认领人本人可放弃；</li>
     *     <li>管理层（admin / project_leader / team_leader）可<b>强制放弃</b>，并把操作人记录到
     *         {@code reviewed_by}/{@code reviewed_at}，同时在 {@code review_note} 追加操作人标记，
     *         保证审计可追溯（实体无独立的 abandoned_by 列，复用评审字段是最近似语义）；</li>
     *     <li>状态必须为 {@code approved}，否则 400；既非本人又非管理层 → 403；记录不存在 → 404。</li>
     * </ul>
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

        boolean isOwner = userId != null && claim.getUserId().equals(userId);
        boolean isManagement = userId != null && isManagementRole(userId);

        if (!isOwner && !isManagement) {
            log.warn("无权放弃该认领: id={}, userId={}, claimUserId={}", id, userId, claim.getUserId());
            throw new BusinessException(ErrorCode.FORBIDDEN, "只有认领人或管理层可以放弃认领");
        }

        // 检查是否为已批准状态
        if (!"approved".equals(claim.getStatus())) {
            log.warn("认领申请未批准，无法放弃: id={}, status={}", id, claim.getStatus());
            throw new BusinessException(ErrorCode.INVALID_STATUS,
                    "仅 approved 状态可放弃，当前状态: " + claim.getStatus());
        }

        claim.setStatus("abandoned");
        if (isOwner) {
            claim.setReviewNote(note);
        } else {
            // 强制放弃：记录操作人，便于审计追溯
            String operatorNote = "强制放弃 by " + userId + (note == null || note.isBlank() ? "" : "：" + note);
            claim.setReviewNote(operatorNote);
            claim.setReviewedBy(userId);
            claim.setReviewedAt(LocalDateTime.now());
        }
        assetClaimRepository.save(claim);

        log.info("认领已放弃: id={}, forced={}", id, !isOwner);
    }

    /**
     * 是否管理层角色（admin / project_leader / team_leader）
     */
    private boolean isManagementRole(UUID userId) {
        return approvalRoutingService.hasRole(userId, "admin")
                || approvalRoutingService.hasRole(userId, "project_leader")
                || approvalRoutingService.hasRole(userId, "team_leader");
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
