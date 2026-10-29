package com.vulnaudit.service.notify;

import lombok.Builder;

/**
 * 单个通道的发送结果
 *
 * @param channel  通道
 * @param success  是否成功
 * @param skipped  是否因未启用/未配置而跳过
 * @param attempts 实际投递次数
 * @param error    错误信息（成功时为 null）
 */
@Builder
public record ChannelSendResult(
        NotificationChannel channel,
        boolean success,
        boolean skipped,
        int attempts,
        String error
) {

    /**
     * 发送成功
     */
    public static ChannelSendResult success(NotificationChannel channel) {
        return ChannelSendResult.builder()
                .channel(channel)
                .success(true)
                .skipped(false)
                .attempts(1)
                .build();
    }

    /**
     * 发送成功（指定尝试次数）
     */
    public static ChannelSendResult success(NotificationChannel channel, int attempts) {
        return ChannelSendResult.builder()
                .channel(channel)
                .success(true)
                .skipped(false)
                .attempts(attempts)
                .build();
    }

    /**
     * 发送失败
     */
    public static ChannelSendResult failure(NotificationChannel channel, String error) {
        return failure(channel, 1, error);
    }

    /**
     * 发送失败（指定尝试次数）
     */
    public static ChannelSendResult failure(NotificationChannel channel, int attempts, String error) {
        return ChannelSendResult.builder()
                .channel(channel)
                .success(false)
                .skipped(false)
                .attempts(attempts)
                .error(error == null || error.isBlank() ? "发送失败" : error)
                .build();
    }

    /**
     * 通道未启用/未配置，直接跳过（降级为仅站内）
     */
    public static ChannelSendResult skipped(NotificationChannel channel, String reason) {
        return ChannelSendResult.builder()
                .channel(channel)
                .success(false)
                .skipped(true)
                .attempts(0)
                .error(reason)
                .build();
    }
}
