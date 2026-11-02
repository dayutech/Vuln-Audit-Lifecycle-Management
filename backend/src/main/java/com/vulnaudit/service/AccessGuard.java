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
import com.vulnaudit.repository.TeamMemberRepository;
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

    /** 团队树下溯的最大层级（防止脏数据成环，t51） */
    private static final int MAX_TEAM_DEPTH = 20;

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
    private final TeamMemberRepository teamMemberRepository;
    private final VulnerabilityRepository vulnerabilityRepository;
    private final com.vulnaudit.repository.KnowledgeArticleRepository knowledgeArticleRepository;
    private final com.vulnaudit.repository.NotificationRecipientRepository notificationRecipientRepository;

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

    /**
     * 是否可提交任务审核（t50）
     *
     * <p>矩阵 2.9：提交审核由任务负责人发起，管理层可代为提交。
     * 判定：管理层（admin / project_leader / team_leader）放行；其余需为任务负责人（assignedTo）
     * 或协作者（task_collaborators）。创建者（assignedBy）不再默认放行 —— 与「谁负责谁提交」对齐。
     */
    public boolean canSubmitTaskReview(UUID taskId, Authentication authentication) {
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
                || taskCollaboratorRepository.existsByTaskIdAndUserId(taskId, userId);
    }

    /**
     * 创建任务时的资产写权校验（t50）
     *
     * <p>不带资产的会议/杂项任务（{@code assetId == null}）视为通过；带资产的任务必须对该资产有写权，
     * 避免在无权资产上创建审计任务造成数据污染。
     */
    public boolean canWriteAssetOrAbsent(UUID assetId, Authentication authentication) {
        return assetId == null || canWriteAsset(assetId, authentication);
    }

    // ------------------------------------------------------------------ 知识库

    /**
     * 是否可删除知识文章（t50）
     *
     * <p>矩阵 2.10 规定删除限 admin/project_leader/team_leader；本方法同时放行<b>作者本人</b>，
     * 原因：文章是协作产物，「作者删自己建的条目」属常规能力，只按角色会让工程师无法清理自己的草稿。
     * 若后续要求严格按矩阵执行，只需把本方法改为 {@code return isManager(authentication);}。
     */
    public boolean canDeleteKnowledge(UUID articleId, Authentication authentication) {
        if (isManager(authentication)) {
            return true;
        }
        if (articleId == null) {
            return false;
        }
        UUID userId = currentUserId(authentication);
        if (userId == null) {
            return false;
        }
        return knowledgeArticleRepository.findById(articleId)
                .map(article -> userId.equals(article.getAuthorId()))
                .orElse(false);
    }

    // ------------------------------------------------------------------ 通知

    /**
     * 是否可读取某条通知的投递记录（t50）
     *
     * <p>判定：admin / project_leader 全量可读；其余用户<strong>仅当自己是该通知的接收人之一</strong>
     * 时可读；发送者与其他任何人一律 403（投递记录含渠道与失败原因，属敏感运行信息）。
     */
    public boolean canReadNotificationRecipients(UUID notificationId, Authentication authentication) {
        if (notificationId == null) {
            return false;
        }
        if (isGlobalReader(authentication)) {
            return true;
        }
        UUID userId = currentUserId(authentication);
        if (userId == null) {
            return false;
        }
        return notificationRecipientRepository
                .findByNotificationIdAndUserId(notificationId, userId)
                .isPresent();
    }

    // ------------------------------------------------------------------ 资产域写权（t49）

    /**
     * 是否可在指定团队下创建资产（t49）
     *
     * <ul>
     *   <li>admin / project_leader → 任意团队（含不指定团队，即 {@code teamId == null}）；</li>
     *   <li>team_leader → 必须显式指定团队，且该团队为「自己领导」或「自己所属」的团队；</li>
     *   <li>其它角色 / 解析不到用户 / 团队未指定 → 拒绝（fail-closed）。</li>
     * </ul>
     *
     * @param teamId         资产归属团队（可空，空表示不归属任何团队）
     * @param authentication 当前认证
     * @return true 表示允许在该团队下创建资产
     */
    public boolean canCreateAssetInTeam(java.util.UUID teamId, Authentication authentication) {
        if (isGlobalReader(authentication)) {
            return true;
        }
        if (teamId == null) {
            // 非管理层必须显式指定自己可见的团队，避免「不填团队即绕过」
            return false;
        }
        java.util.UUID userId = currentUserId(authentication);
        if (userId == null) {
            return false;
        }
        if (isLeaderOfTeam(authentication, teamId)) {
            return true;
        }
        // 团队成员（非领导）同样可向所属团队建资产
        return teamMemberRepository.findTeamIdsByUserId(userId).contains(teamId);
    }

    /**
     * 是否可查看某资产的访问申请明细（t49）
     *
     * <p>访问申请含申请说明等敏感信息，仅下列主体可见：
     * 管理层（admin / project_leader / team_leader）、该资产负责人、以及该资产申请的审批人/评审人。
     * 其它情况一律拒绝（fail-closed）。
     *
     * @param assetId        资产ID
     * @param authentication 当前认证
     * @return true 表示可见明细
     */
    public boolean canSeeAssetAccessDetails(java.util.UUID assetId, Authentication authentication) {
        if (assetId == null) {
            return false;
        }
        if (isManager(authentication)) {
            return true;
        }
        java.util.UUID userId = currentUserId(authentication);
        if (userId == null) {
            return false;
        }
        Asset asset = assetRepository.findById(assetId).orElse(null);
        if (asset != null && userId.equals(asset.getOwnerId())) {
            return true;
        }
        // 该资产申请的审批人 / 实际评审人
        return assetAccessRepository.findByAssetId(assetId).stream()
                .anyMatch(access -> userId.equals(access.getApproverId()) || userId.equals(access.getReviewedBy()));
    }

    // ------------------------------------------------------------------ 团队读取范围（t51）

    /**
     * 是否可读取指定团队的成员/用户列表（t51）
     *
     * <p>团队结构属组织架构数据：成员列表对登录用户开放，但<b>只能看到自己可见的团队</b>。
     * 判定顺序：
     * <ol>
     *   <li>admin / project_leader → 全量；</li>
     *   <li>自己领导的团队、自己所属的团队 → 可见；</li>
     *   <li>上述团队的<b>子团队</b>（descendants）→ 可见（团队树展开需要）；</li>
     *   <li>其它情况（含解析不到用户、团队ID为空）→ 拒绝（fail-closed）。</li>
     * </ol>
     *
     * @param teamId         目标团队ID
     * @param authentication 当前认证
     * @return true 表示可见
     */
    public boolean canReadTeam(java.util.UUID teamId, Authentication authentication) {
        if (teamId == null) {
            return false;
        }
        if (isGlobalReader(authentication)) {
            return true;
        }
        java.util.UUID userId = currentUserId(authentication);
        if (userId == null) {
            return false;
        }
        Set<java.util.UUID> ownTeams = new java.util.HashSet<>();
        teamRepository.findByLeaderId(userId).forEach(team -> ownTeams.add(team.getId()));
        ownTeams.addAll(teamMemberRepository.findTeamIdsByUserId(userId));
        if (ownTeams.isEmpty()) {
            return false;
        }
        if (ownTeams.contains(teamId)) {
            return true;
        }
        // 自己团队的子团队（团队树 / 子团队页签）：
        // 这里用 findByParentId 逐层下溯，而不是团队仓储里的 native 递归 CTE
        // （CTE 在 H2 测试库不可用，会在方法级守卫里抛异常变成 500）。
        java.util.ArrayDeque<java.util.UUID> queue = new java.util.ArrayDeque<>(ownTeams);
        java.util.Set<java.util.UUID> visited = new java.util.HashSet<>(ownTeams);
        int guardDepth = 0;
        while (!queue.isEmpty() && guardDepth++ < MAX_TEAM_DEPTH) {
            java.util.UUID parent = queue.poll();
            for (com.vulnaudit.entity.Team child : teamRepository.findByParentId(parent)) {
                java.util.UUID childId = child.getId();
                if (childId == null || !visited.add(childId)) {
                    continue;
                }
                if (childId.equals(teamId)) {
                    return true;
                }
                queue.add(childId);
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ 内容标签写权（t61）

    /**
     * 是否可修改知识库文章的标签（t61）
     *
     * <p>四类资源标签写权统一规则见 README §29：资产标签看资产写权、工具标签看管理层、
     * 知识库/文档标签看「作者/创建者本人 或 管理层」。
     *
     * <p>资源不存在时返回 true，把 404 交给控制器（避免用 403 暴露资源是否存在）。
     *
     * @param articleId      文章ID
     * @param authentication 当前认证
     * @return true 表示允许修改标签
     */
    public boolean canWriteKnowledgeTags(java.util.UUID articleId, Authentication authentication) {
        if (articleId == null) {
            return false;
        }
        if (isManager(authentication)) {
            return true;
        }
        java.util.UUID userId = currentUserId(authentication);
        if (userId == null) {
            return false;
        }
        return knowledgeArticleRepository.findById(articleId)
                .map(article -> userId.equals(article.getAuthorId()))
                // 不存在 → 放行，由控制器返回 404（不泄露存在性）
                .orElse(true);
    }

    /**
     * 是否可修改文档的标签（t61）
     *
     * <p>与 {@link #canWriteKnowledgeTags} 同一规则：作者本人 或 管理层；
     * 文档不存在时返回 true，把 404 交给控制器。
     *
     * @param documentId     文档ID
     * @param authentication 当前认证
     * @return true 表示允许修改标签
     */
    public boolean canWriteDocumentTags(java.util.UUID documentId, Authentication authentication) {
        if (documentId == null) {
            return false;
        }
        if (isManager(authentication)) {
            return true;
        }
        java.util.UUID userId = currentUserId(authentication);
        if (userId == null) {
            return false;
        }
        return documentRepository.findById(documentId)
                .map(document -> userId.equals(document.getAuthorId()))
                .orElse(true);
    }
}
