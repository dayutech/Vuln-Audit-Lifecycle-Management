package com.vulnaudit.config;

import com.vulnaudit.service.UploadApprovalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 上传审批过期清理（t160）
 *
 * <p>默认每小时一次（`app.upload.purge-cron`）；把超过 `purge_after` 仍 PENDING 的请求标记 EXPIRED 并删临时文件，
 * 避免临时区无限增长。拒绝是立即删除，不依赖本任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UploadApprovalPurgeJob {

    private final UploadApprovalService uploadApprovalService;

    @Scheduled(cron = "${app.upload.purge-cron:0 17 * * * *}")
    public void purgeExpiredUploads() {
        int purged = uploadApprovalService.purgeExpired();
        if (purged > 0) {
            log.info("上传审批过期清理完成: count={}", purged);
        }
    }
}
