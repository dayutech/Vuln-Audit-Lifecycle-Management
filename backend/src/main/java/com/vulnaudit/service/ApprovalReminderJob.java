package com.vulnaudit.service;

import com.vulnaudit.entity.AssetAccess;
import com.vulnaudit.repository.AssetAccessRepository;
import com.vulnaudit.repository.UserRoleRepository;
import com.vulnaudit.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * 审批超时提醒与自动升级。
 *
 * <p>对齐 SRS 3.7.3.3：
 * <ul>
 *   <li>审批超时时间 72 小时：超时后提醒审批人；</li>
 *   <li>超过 7 天未审批：自动升级到上级（项目负责人，缺省则管理员），并改派审批人。</li>
 * </ul>
 *
 * <p>此前系统没有任何调度器，这两条规则完全缺失。定时任务默认每小时执行一次，
 * 阈值可通过 {@code app.approval.*} 配置。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalReminderJob {

    private final AssetAccessRepository assetAccessRepository;
    private final NotificationService notificationService;
    private final UserRoleRepository userRoleRepository;
    private final RoleRepository roleRepository;

    /** 超过该小时数未处理则提醒审批人 */
    @Value("${app.approval.remind-after-hours:72}")
    private long remindAfterHours;

    /** 超过该天数未处理则升级到上级 */
    @Value("${app.approval.escalate-after-days:7}")
    private long escalateAfterDays;

    @Scheduled(cron = "${app.approval.cron:0 0 * * * *}")
    public void scheduledRun() {
        try {
            int actions = runOnce(LocalDateTime.now());
            if (actions > 0) {
                log.info("审批超时任务执行完成: 处理 {} 条", actions);
            }
        } catch (Exception e) {
            // 定时任务异常不能影响主流程
            log.error("审批超时任务执行失败", e);
        }
    }

    /**
     * 执行一轮超时检查（抽出便于单元测试直接调用）。
     *
     * @param now 参照时间
     * @return 处理的申请条数（提醒 + 升级）
     */
    @Transactional
    public int runOnce(LocalDateTime now) {
        List<AssetAccess> pending = assetAccessRepository.findByStatus("pending");
        if (pending == null) {
            return 0;
        }
        int actions = 0;
        for (AssetAccess access : pending) {
            if (access.getCreatedAt() == null) {
                continue;
            }
            Duration age = Duration.between(access.getCreatedAt(), now);
            if (age.toDays() >= escalateAfterDays) {
                actions += escalate(access);
            } else if (age.toHours() >= remindAfterHours) {
                actions += remind(access, age);
            }
        }
        return actions;
    }

    private int remind(AssetAccess access, Duration age) {
        UUID approverId = access.getApproverId();
        if (approverId == null) {
            return 0;
        }
        String title = "访问申请待审批提醒";
        String content = String.format("你有 1 条资产访问申请已等待 %d 小时仍未审批（申请ID: %s，资产ID: %s）。",
                age.toHours(), access.getId(), access.getAssetId());
        notificationService.sendNotification(approverId, "access_reminder", title, content);
        log.info("审批超时提醒: requestId={}, approverId={}, hours={}", access.getId(), approverId, age.toHours());
        return 1;
    }

    private int escalate(AssetAccess access) {
        UUID currentApprover = access.getApproverId();
        UUID superior = resolveSuperior(currentApprover);
        if (superior == null || superior.equals(currentApprover)) {
            // 找不到更上级，只做提醒
            return remind(access, Duration.between(access.getCreatedAt(), LocalDateTime.now()));
        }
        access.setApproverId(superior);
        assetAccessRepository.save(access);

        String content = String.format("访问申请（申请ID: %s，资产ID: %s）超过 %d 天未审批，已自动升级给你处理。",
                access.getId(), access.getAssetId(), escalateAfterDays);
        notificationService.sendNotification(superior, "access_escalated", "访问申请已升级", content);
        if (currentApprover != null) {
            notificationService.sendNotification(currentApprover, "access_escalated",
                    "访问申请已升级", "你有一条申请超时未处理，已自动升级至上级审批。");
        }
        log.info("审批自动升级: requestId={}, from={}, to={}", access.getId(), currentApprover, superior);
        return 1;
    }

    /**
     * 找上级：当前审批人 → 项目负责人 → 管理员（跳过自己）。
     */
    private UUID resolveSuperior(UUID currentApprover) {
        UUID projectLeader = firstUserWithRole("project_leader");
        if (projectLeader != null && !projectLeader.equals(currentApprover)) {
            return projectLeader;
        }
        UUID admin = firstUserWithRole("admin");
        if (admin != null && !admin.equals(currentApprover)) {
            return admin;
        }
        return null;
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
