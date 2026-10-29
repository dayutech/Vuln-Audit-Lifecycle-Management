package com.vulnaudit.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 审计日志过期清理任务。
 *
 * <p>背景：{@code AuditLogService#cleanOldLogs} 早已存在但从未被调度调用，
 * 审计表只会无限增长，与 SRS 3.7.2「审计日志按保留期归档/清理」不符。
 *
 * <p>默认每天 03:30 执行一次（{@code app.audit.cleanup-cron}），保留天数由
 * {@code app.audit.retention-days} 配置（默认 180 天）。执行结果在 INFO 日志中输出
 * 「清理条数」，便于运维核查；失败只记 ERROR，不影响主流程。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogCleanupJob {

    private final AuditLogService auditLogService;

    /** 审计日志保留天数，默认 180 天 */
    @Value("${app.audit.retention-days:180}")
    private int retentionDays;

    /** 每日 03:30 执行（服务器本地时区） */
    @Scheduled(cron = "${app.audit.cleanup-cron:0 30 3 * * *}")
    public void scheduledClean() {
        try {
            int deleted = cleanOnce();
            log.info("审计日志定时清理完成: 保留天数={}, 清理条数={}", retentionDays, deleted);
        } catch (Exception e) {
            // 定时任务异常不能影响主流程
            log.error("审计日志定时清理失败", e);
        }
    }

    /**
     * 执行一次清理（抽出便于单元测试直接调用）。
     *
     * @return 实际删除条数
     */
    public int cleanOnce() {
        return auditLogService.cleanOldLogs(retentionDays);
    }

    /** 当前配置的保留天数（测试与运维排查用） */
    public int retentionDays() {
        return retentionDays;
    }
}
