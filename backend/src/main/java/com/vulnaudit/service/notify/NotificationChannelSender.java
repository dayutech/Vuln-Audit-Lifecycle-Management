package com.vulnaudit.service.notify;

/**
 * 通知通道发送器抽象
 *
 * <p>每个实现对应一种外部通道（邮件 / Webhook / 后续的短信网关）。
 * 实现必须满足：
 * <ul>
 *     <li>{@link #isEnabled()} 返回 false 时视为通道未启用，上层直接降级为仅站内，不抛异常；</li>
 *     <li>{@link #send(NotificationMessage)} 内部吞掉所有异常并转换为 {@link ChannelSendResult}，
 *         保证通知主流程（站内信落库）永远不被外部通道影响。</li>
 * </ul>
 */
public interface NotificationChannelSender {

    /**
     * 通道类型
     */
    NotificationChannel channel();

    /**
     * 通道是否已启用且配置完整
     */
    boolean isEnabled();

    /**
     * 执行一次发送（实现内部负责重试策略）
     *
     * @param message 通知报文
     * @return 发送结果，永不返回 null
     */
    ChannelSendResult send(NotificationMessage message);
}
