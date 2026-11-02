package com.vulnaudit.service;

import com.vulnaudit.entity.AssetTag;
import com.vulnaudit.entity.VulnerabilityAttachment;
import com.vulnaudit.entity.VulnerabilityTag;
import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.repository.AssetTagRepository;
import com.vulnaudit.repository.DocumentRepository;
import com.vulnaudit.repository.DocumentTagRepository;
import com.vulnaudit.repository.EnvironmentRepository;
import com.vulnaudit.repository.EnvironmentSnapshotRepository;
import com.vulnaudit.repository.KnowledgeArticleRepository;
import com.vulnaudit.repository.NotificationRecipientRepository;
import com.vulnaudit.repository.NotificationRepository;
import com.vulnaudit.repository.TaskRepository;
import com.vulnaudit.repository.TeamMemberRepository;
import com.vulnaudit.repository.TeamRepository;
import com.vulnaudit.repository.ToolRepository;
import com.vulnaudit.repository.VulnerabilityAttachmentRepository;
import com.vulnaudit.repository.VulnerabilityRepository;
import com.vulnaudit.repository.VulnerabilityTagRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 子资源写操作的存在性守卫（t65）
 *
 * <p>背景：项目里反复出现「对不存在的父资源写子资源静默成功」——写孤儿标签行、删 0 行仍返回 200、
 * 给不存在的漏洞上传附件等（t55 工具标签、t61 文章/文档标签、更早的工具包上传）。
 *
 * <p>统一规则（所有子资源写端点必须遵守）：
 * <ol>
 *   <li><b>父资源不存在 → 404</b>（不是 200/400/403）；</li>
 *   <li><b>子资源不属于该父资源 → 404</b>（与「不存在」同一状态码，避免用状态码枚举资源）；</li>
 *   <li>正常路径语义不变（该 201 的仍 201）。</li>
 * </ol>
 *
 * <p>实现集中在 {@link SubresourceGuard}，控制器只调用一行 `requireXxx(...)`，
 * 不再各自 if/else，避免第四次遗漏。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SubresourceGuard {

    private final AssetRepository assetRepository;
    private final AssetTagRepository assetTagRepository;
    private final VulnerabilityRepository vulnerabilityRepository;
    private final VulnerabilityTagRepository vulnerabilityTagRepository;
    private final VulnerabilityAttachmentRepository vulnerabilityAttachmentRepository;
    private final KnowledgeArticleRepository knowledgeArticleRepository;
    private final DocumentRepository documentRepository;
    private final DocumentTagRepository documentTagRepository;
    private final ToolRepository toolRepository;
    private final TeamRepository teamRepository;
    private final TeamMemberRepository teamMemberRepository;
    private final EnvironmentRepository environmentRepository;
    private final EnvironmentSnapshotRepository environmentSnapshotRepository;
    private final TaskRepository taskRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationRecipientRepository notificationRecipientRepository;

    // ------------------------------------------------------------------ 父资源

    public void requireAsset(UUID assetId) {
        if (assetId == null || !assetRepository.existsById(assetId)) {
            throw new BusinessException(ErrorCode.ASSET_NOT_FOUND);
        }
    }

    public void requireVulnerability(UUID vulnerabilityId) {
        if (vulnerabilityId == null || !vulnerabilityRepository.existsById(vulnerabilityId)) {
            throw new BusinessException(ErrorCode.VULNERABILITY_NOT_FOUND);
        }
    }

    public void requireKnowledgeArticle(UUID articleId) {
        if (articleId == null || !knowledgeArticleRepository.existsById(articleId)) {
            throw new BusinessException(ErrorCode.KNOWLEDGE_NOT_FOUND);
        }
    }

    public void requireDocument(UUID documentId) {
        if (documentId == null || !documentRepository.existsById(documentId)) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
    }

    public void requireTool(UUID toolId) {
        if (toolId == null || !toolRepository.existsById(toolId)) {
            throw new BusinessException(ErrorCode.TOOL_NOT_FOUND);
        }
    }

    public void requireTeam(UUID teamId) {
        if (teamId == null || !teamRepository.existsById(teamId)) {
            throw new BusinessException(ErrorCode.TEAM_NOT_FOUND);
        }
    }

    public void requireEnvironment(UUID environmentId) {
        if (environmentId == null || !environmentRepository.existsById(environmentId)) {
            throw new BusinessException(ErrorCode.ENVIRONMENT_NOT_FOUND);
        }
    }

    public void requireTask(UUID taskId) {
        if (taskId == null || !taskRepository.existsById(taskId)) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }
    }

    public void requireNotification(UUID notificationId) {
        if (notificationId == null || !notificationRepository.existsById(notificationId)) {
            throw new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND);
        }
    }

    // ------------------------------------------------------------------ 子资源归属

    /** 资产标签必须存在且属于该资产 */
    public AssetTag requireAssetTag(UUID assetId, UUID tagId) {
        requireAsset(assetId);
        return assetTagRepository.findById(tagId == null ? null : tagId)
                .filter(tag -> assetId.equals(tag.getAssetId()))
                .orElseThrow(() -> notFound("资产标签不存在或不属于该资产", assetId, tagId));
    }

    /** 漏洞标签必须存在且属于该漏洞 */
    public VulnerabilityTag requireVulnerabilityTag(UUID vulnerabilityId, UUID tagId) {
        requireVulnerability(vulnerabilityId);
        return vulnerabilityTagRepository.findById(tagId == null ? null : tagId)
                .filter(tag -> vulnerabilityId.equals(tag.getVulnerabilityId()))
                .orElseThrow(() -> notFound("漏洞标签不存在或不属于该漏洞", vulnerabilityId, tagId));
    }

    /** 文档标签必须存在且属于该文档 */
    public void requireDocumentTag(UUID documentId, UUID tagId) {
        requireDocument(documentId);
        documentTagRepository.findById(tagId == null ? null : tagId)
                .filter(tag -> documentId.equals(tag.getDocumentId()))
                .orElseThrow(() -> notFound("文档标签不存在或不属于该文档", documentId, tagId));
    }

    /** 漏洞附件必须存在且属于该漏洞 */
    public VulnerabilityAttachment requireVulnerabilityAttachment(UUID vulnerabilityId, UUID attachmentId) {
        requireVulnerability(vulnerabilityId);
        return vulnerabilityAttachmentRepository.findById(attachmentId == null ? null : attachmentId)
                .filter(item -> vulnerabilityId.equals(item.getVulnerabilityId()))
                .orElseThrow(() -> notFound("附件不存在或不属于该漏洞", vulnerabilityId, attachmentId));
    }

    /** 团队成员关系必须存在（移除成员时） */
    public void requireTeamMember(UUID teamId, UUID userId) {
        requireTeam(teamId);
        if (userId == null || !teamMemberRepository.existsByTeamIdAndUserId(teamId, userId)) {
            throw notFound("该用户不是该团队成员", teamId, userId);
        }
    }

    /** 环境快照必须存在且属于该环境 */
    public void requireEnvironmentSnapshot(UUID environmentId, UUID snapshotId) {
        requireEnvironment(environmentId);
        environmentSnapshotRepository.findById(snapshotId == null ? null : snapshotId)
                .filter(snapshot -> environmentId.equals(snapshot.getEnvironmentId()))
                .orElseThrow(() -> notFound("快照不存在或不属于该环境", environmentId, snapshotId));
    }

    /** 通知接收人关系必须存在（标记已读 / 删除通知） */
    public void requireNotificationRecipient(UUID notificationId, UUID userId) {
        requireNotification(notificationId);
        if (userId == null
                || notificationRecipientRepository.findByNotificationIdAndUserId(notificationId, userId).isEmpty()) {
            throw notFound("通知不存在或不属于当前用户", notificationId, userId);
        }
    }

    // ------------------------------------------------------------------ 内部

    private BusinessException notFound(String message, UUID parentId, UUID childId) {
        log.warn("子资源归属校验失败: {} (parent={}, child={})", message, parentId, childId);
        return new BusinessException(ErrorCode.NOT_FOUND, message);
    }
}
