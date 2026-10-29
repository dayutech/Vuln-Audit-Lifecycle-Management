package com.vulnaudit.service.notify;

import lombok.Builder;

import java.util.UUID;

/**
 * 通知发送报文
 *
 * <p>通道无关的通知载荷，由 {@link NotificationChannelSender} 各自渲染为邮件或 JSON。
 *
 * @param notificationId 通知ID
 * @param userId         接收用户ID
 * @param username       接收用户名称
 * @param recipientEmail 接收人邮箱（邮件通道使用）
 * @param recipientPhone 接收人手机号（短信通道预留）
 * @param type           通知类型
 * @param title          通知标题
 * @param content        通知内容
 * @param priority       优先级
 * @param relatedType    关联业务类型
 * @param relatedId      关联业务ID
 */
@Builder
public record NotificationMessage(
        UUID notificationId,
        UUID userId,
        String username,
        String recipientEmail,
        String recipientPhone,
        String type,
        String title,
        String content,
        String priority,
        String relatedType,
        UUID relatedId
) {
}
