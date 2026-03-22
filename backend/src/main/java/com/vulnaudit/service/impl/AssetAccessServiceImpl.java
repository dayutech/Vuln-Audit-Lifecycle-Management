package com.vulnaudit.service.impl;

import com.vulnaudit.dto.PageResponse;
import com.vulnaudit.entity.AssetAccess;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.AssetAccessRepository;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.UserRepository;
import com.vulnaudit.service.AssetAccessService;
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
import java.util.Optional;
import java.util.UUID;

/**
 * 资产访问服务实现类
 * 
 * 功能说明：
 * - 实现资产访问申请的业务逻辑
 * - 提供访问申请CRUD操作
 * - 提供访问申请审批功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AssetAccessServiceImpl implements AssetAccessService {

    private final AssetAccessRepository assetAccessRepository;
    private final AssetRepository assetRepository;
    private final UserRepository userRepository;

    /**
     * 创建访问申请
     */
    @Override
    @Transactional
    public AssetAccess createAccessRequest(AssetAccess assetAccess) {
        log.info("创建访问申请: assetId={}, userId={}", assetAccess.getAssetId(), assetAccess.getUserId());
        
        // 检查资产是否存在
        if (!assetRepository.existsById(assetAccess.getAssetId())) {
            log.warn("资产不存在: assetId={}", assetAccess.getAssetId());
            throw new BusinessException(ErrorCode.ASSET_NOT_FOUND);
        }
        
        // 检查用户是否存在
        if (!userRepository.existsById(assetAccess.getUserId())) {
            log.warn("用户不存在: userId={}", assetAccess.getUserId());
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        
        // 检查是否已有申请
        Optional<AssetAccess> existingAccess = assetAccessRepository.findByAssetIdAndUserId(
                assetAccess.getAssetId(), assetAccess.getUserId());
        if (existingAccess.isPresent()) {
            if ("approved".equals(existingAccess.get().getStatus())) {
                log.warn("用户已有该资产的访问权限: assetId={}, userId={}", assetAccess.getAssetId(), assetAccess.getUserId());
                throw new BusinessException(ErrorCode.ASSET_ACCESS_DENIED);
            }
            if ("pending".equals(existingAccess.get().getStatus())) {
                log.warn("用户已有待审核的申请: assetId={}, userId={}", assetAccess.getAssetId(), assetAccess.getUserId());
                throw new BusinessException(ErrorCode.CONFLICT);
            }
        }
        
        // 设置默认状态
        assetAccess.setStatus("pending");
        
        AssetAccess savedAccess = assetAccessRepository.save(assetAccess);
        log.info("访问申请创建成功: id={}", savedAccess.getId());
        
        return savedAccess;
    }

    /**
     * 根据ID查询访问申请
     */
    @Override
    public Optional<AssetAccess> findById(UUID id) {
        log.debug("查询访问申请: id={}", id);
        return assetAccessRepository.findById(id);
    }

    /**
     * 分页查询访问申请列表
     */
    @Override
    public PageResponse<AssetAccess> findAll(int page, int size, UUID assetId, UUID userId, String status) {
        log.debug("分页查询访问申请: page={}, size={}, assetId={}, userId={}, status={}", 
                page, size, assetId, userId, status);
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        
        Specification<AssetAccess> spec = (root, query, cb) -> {
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
        
        Page<AssetAccess> accessPage = assetAccessRepository.findAll(spec, pageable);
        
        return PageResponse.<AssetAccess>builder()
                .content(accessPage.getContent())
                .totalElements(accessPage.getTotalElements())
                .totalPages(accessPage.getTotalPages())
                .currentPage(accessPage.getNumber())
                .pageSize(accessPage.getSize())
                .build();
    }

    /**
     * 根据资产ID查询访问申请列表
     */
    @Override
    public List<AssetAccess> findByAssetId(UUID assetId) {
        log.debug("查询资产访问申请: assetId={}", assetId);
        return assetAccessRepository.findByAssetId(assetId);
    }

    /**
     * 根据用户ID查询访问申请列表
     */
    @Override
    public List<AssetAccess> findByUserId(UUID userId) {
        log.debug("查询用户访问申请: userId={}", userId);
        return assetAccessRepository.findByUserId(userId);
    }

    /**
     * 审批访问申请
     */
    @Override
    @Transactional
    public AssetAccess reviewAccessRequest(UUID id, boolean approved, String reviewNote, UUID reviewerId) {
        log.info("审批访问申请: id={}, approved={}, reviewerId={}", id, approved, reviewerId);
        
        AssetAccess access = assetAccessRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("访问申请不存在: id={}", id);
                    return new BusinessException(ErrorCode.ASSET_ACCESS_NOT_FOUND);
                });
        
        // 检查是否为待审核状态
        if (!"pending".equals(access.getStatus())) {
            log.warn("访问申请已处理: id={}, status={}", id, access.getStatus());
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }
        
        // 更新审批信息
        access.setStatus(approved ? "approved" : "rejected");
        access.setReviewedBy(reviewerId);
        access.setReviewedAt(LocalDateTime.now());
        access.setReviewNote(reviewNote);
        
        AssetAccess updatedAccess = assetAccessRepository.save(access);
        log.info("访问申请审批完成: id={}, status={}", updatedAccess.getId(), updatedAccess.getStatus());
        
        return updatedAccess;
    }

    /**
     * 撤销访问申请
     */
    @Override
    @Transactional
    public void revokeAccessRequest(UUID id, UUID userId) {
        log.info("撤销访问申请: id={}, userId={}", id, userId);
        
        AssetAccess access = assetAccessRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("访问申请不存在: id={}", id);
                    return new BusinessException(ErrorCode.ASSET_ACCESS_NOT_FOUND);
                });
        
        // 检查是否为申请人
        if (!access.getUserId().equals(userId)) {
            log.warn("非申请人无法撤销: id={}, userId={}", id, userId);
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        
        // 只能撤销待审核的申请
        if (!"pending".equals(access.getStatus())) {
            log.warn("只能撤销待审核的申请: id={}, status={}", id, access.getStatus());
            throw new BusinessException(ErrorCode.INVALID_STATUS);
        }
        
        assetAccessRepository.delete(access);
        log.info("访问申请已撤销: id={}", id);
    }

    /**
     * 检查用户是否有资产的已批准访问权限
     */
    @Override
    public boolean hasApprovedAccess(UUID assetId, UUID userId) {
        return assetAccessRepository.existsByAssetIdAndUserIdAndStatus(assetId, userId, "approved");
    }

    /**
     * 获取用户对资产的访问权限
     */
    @Override
    public Optional<AssetAccess> getApprovedAccess(UUID assetId, UUID userId) {
        return assetAccessRepository.findByAssetIdAndUserIdAndStatus(assetId, userId, "approved");
    }

    /**
     * 统计用户待审核的访问申请数量
     */
    @Override
    public long countPendingByUser(UUID userId) {
        return assetAccessRepository.countByUserIdAndStatus(userId, "pending");
    }

    /**
     * 获取用户待审批的访问申请列表
     * 用户作为资产负责人时需要审批的申请
     */
    @Override
    public PageResponse<AssetAccess> findPendingApprovalByUser(UUID userId, int page, int size) {
        log.debug("获取用户待审批的访问申请: userId={}", userId);
        
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        
        // 查询状态为pending的访问申请
        Specification<AssetAccess> spec = (root, query, cb) -> {
            return cb.equal(root.get("status"), "pending");
        };
        
        Page<AssetAccess> accessPage = assetAccessRepository.findAll(spec, pageable);
        
        return PageResponse.<AssetAccess>builder()
                .content(accessPage.getContent())
                .totalElements(accessPage.getTotalElements())
                .totalPages(accessPage.getTotalPages())
                .currentPage(accessPage.getNumber())
                .pageSize(accessPage.getSize())
                .build();
    }
}
