package com.vulnaudit.service;

import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.AssetFile;
import com.vulnaudit.entity.Document;
import com.vulnaudit.entity.Task;
import com.vulnaudit.repository.AssetAccessRepository;
import com.vulnaudit.repository.AssetDeliverableRepository;
import com.vulnaudit.repository.AssetFileRepository;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.DocumentAccessRepository;
import com.vulnaudit.repository.DocumentRepository;
import com.vulnaudit.repository.TaskCollaboratorRepository;
import com.vulnaudit.repository.TaskRepository;
import com.vulnaudit.repository.TeamRepository;
import com.vulnaudit.repository.UserRepository;
import com.vulnaudit.repository.VulnerabilityRepository;
import com.vulnaudit.security.RoleAuthority;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 数据级授权守卫。
 *
 * <p>Spring Security 只解决“是否登录”，本类解决“这条数据该不该让你看”。
 * 通过 {@code @PreAuthorize("@accessGuard.canReadAsset(#id, authentication)")}
 * 在控制器方法上声明式引用，避免把判断散落到各方法体里。
 *
 * <p>判定口径（与 SRS 3.7.3 / 权限矩阵 3.2 对齐）：
 * <ul>
 *   <li>admin / project_leader：全局可见</li>
 *   <li>team_leader：本团队（teams.leader_id = 当前用户）资产可见</li>
 *   <li>普通成员：仅自己负责的资产、或已获 approved 访问授权的资产</li>
 *   <li>文档：作者本人 / 显式授权 / 所属资产可见 三者之一</li>
 *   <li>任务：负责人 / 创建者 / 协作者 / 管理层</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccessGuard {

    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final AssetAccessRepository assetAccessRepository;
    private final AssetFileRepository assetFileRepository;
    private final AssetDeliverableRepository assetDeliverableRepository;
    private final DocumentRepository documentRepository;
    private final DocumentAccessRepository documentAccessRepository;
    private final TaskRepository taskRepository;
    private final TaskCollaboratorRepository taskCollaboratorRepository;
    private final TeamRepository teamRepository;
    private final VulnerabilityRepository vulnerabilityRepository;

    // ------------------------------------------------------------------ 角色

    private Set<String> authorities(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return Set.of();
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }

    /** 是否为全局可见角色（admin / project_leader） */
    public boolean isGlobalReader(Authentication authentication) {
        Set<String> auths = authorities(authentication);
        return auths.contains(RoleAuthority.ADMIN) || auths.contains(RoleAuthority.PROJECT_LEADER);
    }

    /** 是否管理层（admin / project_leader / team_leader） */
    public boolean isManager(Authentication authentication) {
        return authorities(authentication).stream().anyMatch(RoleAuthority::isManager);
    }

    private UUID currentUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            return null;
        }
        return userRepository.findByUsername(authentication.getName())
                .map(user -> user.getId())
                .orElse(null);
    }

    private boolean isLeaderOfTeam(Authentication authentication, UUID teamId) {
        if (teamId == null) {
            return false;
        }
        UUID userId = currentUserId(authentication);
        if (userId == null) {
            return false;
        }
        return teamRepository.findByLeaderId(userId).stream()
                .anyMatch(team -> teamId.equals(team.getId()));
    }

    /**
     * 当前用户可见的资产 ID 集合。
     *
     * <p>用于列表类接口做范围过滤（漏洞、文档等以资产为归属的资源）。
     *
     * @return null 表示不限制（admin / project_leader）；否则为可见资产集合，可能为空
     */
    public Set<UUID> visibleAssetIds(Authentication authentication) {
        if (isGlobalReader(authentication)) {
            return null;
        }
        UUID userId = currentUserId(authentication);
        if (userId == null) {
            return Set.of();
        }
        Set<UUID> ids = new java.util.HashSet<>();
        // 1) 自己负责的资产
        assetRepository.findByOwnerId(userId).stream()
                .map(Asset::getId)
                .filter(java.util.Objects::nonNull)
                .forEach(ids::add);
        // 2) 已批准访问的资产
        assetAccessRepository.findByUserIdAndStatus(userId, "approved").stream()
                .map(access -> access.getAssetId())
                .filter(java.util.Objects::nonNull)
                .forEach(ids::add);
        // 3) 自己作为负责人的团队下的资产
        teamRepository.findByLeaderId(userId).stream()
                .map(team -> team.getId())
                .filter(java.util.Objects::nonNull)
                .forEach(teamId -> assetRepository.findByTeamId(teamId).stream()
                        .map(Asset::getId)
                        .filter(java.util.Objects::nonNull)
                        .forEach(ids::add));
        return ids;
    }

    // ------------------------------------------------------------------ 资产

    /** 是否可读取资产及其从属资源（元数据 / 文件 / 交付物 / 环境） */
    public boolean canReadAsset(UUID assetId, Authentication authentication) {
        if (assetId == null) {
            return false;
        }
        // 管理层先放行：不存在的资源交由控制器返回 404，而不是 403
        if (isGlobalReader(authentication)) {
            return true;
        }
        Asset asset = assetRepository.findById(assetId).orElse(null);
        if (asset == null) {
            // 非管理层对不存在的资源同样返回 403，避免通过状态码枚举资源
            return false;
        }
        UUID userId = currentUserId(authentication);
        if (userId != null) {
            if (userId.equals(asset.getOwnerId())) {
                return true;
            }
            if (assetAccessRepository.existsByAssetIdAndUserIdAndStatus(assetId, userId, "approved")) {
                return true;
            }
        }
        return isLeaderOfTeam(authentication, asset.getTeamId());
    }

    /** 是否可写资产（仅负责人、管理层） */
    public boolean canWriteAsset(UUID assetId, Authentication authentication) {
        if (assetId == null) {
            return false;
        }
        if (isManager(authentication)) {
            return true;
        }
        Asset asset = assetRepository.findById(assetId).orElse(null);
        UUID userId = currentUserId(authentication);
        return asset != null && userId != null && userId.equals(asset.getOwnerId());
    }

    /** 以文件 ID 判断所属资产可读性 */
    public boolean canReadAssetFile(UUID fileId, Authentication authentication) {
        if (fileId == null) {
            return false;
        }
        if (isManager(authentication)) {
            return true;
        }
        AssetFile file = assetFileRepository.findById(fileId).orElse(null);
        return file != null && canReadAsset(file.getAssetId(), authentication);
    }

    /** 以文件 ID 判断所属资产可写性 */
    public boolean canWriteAssetFile(UUID fileId, Authentication authentication) {
        if (fileId == null) {
            return false;
        }
        if (isManager(authentication)) {
            return true;
        }
        AssetFile file = assetFileRepository.findById(fileId).orElse(null);
        return file != null && canWriteAsset(file.getAssetId(), authentication);
    }

    /** 以交付物 ID 判断所属资产可读性 */
    public boolean canReadDeliverable(UUID deliverableId, Authentication authentication) {
        if (deliverableId == null) {
            return false;
        }
        if (isManager(authentication)) {
            return true;
        }
        return assetDeliverableRepository.findById(deliverableId)
                .map(deliverable -> canReadAsset(deliverable.getAssetId(), authentication))
                .orElse(false);
    }

    /** 以交付物 ID 判断所属资产可写性 */
    public boolean canWriteDeliverable(UUID deliverableId, Authentication authentication) {
        if (deliverableId == null) {
            return false;
        }
        if (isManager(authentication)) {
            return true;
        }
        return assetDeliverableRepository.findById(deliverableId)
                .map(deliverable -> canWriteAsset(deliverable.getAssetId(), authentication))
                .orElse(false);
    }

    // ------------------------------------------------------------------ 漏洞

    /**
     * 以漏洞 ID 判断其所属资产是否可读。
     *
     * <p>用于漏洞从属资源（附件、POC 等）的下载/查看授权：先由 vulnerabilityId 反查 assetId，
     * 再复用 {@link #canReadAsset(UUID, Authentication)} 的口径（管理员/项目负责人全局可见，
     * 小组长看本团队资产，成员看自己负责或已获批访问的资产）。
     */
    public boolean canReadVulnerability(UUID vulnerabilityId, Authentication authentication) {
        if (vulnerabilityId == null) {
            return false;
        }
        // 管理层先放行：不存在的漏洞交由控制器返回 404，而不是 403
        if (isGlobalReader(authentication)) {
            return true;
        }
        return vulnerabilityRepository.findById(vulnerabilityId)
                .map(vulnerability -> canReadAsset(vulnerability.getAssetId(), authentication))
                .orElse(false);
    }

    /**
     * 以漏洞 ID 判断其所属资产是否可写。
     *
     * <p>用于漏洞的修改、状态流转、标签与附件维护——**不能用只读口径代替**，
     * 否则「能改别人资产的漏洞状态」会与「看不到该漏洞」并存。
     * 判定复用 {@link #canWriteAsset(UUID, Authentication)}：管理层（admin/project_leader/team_leader）
     * 放行；其余用户需为漏洞所属资产的负责人。
     */
    public boolean canWriteVulnerability(UUID vulnerabilityId, Authentication authentication) {
        if (vulnerabilityId == null) {
            return false;
        }
        if (isManager(authentication)) {
            return true;
        }
        return vulnerabilityRepository.findById(vulnerabilityId)
                .map(vulnerability -> canWriteAsset(vulnerability.getAssetId(), authentication))
                .orElse(false);
    }

    // ------------------------------------------------------------------ 文档

    /** 是否可读文档内容（列表可见，内容需授权） */
    public boolean canReadDocument(UUID documentId, Authentication authentication) {
        if (documentId == null) {
            return false;
        }
        if (isGlobalReader(authentication)) {
            return true;
        }
        Document document = documentRepository.findById(documentId).orElse(null);
        if (document == null) {
            return false;
        }
        UUID userId = currentUserId(authentication);
        if (userId != null) {
            if (userId.equals(document.getAuthorId()) || userId.equals(document.getCreatedBy())) {
                return true;
            }
            if (documentAccessRepository.existsByDocumentIdAndUserIdAndAccessType(documentId, userId, "read")) {
                return true;
            }
        }
        // 文档挂在可见资产下时，按资产可见性放行
        return document.getAssetId() != null && canReadAsset(document.getAssetId(), authentication);
    }

    /** 是否可修改/删除文档（作者本人或管理层） */
    public boolean canWriteDocument(UUID documentId, Authentication authentication) {
        if (documentId == null) {
            return false;
        }
        if (isManager(authentication)) {
            return true;
        }
        Document document = documentRepository.findById(documentId).orElse(null);
        UUID userId = currentUserId(authentication);
        return document != null && userId != null
                && (userId.equals(document.getAuthorId()) || userId.equals(document.getCreatedBy()));
    }

    // ------------------------------------------------------------------ 任务

    /** 是否可读任务（负责人 / 创建者 / 协作者 / 管理层） */
    public boolean canReadTask(UUID taskId, Authentication authentication) {
        if (isManager(authentication)) {
            return true;
        }
        if (taskId == null) {
            return false;
        }
        Task task = taskRepository.findById(taskId).orElse(null);
        if (task == null) {
            return false;
        }
        UUID userId = currentUserId(authentication);
        if (userId == null) {
            return false;
        }
        return userId.equals(task.getAssignedTo())
                || userId.equals(task.getAssignedBy())
                || taskCollaboratorRepository.existsByTaskIdAndUserId(taskId, userId);
    }

    /** 是否可删除任务（仅管理层或任务创建者） */
    public boolean canDeleteTask(UUID taskId, Authentication authentication) {
        if (isManager(authentication)) {
            return true;
        }
        if (taskId == null) {
            return false;
        }
        Task task = taskRepository.findById(taskId).orElse(null);
        UUID userId = currentUserId(authentication);
        return task != null && userId != null && userId.equals(task.getAssignedBy());
    }
}
