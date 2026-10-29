package com.vulnaudit.controller;

import com.vulnaudit.entity.Asset;
import com.vulnaudit.entity.AssetAccess;
import com.vulnaudit.repository.AssetAccessRepository;
import com.vulnaudit.repository.AssetRepository;
import com.vulnaudit.service.ApprovalReminderJob;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 审批超时提醒与自动升级测试（SRS 3.7.3.3）。
 */
@DisplayName("审批超时任务测试")
public class ApprovalReminderJobTest extends BaseTest {

    @Autowired
    private ApprovalReminderJob approvalReminderJob;

    @Autowired
    private AssetAccessRepository assetAccessRepository;

    @Autowired
    private AssetRepository assetRepository;

    private Asset createAsset() {
        return assetRepository.save(Objects.requireNonNull(Asset.builder()
                .name("超时审批测试资产")
                .code("AST-REMIND-" + System.nanoTime())
                .targetType("Web应用")
                .targetSource("客户委托")
                .priority("P2")
                .status("active")
                .ownerId(adminUserId)
                .build()));
    }

    private AssetAccess createPending(Asset asset, LocalDateTime createdAt) {
        AssetAccess access = AssetAccess.builder()
                .assetId(asset.getId())
                .userId(engineerUserId)
                .approverId(leaderUserId)
                .status("pending")
                .canViewDocuments(false)
                .canViewEnvironments(false)
                .canViewFiles(true)
                .requestNote("超时测试")
                .build();
        access.setCreatedAt(createdAt);
        return assetAccessRepository.save(Objects.requireNonNull(access));
    }

    @Test
    @DisplayName("超过 72 小时未审批 → 提醒审批人")
    void remindWhenOver72Hours() {
        Asset asset = createAsset();
        createPending(asset, LocalDateTime.now().minusHours(80));

        int actions = approvalReminderJob.runOnce(LocalDateTime.now());

        assertEquals(1, actions, "应产生 1 次超时提醒");
        AssetAccess reloaded = assetAccessRepository.findByAssetIdAndUserId(asset.getId(), engineerUserId)
                .orElseThrow();
        assertEquals(leaderUserId, reloaded.getApproverId(), "提醒阶段不改变审批人");
    }

    @Test
    @DisplayName("超过 7 天未审批 → 自动升级到管理员并改派")
    void escalateWhenOver7Days() {
        Asset asset = createAsset();
        createPending(asset, LocalDateTime.now().minusDays(8));

        int actions = approvalReminderJob.runOnce(LocalDateTime.now());

        assertEquals(1, actions, "应产生 1 次升级动作");
        AssetAccess reloaded = assetAccessRepository.findByAssetIdAndUserId(asset.getId(), engineerUserId)
                .orElseThrow();
        assertEquals(adminUserId, reloaded.getApproverId(),
                "系统内无 project_leader 时应升级到管理员");
    }

    @Test
    @DisplayName("未超时的申请不产生动作")
    void noActionWhenFresh() {
        Asset asset = createAsset();
        createPending(asset, LocalDateTime.now().minusHours(1));

        assertEquals(0, approvalReminderJob.runOnce(LocalDateTime.now()));
    }
}
