package com.vulnaudit.service;

import com.vulnaudit.entity.ApprovalScope;
import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.Role;
import com.vulnaudit.entity.Team;
import com.vulnaudit.entity.TeamMember;
import com.vulnaudit.repository.ApprovalScopeRepository;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.RoleRepository;
import com.vulnaudit.repository.TeamMemberRepository;
import com.vulnaudit.repository.TeamRepository;
import com.vulnaudit.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 审批路由与审批权限判定。
 *
 * <p>对齐 SRS 3.7.3：
 * <ol>
 *   <li>优先路由到申请人的直属领导（小组长），前提是其被授予了覆盖该资产的审批范围
 *       （{@code approval_scopes}）；</li>
 *   <li>直属领导无范围时，路由到项目负责人；</li>
 *   <li>项目负责人也不存在时，路由到系统管理员。</li>
 * </ol>
 *
 * <p>此前 {@code ApprovalScope} 实体与仓储存在但没有任何业务引用，审批接口对任何
 * 登录用户开放，与「部门领导只能审批其授权范围内的申请」相悖。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalRoutingService {

    public static final String SCOPE_TEAM = "team";
    public static final String SCOPE_ASSET = "asset";
    public static final String SCOPE_ASSET_CATEGORY = "asset_category";

    private final ApprovalScopeRepository approvalScopeRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;
    private final AssetRepository assetRepository;

    /** 用户是否拥有某个角色码（如 admin / project_leader / team_leader） */
    public boolean hasRole(UUID userId, String roleCode) {
        if (userId == null || roleCode == null) {
            return false;
        }
        List<UUID> roleIds = userRoleRepository.findRoleIdsByUserId(userId);
        if (roleIds == null || roleIds.isEmpty()) {
            return false;
        }
        return roleRepository.findAllById(roleIds).stream()
                .map(Role::getCode)
                .filter(Objects::nonNull)
                .anyMatch(code -> code.equalsIgnoreCase(roleCode));
    }

    /** 是否拥有全局审批权（admin / project_leader 可审批全部资产） */
    public boolean isGlobalApprover(UUID userId) {
        return hasRole(userId, "admin") || hasRole(userId, "project_leader");
    }

    /**
     * 解析某资产访问申请应路由到的审批人。
     *
     * @return 审批人用户ID；若系统中既无负责人也无管理员则返回 null
     */
    public UUID resolveApprover(UUID assetId, UUID applicantId) {
        Asset asset = assetId == null ? null : assetRepository.findById(assetId).orElse(null);

        // 1) 直属领导 + 具备覆盖该资产的审批范围
        if (asset != null && applicantId != null) {
            for (Team team : applicantTeams(applicantId)) {
                UUID leaderId = team.getLeaderId();
                if (leaderId == null || leaderId.equals(applicantId)) {
                    continue;
                }
                if (coversAsset(leaderId, asset)) {
                    log.debug("审批路由命中直属领导: applicant={}, leader={}, asset={}", applicantId, leaderId, assetId);
                    return leaderId;
                }
            }
        }

        // 2) 项目负责人
        UUID projectLeader = firstUserWithRole("project_leader");
        if (projectLeader != null) {
            return projectLeader;
        }

        // 3) 系统管理员
        UUID admin = firstUserWithRole("admin");
        if (admin == null) {
            log.warn("审批路由失败：系统内没有项目负责人或管理员，assetId={}", assetId);
        }
        return admin;
    }

    /**
     * 审批人是否有权审批该资产。
     *
     * <p>admin / project_leader 全量放行；team_leader 需满足其一：
     * 被授予覆盖该资产的审批范围，或本身就是该资产所属团队的负责人。
     */
    public boolean canReview(UUID assetId, UUID reviewerId) {
        if (reviewerId == null) {
            return false;
        }
        if (isGlobalApprover(reviewerId)) {
            return true;
        }
        Asset asset = assetId == null ? null : assetRepository.findById(assetId).orElse(null);
        if (asset == null) {
            return false;
        }
        if (!hasRole(reviewerId, "team_leader")) {
            return false;
        }
        if (coversAsset(reviewerId, asset)) {
            return true;
        }
        // 天然范围：该资产所属团队的负责人
        return asset.getTeamId() != null && teamRepository.findByLeaderId(reviewerId).stream()
                .anyMatch(team -> asset.getTeamId().equals(team.getId()));
    }

    /**
     * 审批人的有效范围是否覆盖目标资产。
     */
    public boolean coversAsset(UUID approverId, Asset asset) {
        if (approverId == null || asset == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        List<ApprovalScope> scopes = approvalScopeRepository.findByApproverId(approverId);
        return scopes.stream()
                .filter(scope -> scope.getExpiresAt() == null || scope.getExpiresAt().isAfter(now))
                .anyMatch(scope -> matches(scope, asset));
    }

    private boolean matches(ApprovalScope scope, Asset asset) {
        String type = scope.getScopeType();
        UUID scopeId = scope.getScopeId();
        if (type == null || scopeId == null) {
            return false;
        }
        return switch (type) {
            case SCOPE_ASSET -> scopeId.equals(asset.getId());
            case SCOPE_TEAM -> scopeId.equals(asset.getTeamId());
            // 资产分类：当前数据模型以 targetType 作为分类维度
            case SCOPE_ASSET_CATEGORY ->
                    asset.getTargetType() != null && scopeId.toString().equalsIgnoreCase(asset.getTargetType());
            default -> false;
        };
    }

    private List<Team> applicantTeams(UUID userId) {
        List<TeamMember> memberships = teamMemberRepository.findByUserId(userId);
        if (memberships == null || memberships.isEmpty()) {
            return List.of();
        }
        List<UUID> teamIds = memberships.stream()
                .map(TeamMember::getTeamId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (teamIds.isEmpty()) {
            return List.of();
        }
        return teamRepository.findAllById(teamIds);
    }

    private UUID firstUserWithRole(String roleCode) {
        return roleRepository.findByCode(roleCode)
                .map(role -> userRoleRepository.findByRoleId(role.getId()).stream()
                        .map(userRole -> userRole.getUserId())
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null))
                .orElse(null);
    }
}
