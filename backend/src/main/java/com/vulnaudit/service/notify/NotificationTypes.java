package com.vulnaudit.service.notify;

import com.vulnaudit.exception.BusinessException;
import com.vulnaudit.exception.ErrorCode;

import jakarta.annotation.Nullable;
import java.util.List;

/**
 * 通知类型常量与校验
 *
 * <p>背景：{@code notifications.type} 列早期为 VARCHAR(20)，而默认值
 * {@code vulnerability_discovered}（23 字符）会被数据库拒绝并返回 500。
 * 收敛方案：
 * <ol>
 *     <li>数据库列扩到 VARCHAR(50)（Flyway V15）；实体 {@code @Column(length = 50)} 同步；</li>
 *     <li>全仓写入该列的 type 字面量集中到本类的常量，避免再次超长；</li>
 *     <li>写入前统一走 {@link #requireValid(String)}，超长直接 400，不再让数据库抛 500。</li>
 * </ol>
 */
public final class NotificationTypes {

    /**
     * 通知类型最大长度，与 notifications.type 列宽、实体 @Column(length) 保持一致
     */
    public static final int MAX_LENGTH = 50;

    /** 漏洞刚被发现（默认值，23 字符，旧列宽 20 时正是它触发 500） */
    public static final String VULNERABILITY_DISCOVERED = "vulnerability_discovered";

    /** 漏洞状态/信息变更 */
    public static final String VULNERABILITY_UPDATED = "vulnerability_updated";

    /** 资产访问申请通过 */
    public static final String ASSET_ACCESS_APPROVED = "asset_access_approved";

    /** 资产访问申请被驳回 */
    public static final String ASSET_ACCESS_REJECTED = "asset_access_rejected";

    /** 任务分配 */
    public static final String TASK_ASSIGNED = "task_assigned";

    /** 文档评论 */
    public static final String DOCUMENT_COMMENT = "document_comment";

    /** 系统公告 */
    public static final String SYSTEM_ANNOUNCEMENT = "system_announcement";

    /** 审批超时提醒 */
    public static final String ACCESS_REMINDER = "access_reminder";

    /** 审批自动升级 */
    public static final String ACCESS_ESCALATED = "access_escalated";

    /** 系统通知（type 缺省时的兜底值） */
    public static final String SYSTEM = "system";

    /**
     * 全部已知类型（均为常量，长度必须 <= {@link #MAX_LENGTH}）
     */
    public static final List<String> ALL = List.of(
            VULNERABILITY_DISCOVERED,
            VULNERABILITY_UPDATED,
            ASSET_ACCESS_APPROVED,
            ASSET_ACCESS_REJECTED,
            TASK_ASSIGNED,
            DOCUMENT_COMMENT,
            SYSTEM_ANNOUNCEMENT,
            ACCESS_REMINDER,
            ACCESS_ESCALATED,
            SYSTEM
    );

    private NotificationTypes() {
    }

    /**
     * 归一化并校验通知类型
     *
     * @param type 原始类型（可为 null / 空白）
     * @return 非空类型；入参为空时返回 {@link #SYSTEM}
     * @throws BusinessException 长度超过 {@link #MAX_LENGTH} 时抛出 400，避免落库触发 500
     */
    public static String requireValid(@Nullable String type) {
        String normalized = normalize(type);
        if (normalized == null) {
            return SYSTEM;
        }
        if (normalized.length() > MAX_LENGTH) {
            throw new BusinessException(ErrorCode.BAD_REQUEST,
                    "通知类型长度不能超过 " + MAX_LENGTH + " 个字符，当前 " + normalized.length() + " 个字符");
        }
        return normalized;
    }

    /**
     * 归一化（trim），空白返回 null
     */
    @Nullable
    public static String normalize(@Nullable String type) {
        if (type == null) {
            return null;
        }
        String trimmed = type.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
