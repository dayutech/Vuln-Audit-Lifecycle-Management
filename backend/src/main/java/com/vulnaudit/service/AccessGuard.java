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
 * <p><b>单一事实来源（t46 统一）</b>：资产可见性只有一个来源 —— {@link #visibleAssetIds(Authentication)}。
 * <ul>
 *   <li>列表接口用 {@code visibleAssetIds(...)} 过滤范围（{@code null} = 不限制）；</li>
 *   <li>详情/从属资源用 {@link #canReadAsset(UUID, Authentication)}，其实现就是
 *       {@code visibleAssetIds(...)} 的成员判定；</li>
 *   <li>写权限 {@link #canWriteAsset(UUID, Authentication)} 以读为前提（写 ⊂ 读），
 *       不会出现「能写不能读」或「列表可见但详情 403」。</li>
 * </ul>
 * <b>列表与详情共用同一可见性判定，不得各自实现。</b>
 *
 * <p>判定口径（与 SRS 3.7.3 / 权限矩阵 3.2 对齐）：
 * <ul>
 *   <li>admin / project_leader：全局可见（{@code visibleAssetIds} 返回 {@code null}）</li>
 *   <li>team_leader：本团队（teams.leader_id = 当前用户）资产可见</li>
 *   <li>普通成员：仅自己负责的资产、或已获 approved 访问授权的资产（团队普通成员不自动可见）</li>
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
     * 是否可管理指定团队（团队 CRUD / 成员增删的数据级守卫）
     *
     * <p>判定规则：
     * <ul>
     *   <li>admin / project_leader → 可管理任意团队；</li>
     *   <li>team_leader → 仅可管理「自己领导」的团队（{@code teams.leader_id = 当前用户}）；</li>
     *   <li>其它角色 / 团队ID为空 / 解析不到用户 → 一律拒绝（fail-closed）。</li>
     * </ul>
     *
     * @param teamId         目标团队ID
     * @param authentication 当前认证
     * @return true 表示可管理
     */
    public boolean canManageTeam(UUID teamId, Authentication authentication) {
        if (teamId == null) {
            return false;
        }
        if (isGlobalReader(authentication)) {
            return true;
        }
        return isLeaderOfTeam(authentication, teamId);
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

    /**
     * 是否可读取资产及其从属资源（元数据 / 文件 / 交付物 / 环境）
     *
     * <p><b>实现即 {@link #visibleAssetIds(Authentication)}（单一事实来源）</b>：
     * 管理层放行（{@code null} 表示不限制）；其余用户仅当资产在可见集合内。
     * 列表过滤与详情判定因此天然一致，不存在「列表能看到、点进去 403」。
     */
    public boolean canReadAsset(UUID assetId, Authentication authentication) {
        if (assetId == null) {
            return false;
        }
        Set<UUID> visible = visibleAssetIds(authentication);
        if (visible == null) {
            // 管理层（admin / project_leader）：放行，不存在的资源交由控制器返回 404
            return true;
        }
        // 非管理层：未解析到用户的空集合、或不存在的资源，同样返回 false（避免用状态码枚举资源）
        return visible.contains(assetId);
    }

    /**
     * 是否可写资产。
     *
     * <p><b>写 ⊂ 读</b>：先要求 {@link #canReadAsset} 通过，再判定写身份，
     * 避免出现「能写但不能读」以及「改完刷新就 403」的割裂状态。
     * <ul>
     *   <li>写身份：管理层（admin / project_leader / team_leader）或该资产负责人；</li>
     *   <li>由此 team_leader 只能写「本团队（自己领导）可见」的资产 —— 与读口径一致，
     *       属 t46 的有意收窄（原先可写任意资产但读不到，属不自洽状态）。</li>
     * </ul>
     */
    public boolean canWriteAsset(UUID assetId, Authentication authentication) {
        if (assetId == null) {
            return false;
        }
        if (!canReadAsset(assetId, authentication)) {
            return false;
        }
        if (isManager(authentication)) {
            return true;
        }
        Asset asset = assetRepository.findById(assetId).orElse(null);
        UUID userId = currentUserId(authentication);
        return asset != null && userId != null && userId.equals(asset.getOwnerId());
    }

    /** 以文件 ID 判断所属资产可读性（不另设口径，完全复用 canReadAsset） */
    public boolean canReadAssetFile(UUID fileId, Authentication authentication) {
        if (fileId == null) {
            return false;
        }
        AssetFile file = assetFileRepository.findById(fileId).orElse(null);
        if (file == null) {
            // 管理层放行，不存在的文件交由控制器返回 404；非管理层 403（防枚举）
            return isGlobalReader(authentication);
        }
        return canReadAsset(file.getAssetId(), authentication);
    }

    /** 以文件 ID 判断所属资产可写性（写 ⊂ 读，复用 canWriteAsset） */
    public boolean canWriteAssetFile(UUID fileId, Authentication authentication) {
        if (fileId == null) {
            return false;
        }
        AssetFile file = assetFileRepository.findById(fileId).orElse(null);
        if (file == null) {
            // 管理层放行 → 控制器 404；其余 403
            return isManager(authentication);
        }
        return canWriteAsset(file.getAssetId(), authentication);
    }

    /** 以交付物 ID 判断所属资产可读性（不另设口径，完全复用 canReadAsset） */
    public boolean canReadDeliverable(UUID deliverableId, Authentication authentication) {
        if (deliverableId == null) {
            return false;
        }
        var deliverable = assetDeliverableRepository.findById(deliverableId).orElse(null);
        if (deliverable == null) {
            return isGlobalReader(authentication);
        }
        return canReadAsset(deliverable.getAssetId(), authentication);
    }

    /** 以交付物 ID 判断所属资产可写性（写 ⊂ 读，复用 canWriteAsset） */
    public boolean canWriteDeliverable(UUID deliverableId, Authentication authentication) {
        if (deliverableId == null) {
            return false;
        }
        var deliverable = assetDeliverableRepository.findById(deliverableId).orElse(null);
        if (deliverable == null) {
            return isManager(authentication);
        }
        return canWriteAsset(deliverable.getAssetId(), authentication);
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
